package historico.handler;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import historico.repository.HistoricoRepository;
import historico.service.HistoricoService;
import pagamentos.auth.AuthenticatedUserResolver;
import pagamentos.config.DynamoDbConfig;
import pagamentos.logging.DataLogger;
import pagamentos.response.ApiResponse;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * 
 * HistoricoHandler Lambda Function - Controller Layer
 */
public class HistoricoHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse>{
    
    private final HistoricoService service;

    public HistoricoHandler() {
        DynamoDbClient client = DynamoDbConfig.createClient();
        this.service = new HistoricoService(
                new HistoricoRepository(client, DynamoDbConfig.tableName())
            );
    }


    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
        
        long startTime = System.currentTimeMillis();
        String operationRead = "READ_HISTORICO";
        

        try {
            String method = request.getRequestContext().getHttp().getMethod();
            String path = request.getRawPath();

            String userName = AuthenticatedUserResolver.resolve(request);

            // GET 'Historico' list
            if("/historico".equals(path) && "GET".equals(method)) {
                APIGatewayV2HTTPResponse response = ApiResponse.json(200, service.listHistoricoByUser(userName));

                // CloudWatch Logs
                DataLogger.info(
                    context,
                    request,
                    operationRead,
                    "Request completed successfully",
                    Map.of(
                            "statusCode", response.getStatusCode(),
                            "durationMs", System.currentTimeMillis() - startTime
                    )
                );

                return response;
            }

            // In case it dosent find the endpoint
            return ApiResponse.json(404, Map.of("error", "Route not found"));
        
        } catch (IllegalArgumentException e) {
            // CloudWatch Logs
            DataLogger.error(
                context,
                request,
                operationRead,
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
                operationRead,
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
