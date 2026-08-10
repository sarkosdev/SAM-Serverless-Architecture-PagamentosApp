package pagamentos.config;

import java.net.URI;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/**
 * 
 * S3Config Clas
 */
public final class S3Config {

    private static final String S3_ENDPOINT = "S3_ENDPOINT";
    private static final String BUCKET_NAME = "BUCKET_NAME";

    private S3Config() {
    }

    public static S3Client createClient() {
        S3ClientBuilder builder = S3Client.builder().region(Region.EU_WEST_1);

        String endpoint = System.getenv(S3_ENDPOINT);

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }

        return builder.build();
    }

    public static String bucketName() {
        return System.getenv(BUCKET_NAME);
    }
}