package pagamentos.handler;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import pagamentos.config.DynamoDbConfig;
import pagamentos.logging.DataLogger;
import pagamentos.repository.PagamentoRepository;
import pagamentos.response.ApiResponse;
import pagamentos.service.PagamentoService;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class ReadProcessoHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final PagamentoService service;

    public ReadProcessoHandler() {
        DynamoDbClient client = DynamoDbConfig.createClient();
        this.service = new PagamentoService(
                new PagamentoRepository(client, DynamoDbConfig.tableName())
        );
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {

        long startTime = System.currentTimeMillis();
        String operation = "READ_PAGAMENTO";

        try {
            String method = request.getRequestContext().getHttp().getMethod();
            String path = request.getRawPath();

            //testing
            String userName = "";
            var authorizer = request.getRequestContext().getAuthorizer();
            if(authorizer != null && authorizer.getJwt() != null) {
                Map<String, String> claims = authorizer.getJwt().getClaims();
                userName = claims.get("username");
            }
            //testing

            if (!"GET".equalsIgnoreCase(method)) {
                return ApiResponse.json(405, Map.of("error", "Method not allowed"));
            }

            if ("/pagamentos".equals(path)) {

                APIGatewayV2HTTPResponse response = ApiResponse.json(200, service.listProcessos(userName));

                // CloudWatch Logs
                DataLogger.info(
                    context,
                    request,
                    operation,
                    "Request completed successfully",
                    Map.of(
                            "statusCode", response.getStatusCode(),
                            "durationMs", System.currentTimeMillis() - startTime
                    )
                );

                return response;
            }

            if (path.startsWith("/pagamentos/status/")) {
                String status = path.substring("/pagamentos/status/".length());
                
                APIGatewayV2HTTPResponse response = ApiResponse.json(200, service.listProcessosByStatus(status, userName));
                
                // CloudWatch Logs
                DataLogger.info(
                    context,
                    request,
                    operation,
                    "Request completed successfully",
                    Map.of(
                            "statusCode", response.getStatusCode(),
                            "durationMs", System.currentTimeMillis() - startTime
                    )
                );

                return response;                
            }


            if (path.startsWith("/pagamentos/")) {
                String id = path.substring("/pagamentos/".length());
                Map<String, Object> pagamento = service.getProcesso(id);

                if (pagamento == null) {
                    return ApiResponse.json(404, Map.of("error", "Pagamento not found", "id", id));
                }

                return ApiResponse.json(200, pagamento);
            }
            

            return ApiResponse.json(404, Map.of("error", "Route not found"));
        } catch (IllegalArgumentException e) {
            // CloudWatch Logs
            DataLogger.error(
                context,
                request,
                operation,
                "Request failed",
                e,
                Map.of(
                        "statusCode", 400,
                        "durationMs", System.currentTimeMillis() - startTime
                )
            );

            return ApiResponse.json(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            // CloudWatch Logs
            DataLogger.error(
                context,
                request,
                operation,
                "Request failed",
                e,
                Map.of(
                        "statusCode", 500,
                        "durationMs", System.currentTimeMillis() - startTime
                )
            );
            
            return ApiResponse.json(500, Map.of("error", "Internal server error"));
        }
    }
}