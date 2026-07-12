package pagamentos.handler;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import pagamentos.config.DynamoDbConfig;
import pagamentos.logging.DataLogger;
import pagamentos.model.PagarPagamentosRequest;
import pagamentos.repository.PagamentoRepository;
import pagamentos.response.ApiResponse;
import pagamentos.service.PagamentoService;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public class PagarPagamentoHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final PagamentoService service;

    public PagarPagamentoHandler() {
        DynamoDbClient client = DynamoDbConfig.createClient();
        this.service = new PagamentoService(
                new PagamentoRepository(client, DynamoDbConfig.tableName())
        );
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
        long startTime = System.currentTimeMillis();
        final String operation = "PAGAR_PAGAMENTO";
        
        try {

            

            PagarPagamentosRequest body = objectMapper.readValue(request.getBody(), PagarPagamentosRequest.class);
            
            //testing
            String userName = "";
            var authorizer = request.getRequestContext().getAuthorizer();
            if(authorizer != null && authorizer.getJwt() != null) {
                Map<String, String> claims = authorizer.getJwt().getClaims();
                userName = claims.get("username");
            }
            //testing


            APIGatewayV2HTTPResponse response = ApiResponse.json(200, service.pagarProcessos(body.listaProcess(), userName));
            
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