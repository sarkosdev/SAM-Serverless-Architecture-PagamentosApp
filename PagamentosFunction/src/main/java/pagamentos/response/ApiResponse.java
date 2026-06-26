package pagamentos.response;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * API Response Class returned on a request
 */
public class ApiResponse {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static APIGatewayV2HTTPResponse json(int statusCode, Object body) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of(
                            "Content-Type", "application/json",
                            "Access-Control-Allow-Origin", "*",
                            "Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS",
                            "Access-Control-Allow-Headers", "Content-Type,Authorization"
                    ))
                    .withBody(objectMapper.writeValueAsString(body))
                    .build();
        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody("{\"error\":\"Failed to serialize response\"}")
                    .build();
        }
    }
}