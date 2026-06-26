package pagamentos.handler;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import pagamentos.config.DynamoDbConfig;
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
        try {
            PagarPagamentosRequest body = objectMapper.readValue(request.getBody(), PagarPagamentosRequest.class);
            return ApiResponse.json(200, service.pagarProcessos(body.listaProcess()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.json(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.json(500, Map.of("error", "Internal server error"));
        }
    }
}