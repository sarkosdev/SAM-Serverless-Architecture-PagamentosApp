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

public class DeletePagamentoHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final PagamentoService service;

    public DeletePagamentoHandler() {
        DynamoDbClient client = DynamoDbConfig.createClient();
        this.service = new PagamentoService(
                new PagamentoRepository(client, DynamoDbConfig.tableName())
        );
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
        long startTime = System.currentTimeMillis();
        final String operation = "DELETE_PAGAMENTO";
        
        try {
            String method = request.getRequestContext().getHttp().getMethod();
            String path = request.getRawPath();

            if (!"DELETE".equalsIgnoreCase(method)) {
                return ApiResponse.json(405, Map.of("error", "Method not allowed"));
            }

            if (path.startsWith("/pagamentos/process/")) {
                String processNum = path.substring("/pagamentos/process/".length());
                boolean deleted = service.deletePagamentoByProcessNum(processNum);

                if (!deleted) {
                    APIGatewayV2HTTPResponse response = ApiResponse.json(404, Map.of(
                            "error", "Pagamento not found for processNum",
                            "processNum", processNum
                    ));

                    // CloudWatch Logs
                    DataLogger.info(
                        context,
                        request,
                        operation,
                        "Pagamento not found for processNum",
                        Map.of(
                                "statusCode", response.getStatusCode(),
                                "durationMs", System.currentTimeMillis() - startTime
                        )
                    );

                    return response;
                }

                APIGatewayV2HTTPResponse response =  ApiResponse.json(200, Map.of(
                        "message", "Pagamento deleted successfully",
                        "processNum", processNum
                ));

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
            }

            if (path.startsWith("/pagamentos/")) {
                String id = path.substring("/pagamentos/".length());
                boolean deleted = service.deletePagamentoById(id);

                if (!deleted) {
                    APIGatewayV2HTTPResponse response = ApiResponse.json(404, Map.of(
                            "error", "Pagamento not found",
                            "id", id
                    ));

                    // CloudWatch Logs
                    DataLogger.info(
                        context,
                        request,
                        operation,
                        "Pagamento not found",
                        Map.of(
                                "statusCode", response.getStatusCode(),
                                "durationMs", System.currentTimeMillis() - startTime
                        )
                    );

                    return response;
                }

                APIGatewayV2HTTPResponse response = ApiResponse.json(200, Map.of(
                        "message", "Pagamento deleted successfully",
                        "id", id
                ));

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
                "Internal server error",
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