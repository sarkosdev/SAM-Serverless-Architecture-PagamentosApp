package pagamentos.logging;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

public class DataLogger {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void info(
            Context context,
            APIGatewayV2HTTPEvent request,
            String operation,
            String message,
            Map<String, Object> extraFields
    ) {
        log("INFO", context, request, operation, message, extraFields);
    }

    public static void error(
            Context context,
            APIGatewayV2HTTPEvent request,
            String operation,
            String message,
            Exception exception,
            Map<String, Object> extraFields
    ) {
        Map<String, Object> fields = new HashMap<>();

        if (extraFields != null) {
            fields.putAll(extraFields);
        }

        if (exception != null) {
            fields.put("exceptionType", exception.getClass().getSimpleName());
            fields.put("exceptionMessage", exception.getMessage());
        }

        log("ERROR", context, request, operation, message, fields);
    }

    private static void log(
            String level,
            Context context,
            APIGatewayV2HTTPEvent request,
            String operation,
            String message,
            Map<String, Object> extraFields
    ) {
        try {
            Map<String, Object> log = new HashMap<>();

            log.put("timestamp", Instant.now().toString());
            log.put("level", level);
            log.put("operation", operation);
            log.put("message", message);

            if (context != null) {
                log.put("requestId", context.getAwsRequestId());
                log.put("functionName", context.getFunctionName());
            }

            if (request != null && request.getRequestContext() != null) {
                log.put("routeKey", request.getRouteKey());
                log.put("rawPath", request.getRawPath());

                if (request.getRequestContext().getHttp() != null) {
                    log.put("method", request.getRequestContext().getHttp().getMethod());
                    log.put("sourceIp", request.getRequestContext().getHttp().getSourceIp());
                    log.put("userAgent", request.getRequestContext().getHttp().getUserAgent());
                }

                String username = extractUsername(request);
                if (username != null) {
                    log.put("username", username);
                }
            }

            if (extraFields != null) {
                log.putAll(extraFields);
            }

            System.out.println(objectMapper.writeValueAsString(log));
        } catch (Exception e) {
            System.out.println("{\"level\":\"ERROR\",\"message\":\"Failed to serialize structured log\"}");
        }
    }

    private static String extractUsername(APIGatewayV2HTTPEvent request) {
        try {
            
            if (request == null ||
            request.getRequestContext() == null ||
            request.getRequestContext().getAuthorizer() == null ||
            request.getRequestContext().getAuthorizer().getJwt() == null ||
            request.getRequestContext().getAuthorizer().getJwt().getClaims() == null) {
            return null;
        }

        Map<String, String> claims =
                request.getRequestContext()
                        .getAuthorizer()
                        .getJwt()
                        .getClaims();

        String username = claims.get("username");

        if (username != null && !username.isBlank()) {
            return username;
        }

        return claims.get("cognito:username");
        } catch (Exception e) {
            return null;
        }
    }
}
