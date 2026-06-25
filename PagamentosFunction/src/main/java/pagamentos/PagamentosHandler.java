package pagamentos;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;


public class PagamentosHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String tableName = System.getenv("TABLE_NAME");
    private final DynamoDbClient dynamoDb = createDynamoDbClient();


    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {

        context.getLogger().log("TABLE_NAME=" + System.getenv("TABLE_NAME"));
        context.getLogger().log("DYNAMODB_ENDPOINT=" + System.getenv("DYNAMODB_ENDPOINT"));
        context.getLogger().log("AWS_REGION=" + System.getenv("AWS_REGION"));

        try {
            String method = request.getRequestContext().getHttp().getMethod();
            String path = request.getRawPath();

            if ("OPTIONS".equalsIgnoreCase(method)) {
                return response(200, Map.of("message", "OK"));
            }

            // Add one row in to our 'Pagamentos' table in our DynamoDB table
            if ("POST".equalsIgnoreCase(method) && "/pagamentos".equals(path)) {
                return createPagamento(request);
            }

            // Get list of all 'Pagamentos' in our DynamoDB table
            if ("GET".equalsIgnoreCase(method) && "/pagamentos".equals(path)) {
                return listPagamentos();
            }

            // Get list of 'Pagamentos' with status = PENDING or with status = PAGO
            if("GET".equalsIgnoreCase(method) && path.startsWith("/pagamentos/status/")) {
                String status = extractStatus(path);
                return listPagamentosByStatus(status);
            }

            // Post of 'Pagamentos' in order to be payed 
            if ("POST".equalsIgnoreCase(method) && "/pagamentos/pagar".equals(path)) {
                return valorSerPago(request);
            }

            if ("GET".equalsIgnoreCase(method) && path.startsWith("/pagamentos/")) {
                String id = extractId(path);
                return getPagamento(id);
            }


            // Delete an entry from our table 'Pagamentos' according to a processNum
            if ("DELETE".equalsIgnoreCase(method) && path.startsWith("/pagamentos/process/")) {
                String processNum = extractProcessNum(path);
                return deletePagamentoByProcessNum(processNum);
            }

            return response(404, Map.of("error", "Route not found"));

        } catch (Exception e) {
            context.getLogger().log("ERROR: " + e.getMessage());
            return response(500, Map.of("error", "Internal server error"));
        }
    }

    private APIGatewayV2HTTPResponse createPagamento(APIGatewayV2HTTPEvent request) throws Exception {
        CreatePagamentoRequest body = mapper.readValue(request.getBody(), CreatePagamentoRequest.class);

        // Validate that 'processNum' field is fullfield
        if (body.processNum() == null || body.processNum().isBlank()) {
            return response(400, Map.of("error", "process number is required"));
        }

        // Validate that 'processValue' field is fullfield
        if (body.processValue() == null || body.processValue().isBlank()) {
            return response(400, Map.of("error", "process value is required"));
        }

        // Query dynamoDB table - SELECT
        ScanResponse result = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#processNum = :processNum AND #status = :pending")
                .expressionAttributeNames(Map.of(
                        "#processNum", "processNum",
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":processNum", AttributeValue.fromS(body.processNum),
                        ":pending", AttributeValue.fromS("PENDING")
                ))
        .build());


        if(result != null) {
            return response(400, Map.of("error", "process number already exist in DB"));
        }


        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.fromS(id));
        item.put("processNum", AttributeValue.fromS(body.processNum()));
        item.put("processValue", AttributeValue.fromS(body.processValue()));
        item.put("status", AttributeValue.fromS("PENDING"));
        item.put("createdAt", AttributeValue.fromS(createdAt));

        // Add the new process to our DynamoDB table
        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        // Endpoint response when called (when creating process we return the process)
        return response(201, Map.of(
                "id", id,
                "processNum", body.processNum(),
                "processValue", body.processValue(),
                "status", "PENDING",
                "createdAt", createdAt
        ));
    }

    // Calculate the total value according to the list of process numbers provided
    private APIGatewayV2HTTPResponse valorSerPago(APIGatewayV2HTTPEvent request) throws Exception{

        PagarPagamentosRequest body = mapper.readValue(
                request.getBody(),
                PagarPagamentosRequest.class
        );

        List<String> listProcessNumb = body.listaProcess();
        
        if (listProcessNumb == null || listProcessNumb.isEmpty()) {
            return response(400, Map.of("error", "processNumbers list is required"));
        }

        List<Map<String, String>> pagamentosEncontrados = new ArrayList<>();
        List<String> processosNaoEncontrados = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (String processNum : listProcessNumb) {
            if (processNum == null || processNum.isBlank()) {
                continue;
            }

            System.out.println("valor do processNum :: " + processNum);

            // Query dynamoDB table - SELECT
            ScanResponse result = dynamoDb.scan(ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("#processNum = :processNum AND #status = :pending")
                    .expressionAttributeNames(Map.of(
                            "#processNum", "processNum",
                            "#status", "status"
                    ))
                    .expressionAttributeValues(Map.of(
                            ":processNum", AttributeValue.fromS(processNum),
                            ":pending", AttributeValue.fromS("PENDING")
                    ))
                    .build());

            System.out.println("valor do result :: " + result);

            if (result.items().isEmpty()) {
                processosNaoEncontrados.add(processNum);
                continue;
            }

            Map<String, AttributeValue> item = result.items().getFirst();

            String id = item.get("id").s();
            String processValue = item.get("processValue").s();

            total = total.add(new BigDecimal(processValue));

            // Update DynamoDB entrys
            dynamoDb.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("id", AttributeValue.fromS(id)))
                    .updateExpression("SET #status = :paid")
                    .expressionAttributeNames(Map.of(
                            "#status", "status"
                    ))
                    .expressionAttributeValues(Map.of(
                            ":paid", AttributeValue.fromS("PAGO")
                    ))
                    .build());

            Map<String, String> pagamento =  new HashMap<>(itemToMap(item));
            pagamento.put("status", "PAGO");
            pagamentosEncontrados.add(pagamento);
        }

        return response(200, Map.of(
                "message", "Payment operation processed",
                "total", total.toString(),
                "paidCount", pagamentosEncontrados.size(),
                "notFoundOrNotPending", processosNaoEncontrados,
                "pagamentos", pagamentosEncontrados
        ));

    }



    private APIGatewayV2HTTPResponse getPagamento(String id) throws Exception {
        GetItemResponse result = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .build());

        if (!result.hasItem()) {
            return response(404, Map.of("error", "Pagamento not found"));
        }

        return response(200, itemToMap(result.item()));
    }

    private APIGatewayV2HTTPResponse listPagamentos() throws Exception {
        ScanResponse result = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .limit(50)
                .build());

        List<Map<String, String>> pagamentos = result.items()
                .stream()
                .map(this::itemToMap)
                .toList();

        return response(200, pagamentos);
    }


    private APIGatewayV2HTTPResponse listPagamentosByStatus(String status) throws Exception {
        String normalizedStatus = status.toUpperCase();

        if (!normalizedStatus.equals("PENDING") && !normalizedStatus.equals("PAGO")) {
            return response(400, Map.of(
                    "error", "Invalid status. Allowed values: PENDING, PAGO"
            ));
        }

        ScanResponse result = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#status = :statusValue")
                .expressionAttributeNames(Map.of(
                        "#status", "status"
                ))
                .expressionAttributeValues(Map.of(
                        ":statusValue", AttributeValue.fromS(normalizedStatus)
                ))
                .limit(50)
                .build());

        List<Map<String, String>> pagamentos = result.items()
                .stream()
                .map(this::itemToMap)
                .toList();

        return response(200, pagamentos);
    }



    private APIGatewayV2HTTPResponse deletePagamentoByProcessNum(String processNum) throws Exception {
        if (processNum == null || processNum.isBlank()) {
            return response(400, Map.of("error", "processNum is required"));
        }

        // Querys for process in 'Pagamentos' DynamoDB table by processNum
        ScanResponse result = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#processNum = :processNum")
                .expressionAttributeNames(Map.of(
                        "#processNum", "processNum"
                ))
                .expressionAttributeValues(Map.of(
                        ":processNum", AttributeValue.fromS(processNum)
                ))
                .limit(1)
                .build());

        if (result.items().isEmpty()) {
            return response(404, Map.of(
                    "error", "Pagamento not found for processNum",
                    "processNum", processNum
            ));
        }

        Map<String, AttributeValue> item = result.items().getFirst();
        String id = item.get("id").s();

        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .build());

        return response(200, Map.of(
                "message", "Pagamento deleted succesfully",
                "processNum", processNum
        ));
    }

    private String extractId(String path) {
        return path.substring("/pagamentos/".length());
    }

    private String extractStatus(String path) {
        return path.substring("/pagamentos/status/".length());
    }

    private String extractProcessNum(String path) {
        return path.substring("/pagamentos/process/".length());
    }


    private Map<String, String> itemToMap(Map<String, AttributeValue> item) {
        return Map.of(
                "id", item.get("id").s(),
                "processNum", item.get("processNum").s(),
                "processValue", item.get("processValue").s(),
                "status", item.get("status").s(),
                "createdAt", item.get("createdAt").s()
        );
    }

    private APIGatewayV2HTTPResponse response(int statusCode, Object body) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS",
                            "Access-Control-Allow-Headers", "Content-Type,Authorization"
                    ))
                    .withBody(mapper.writeValueAsString(body))
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Failed to serialize response\"}")
                    .build();
        }
    }


    // DynamoDBClient connector 
    private DynamoDbClient createDynamoDbClient() {
        String endpoint = System.getenv("DYNAMODB_ENDPOINT");
        String region = Optional.ofNullable(System.getenv("AWS_REGION")).orElse("eu-west-1");

        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("dummy", "dummy")
                    )
            );
        }

        return builder.build();
    }

    public record CreatePagamentoRequest(String processNum, String processValue) {}

    public record PagarPagamentosRequest(List<String> listaProcess) {}

}
