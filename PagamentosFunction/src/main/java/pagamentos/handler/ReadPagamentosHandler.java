package pagamentos.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import pagamentos.config.DynamoDbConfig;
import pagamentos.repository.PagamentoRepository;
import pagamentos.response.ApiResponse;
import pagamentos.service.PagamentoService;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Map;

public class ReadPagamentosHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final PagamentoService service;

    public ReadPagamentosHandler() {
        DynamoDbClient client = DynamoDbConfig.createClient();
        this.service = new PagamentoService(
                new PagamentoRepository(client, DynamoDbConfig.tableName())
        );
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
        try {
            String method = request.getRequestContext().getHttp().getMethod();
            String path = request.getRawPath();

            if (!"GET".equalsIgnoreCase(method)) {
                return ApiResponse.json(405, Map.of("error", "Method not allowed"));
            }

            if ("/pagamentos".equals(path)) {
                return ApiResponse.json(200, service.listPagamentos());
            }

            if (path.startsWith("/pagamentos/status/")) {
                String status = path.substring("/pagamentos/status/".length());
                return ApiResponse.json(200, service.listPagamentosByStatus(status));
            }

            if (path.startsWith("/pagamentos/")) {
                String id = path.substring("/pagamentos/".length());
                Map<String, Object> pagamento = service.getPagamento(id);

                if (pagamento == null) {
                    return ApiResponse.json(404, Map.of("error", "Pagamento not found", "id", id));
                }

                return ApiResponse.json(200, pagamento);
            }

            return ApiResponse.json(404, Map.of("error", "Route not found"));
        } catch (IllegalArgumentException e) {
            return ApiResponse.json(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.json(500, Map.of("error", "Internal server error"));
        }
    }
}