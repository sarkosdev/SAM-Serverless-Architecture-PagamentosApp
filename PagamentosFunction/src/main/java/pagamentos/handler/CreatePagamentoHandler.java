package pagamentos.handler;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import pagamentos.auth.AuthenticatedUserResolver;
import pagamentos.config.DynamoDbConfig;
import pagamentos.exception.ProcessAlreadyExistsException;
import pagamentos.logging.DataLogger;
import pagamentos.model.CreatePagamentoRequest;
import pagamentos.repository.PagamentoRepository;
import pagamentos.response.ApiResponse;
import pagamentos.service.PagamentoService;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * 
 * CreatePagamentoHandler - Controller Layer
 */
public class CreatePagamentoHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final PagamentoService service;
    final String operation = "CREATE_PAGAMENTO";

    public CreatePagamentoHandler() {
        DynamoDbClient client = DynamoDbConfig.createClient();
        this.service = new PagamentoService(
                new PagamentoRepository(client, DynamoDbConfig.tableName())
        );
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {

        long startTime = System.currentTimeMillis();
        

        // CloudWatch Logs
        DataLogger.info(context, request, operation, "Request received", Map.of());

       


        try {
            CreatePagamentoRequest body = objectMapper.readValue(request.getBody(), CreatePagamentoRequest.class);
            
            String userName = AuthenticatedUserResolver.resolve(request);

            APIGatewayV2HTTPResponse response = ApiResponse.json(201, service.createProcess(body, userName));

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

        } catch (ProcessAlreadyExistsException e) {
            DataLogger.warn(
                context,
                request,
                operation,
                "Process creation conflict",
                Map.of(
                    "statusCode", 409,
                    "reasonCode", "DUPLICATE_PROCESS",
                    "durationMs", System.currentTimeMillis() - startTime)
            );

            return ApiResponse.json(409, Map.of("error", e.getMessage()));
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