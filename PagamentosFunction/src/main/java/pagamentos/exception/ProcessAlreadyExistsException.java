package pagamentos.exception;

public class ProcessAlreadyExistsException extends RuntimeException{
    
    public ProcessAlreadyExistsException() {
        super("Já existe um processo com esse número.");
    }
}
