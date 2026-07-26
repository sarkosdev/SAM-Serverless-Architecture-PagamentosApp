package pagamentos.handler;

import java.util.Base64;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;

import pagamentos.auth.AuthenticatedUserResolver;
import pagamentos.config.S3Config;
import pagamentos.logging.DataLogger;
import pagamentos.response.ApiResponse;
import pagamentos.service.S3Service;

/**
 * DownloadPdfHandler Lambda Function - Controller Layer
 */
public class DownloadPdfHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private final S3Service s3Service;

    public DownloadPdfHandler() {
        this.s3Service = new S3Service(S3Config.createClient(), S3Config.bucketName());
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent request, Context context) {
        long startTime = System.currentTimeMillis();
        final String operation = "DOWNLOAD_PDF";

        try {
            String userName = AuthenticatedUserResolver.resolve(request);

            String key = request.getQueryStringParameters() != null
                    ? request.getQueryStringParameters().get("key")
                    : null;

            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key is required");
            }

            String expectedPrefix = "pagamentos/" + userName + "/";

            if (!key.startsWith(expectedPrefix)) {
                throw new IllegalArgumentException("Não tens permissão para aceder a este ficheiro.");
            }

            byte[] pdfBytes = s3Service.downloadPdf(key);
            String fileName = key.substring(key.lastIndexOf('/') + 1);

            APIGatewayV2HTTPResponse response = APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(200)
                    .withHeaders(Map.of(
                            "Content-Type", "application/pdf",
                            "Content-Disposition", "attachment; filename=\"" + fileName + "\""
                    ))
                    .withBody(Base64.getEncoder().encodeToString(pdfBytes))
                    .withIsBase64Encoded(true)
                    .build();

            DataLogger.info(
                    context, request, operation, "Request completed successfully",
                    Map.of("statusCode", 200, "durationMs", System.currentTimeMillis() - startTime)
            );

            return response;

        } catch (IllegalArgumentException e) {
            DataLogger.error(
                    context, request, operation, "Request failed", e,
                    Map.of("statusCode", 400, "durationMs", System.currentTimeMillis() - startTime)
            );
            return ApiResponse.json(400, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            DataLogger.error(
                    context, request, operation, "Internal server error", e,
                    Map.of("statusCode", 500, "durationMs", System.currentTimeMillis() - startTime)
            );
            return ApiResponse.json(500, Map.of("error", "Internal server error"));
        }
    }
}