package pagamentos.service;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import pagamentos.exception.ProcessAlreadyExistsException;
import pagamentos.model.CreatePagamentoRequest;
import pagamentos.repository.PagamentoRepository;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * 
 * PagamentoService Class - Service Layer
 */
public class PagamentoService {

    // Eligible date formater
    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));

    private static final String TYPE_PROCESSO = "PROCESSO";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PAGO = "PAGO";

    private final PagamentoRepository repository;

    public PagamentoService(PagamentoRepository repository) {
        this.repository = repository;
    }

    // Create 'Pagamento' Entry logic method
    public Map<String, Object> createProcess(CreatePagamentoRequest request, String userName) {
        /** 
        if (request == null) throw new IllegalArgumentException("Request body is required");
        if (request.processNum() == null || request.processNum().isBlank()) throw new IllegalArgumentException("processNum is required");
        if (request.processValue() == null || request.processValue().isBlank()) throw new IllegalArgumentException("processValue is required");
        if (userName == null || userName.isBlank()) throw new IllegalArgumentException("userName is required");
        */

        // Validates previously the request
        this.validateRequest(request, userName);

        String ownerId = userName.trim();
        String normalizedProcessNum =normalizeProcessNum(request.processNum());

        String id = UUID.randomUUID().toString();

        String uniqueKey = buildUniqueKey(ownerId,normalizedProcessNum);

        Map<String, AttributeValue> processoExistente = repository.findFirstByProcessNum(request.processNum(), userName);

        /* 
        if(processoExistente != null) {
            throw new IllegalArgumentException("Já existe um processo com o número " + request.processNum() + ".");
        }
        */

        

        //String id = UUID.randomUUID().toString();
        String createdAt = CREATED_AT_FORMATTER.format(Instant.now()).toString();

        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.fromS(id),
                "userName", AttributeValue.fromS(userName),
                "processNum", AttributeValue.fromS(request.processNum()),
                "processValue", AttributeValue.fromS(request.processValue()),
                "status", AttributeValue.fromS(STATUS_PENDING),
                "type", AttributeValue.fromS(TYPE_PROCESSO),
                "createdAt", AttributeValue.fromS(createdAt),
                "uniqueKey", AttributeValue.fromS(uniqueKey)
        );


        // Check if process was saved
        boolean created = repository.saveProcessIfUnique(item, uniqueKey);


        //repository.save(item);

        // It wasnt created that means theres is already that process saved for that user, and processes must be unique per user
        if (!created) {throw new ProcessAlreadyExistsException();}

        return Map.of(
                "id", id,
                "userName", userName,
                "processNum", request.processNum(),
                "processValue", request.processValue(),
                "status", STATUS_PENDING,
                "type", TYPE_PROCESSO,
                "createdAt", createdAt
        );
    }



    // List 'Pagamento' Entries from DynamoDB Table logic method
    public List<Map<String, Object>> listProcessos(String userName) {
        if(userName.isEmpty()) throw new IllegalArgumentException("userName is required");

        return repository.findAllProcesso(userName)
                .stream()
                .map(this::toResponseProcess)
                .toList();
    }

    // Averiguar se pode ser eliminado
    // Get 'Pagamento' Entry from DynamoDB Table logic method
    public Map<String, Object> getProcesso(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");

        Map<String, AttributeValue> item = repository.findById(id);

        if (item == null) return null;

        return toResponseProcess(item);
    }


    // List 'Pagamento' entries from 'Pagamentos' Table logic method with status PENDING or PAGO logic method
    public List<Map<String, Object>> listProcessosByStatus(String status, String userName) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
        if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("userName is required");
        
        String normalizedStatus = status.toUpperCase();

        if (!normalizedStatus.equals(STATUS_PENDING) && !normalizedStatus.equals(STATUS_PAGO)) {
            throw new IllegalArgumentException("Invalid status. Allowed values: 'PENDING' or 'PAGO'");
        }

        return repository.findByStatus(normalizedStatus, userName)
                .stream()
                .map(this::toResponseProcess)
                .toList();
    }

    
    // Delete 'Pagamento' by Id
    public boolean deleteProcessoById(String id, String userName) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if(userName == null || userName.isEmpty()) throw new IllegalArgumentException("userName is required"); 

        Map<String, AttributeValue> item = repository.findById(id);

        if (item == null) return false;

        repository.deleteById(id);
        return true;
    }


    // Delete 'Pagamento' from 'Pagamento' Table in DynamoDB by 'processNum'
    public boolean deletePagamentoByProcessNum(String processNum, String userName) {
        if (processNum == null || processNum.isBlank()) {
            throw new IllegalArgumentException("processNum is required");
        }

        if(userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("userName is required");
        }

        Map<String, AttributeValue> item = repository.findFirstByProcessNum(processNum, userName);

        if (item == null) return false;

        String id = item.get("id").s();
        repository.deleteById(id);
        return true;
    }


    private void validateRequest( CreatePagamentoRequest request, String userName) {

        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        if (request.processNum() == null || request.processNum().isBlank()) {
            throw new IllegalArgumentException( "processNum is required");
        }

        if (request.processValue() == null || request.processValue().isBlank()) {
            throw new IllegalArgumentException("processValue is required");
        }

        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("userName is required");
        }
    }

    // USE IT TO NORMALIZE PROCESS NUMBERS FOR UNIQUE KEY
    private String normalizeProcessNum(String processNum) {
        return processNum.trim().toUpperCase(Locale.ROOT);
    }

    // Builds unique key for our DynamoDB Table
    private String buildUniqueKey(String ownerId,String processNum) {

        try {
            String source =ownerId+ "\u001F"+ processNum;

            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));

            return "UNIQUE#PROCESSO#"+ HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate unique process key",e);
        }
    }



    private Map<String, Object> toResponseProcess(Map<String, AttributeValue> item) {
        return Map.of(
                "id", item.get("id").s(),
                "processNum", item.get("processNum").s(),
                "processValue", item.get("processValue").s(),
                "status", item.get("status").s(),
                "createdAt", item.get("createdAt").s(),
                "userName", item.get("userName").s(),
                "type", item.get("type").s()
        );
    }

}