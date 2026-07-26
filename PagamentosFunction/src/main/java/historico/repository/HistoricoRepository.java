package historico.repository;

import java.util.List;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

/**
 * 
 * HistoricoRepository Class - Data Layer
 */
public class HistoricoRepository {

    final String TYPE = "PAGAMENTO";

    private final DynamoDbClient dynamoDb;
    private final String tableName;

    public HistoricoRepository(DynamoDbClient dynamoDb, String tableName) {
        this.dynamoDb = dynamoDb;
        this.tableName = tableName;
    }

    // Find all 'Historico' from Table according to userName
    public List<Map<String, AttributeValue>> findAllHistoricoByUser(String userName) {

        ScanResponse response = dynamoDb.scan(ScanRequest.builder()
                .tableName(tableName)
                .filterExpression("#userName = :userName AND #type = :type")
                .expressionAttributeNames(Map.of(
                    "#userName", "userName",
                    "#type", "type"
                ))
                .expressionAttributeValues(Map.of(
                    ":userName", AttributeValue.builder().s(userName).build(),
                    ":type", AttributeValue.builder().s(TYPE).build()
                ))
                .build());

        return response.items();
    }

    
}
