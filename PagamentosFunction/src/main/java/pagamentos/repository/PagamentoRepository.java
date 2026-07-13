package pagamentos.repository;

import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * 
 * 'Pagamento' Table Repository Class
 * 
 */
public class PagamentoRepository {

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public PagamentoRepository(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }


    // Save 'Pagamento' in to DynamoDB 'Pagamentos' Tabel
    public void save(Map<String, AttributeValue> item) {
        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    // Find all 'Pagamento' in DynamoDB 'Pagamentos' Tabel
    public List<Map<String, AttributeValue>> findAll(String userName) {

        final String type = "PROCESSO";

        ScanResponse response = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("userName = :userName AND type = :type")
                .expressionAttributeValues(Map.of(
                        ":userName", AttributeValue.builder().s(userName).build(),
                        ":type", AttributeValue.builder().s(type).build()
                ))
                .build());

        return response.items();
    }


    // Find 'Pagamento' by ID in DynamoDB 'Pagamentos' Tabel
    public Map<String, AttributeValue> findById(String id) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return null;
        }

        return response.item();
    }


    // Find 'Pagamento' by STATUS in DynamoDB 'Pagamentos' Tabel
    public List<Map<String, AttributeValue>> findByStatus(String status, String userName) {

        final String type = "PROCESSO";

        ScanResponse response = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#status = :statusValue AND #userName = :userName AND #type = :type")
                .expressionAttributeNames(Map.of(
                        "#status", "status",
                        "#userName", "userName",
                        "#type", "type"
                ))
                .expressionAttributeValues(Map.of(
                        ":statusValue", AttributeValue.fromS(status),
                        ":userName", AttributeValue.fromS(userName),
                        ":type", AttributeValue.fromS(type)
                ))
                .build());

        return response.items();
    }


    // Find first 'Pagamento' entry in DynamoDB 'Pagamentos' Tabel
    public Map<String, AttributeValue> findFirstByProcessNum(String processNum, String userName) {

        final String type = "PROCESSO";

        ScanResponse response = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#processNum = :processNum AND #userName = :userName AND #type = :type")
                .expressionAttributeNames(Map.of(
                        "#processNum", "processNum",
                        "#userName", "userName",
                        "#type", "type"
                ))
                .expressionAttributeValues(Map.of(
                        ":processNum", AttributeValue.fromS(processNum),
                        ":userName", AttributeValue.fromS(userName),
                        ":type", AttributeValue.fromS(type)
                ))
                .build());

        if (!response.hasItems() || response.items().isEmpty()) {
            return null;
        }

        return response.items().get(0);
    }

    // Update 'Pagamento' status 
    public void updateStatus(String id, String status) {
        dynamoDb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .updateExpression("SET #status = :status")
                .expressionAttributeNames(Map.of("#status", "status"))
                .expressionAttributeValues(Map.of(":status", AttributeValue.fromS(status)))
                .build());
    }

    // Delete 'Pagamento' entry from DynamoDB Table 'Pagamentos'
    public void deleteById(String id) {
        dynamoDb.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .build());
    }
}