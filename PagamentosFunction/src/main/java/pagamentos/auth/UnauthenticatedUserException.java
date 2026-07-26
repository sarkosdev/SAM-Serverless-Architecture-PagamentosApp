package pagamentos.auth;

/**
 * 
 * UnauthenticatedUserException Class
 */
public class UnauthenticatedUserException
        extends RuntimeException {

    public UnauthenticatedUserException(String message) {
        super(message);
    }
}
