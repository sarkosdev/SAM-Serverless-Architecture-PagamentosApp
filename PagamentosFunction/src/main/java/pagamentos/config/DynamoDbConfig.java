package pagamentos.config;

import java.net.URI;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * DynamoDB Configuration Class
 */
public class DynamoDbConfig {

    public static DynamoDbClient createClient() {
        String endpoint = System.getenv("DYNAMODB_ENDPOINT");
        String region = System.getenv("AWS_REGION");

        if (region == null || region.isBlank()) {
            region = "eu-west-1";
        }

        DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("dummy", "dummy")));
        }

        return builder.build();
    }

    public static String tableName() {
        String tableName = System.getenv("TABLE_NAME");

        if (tableName == null || tableName.isBlank()) {
            throw new IllegalStateException("TABLE_NAME environment variable is required");
        }

        return tableName;
    }
}