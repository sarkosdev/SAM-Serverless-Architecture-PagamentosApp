package pagamentos.auth;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;

public final class AuthenticatedUserResolver {

    private static final String APP_ENV = "APP_ENV";
    private static final String LOCAL_ENVIRONMENT = "local";

    private static final String LOCAL_USERNAME_ENV = "LOCAL_USERNAME";
    private static final String LOCAL_USERNAME_HEADER = "x-local-username";

    private AuthenticatedUserResolver() {
    }

    public static String resolve(APIGatewayV2HTTPEvent event) {

        String cognitoUsername = getUsernameFromCognito(event);

        if (isNotBlank(cognitoUsername)) {
            return cognitoUsername;
        }

        /*
         * O fallback local só pode ser usado quando
         * APP_ENV está explicitamente configurado como "local".
         */
        if (isLocalEnvironment()) {

            String usernameFromHeader = getHeaderIgnoreCase(
                    event != null ? event.getHeaders() : null,
                    LOCAL_USERNAME_HEADER
                    );

            if (isNotBlank(usernameFromHeader)) {
                return usernameFromHeader.trim();
            }

            String usernameFromEnvironment = System.getenv(LOCAL_USERNAME_ENV);

            if (isNotBlank(usernameFromEnvironment)) {
                return usernameFromEnvironment.trim();
            }
        }

        throw new UnauthenticatedUserException(
                "Não foi possível determinar o utilizador autenticado."
        );
    }

    private static String getUsernameFromCognito(APIGatewayV2HTTPEvent event) {
        if (event == null
                || event.getRequestContext() == null
                || event.getRequestContext().getAuthorizer() == null
                || event.getRequestContext()
                        .getAuthorizer()
                        .getJwt() == null
                || event.getRequestContext()
                        .getAuthorizer()
                        .getJwt()
                        .getClaims() == null) {

            return null;
        }

        Map<String, String> claims =
                event.getRequestContext()
                        .getAuthorizer()
                        .getJwt()
                        .getClaims();

        /*
         * O access token Cognito pode fornecer "username".
         * Dependendo do token/configuração, também podes encontrar
         * "cognito:username".
         */
        String username = claims.get("username");

        if (isNotBlank(username)) {
            return username;
        }

        return claims.get("cognito:username");
    }

    private static boolean isLocalEnvironment() {
        return LOCAL_ENVIRONMENT.equalsIgnoreCase(
                System.getenv(APP_ENV)
        );
    }

    private static String getHeaderIgnoreCase(Map<String, String> headers, String headerName) {
        if (headers == null || headers.isEmpty()) {return null;}

        return headers.entrySet()
                .stream()
                .filter(entry ->
                        entry.getKey() != null
                                && entry.getKey()
                                        .equalsIgnoreCase(headerName)
                )
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}