package pagamentos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import pagamentos.model.CreatePagamentoRequest;
import pagamentos.repository.PagamentoRepository;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class PagamentoService {

    private final PagamentoRepository repository;

    public PagamentoService(PagamentoRepository repository) {
        this.repository = repository;
    }

    // Create 'Pagamento' Entry logic method
    public Map<String, Object> createProcess(CreatePagamentoRequest request, String userName) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        if (request.processNum() == null || request.processNum().isBlank()) throw new IllegalArgumentException("processNum is required");
        if (request.processValue() == null || request.processValue().isBlank()) throw new IllegalArgumentException("processValue is required");
        if(userName.isEmpty()) throw new IllegalArgumentException("userName is required");

        String id = UUID.randomUUID().toString();
        String createdAt = Instant.now().toString();

        Map<String, AttributeValue> item = Map.of(
                "id", AttributeValue.fromS(id),
                "userName", AttributeValue.fromS(userName),
                "processNum", AttributeValue.fromS(request.processNum()),
                "processValue", AttributeValue.fromS(request.processValue()),
                "status", AttributeValue.fromS("PENDING"),
                "createdAt", AttributeValue.fromS(createdAt)
        );

        repository.save(item);

        return Map.of(
                "id", id,
                "userName", userName,
                "processNum", request.processNum(),
                "processValue", request.processValue(),
                "status", "PENDING",
                "createdAt", createdAt
        );
    }

    // List 'Pagamento' Entries from DynamoDB Table logic method
    public List<Map<String, Object>> listProcessos(String userName) {
        if(userName.isEmpty()) throw new IllegalArgumentException("userName is required");

        return repository.findAll(userName)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Averiguar se pode ser eliminado
    // Get 'Pagamento' Entry from DynamoDB Table logic method
    public Map<String, Object> getProcesso(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");

        Map<String, AttributeValue> item = repository.findById(id);

        if (item == null) return null;

        return toResponse(item);
    }


    // List 'Pagamento' entries from 'Pagamentos' Table logic method with status PENDING or PAGO logic method
    public List<Map<String, Object>> listProcessosByStatus(String status, String userName) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
        if (userName == null || userName.isEmpty()) throw new IllegalArgumentException("userName is required");
        
            String normalizedStatus = status.toUpperCase();

        if (!normalizedStatus.equals("PENDING") && !normalizedStatus.equals("PAGO")) {
            throw new IllegalArgumentException("Invalid status. Allowed values: 'PENDING' or 'PAGO'");
        }

        return repository.findByStatus(normalizedStatus, userName)
                .stream()
                .map(this::toResponse)
                .toList();
    }



    // Pagar Processos functionality logic method
    public Map<String, Object> pagarProcessos(List<String> listaProcess, String userName) {
        if (listaProcess == null || listaProcess.isEmpty()) {
            throw new IllegalArgumentException("listaProcess is required");
        }

        if(userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("userName is required");
        }

        BigDecimal total = BigDecimal.ZERO;

        List<Map<String, AttributeValue>> processosPagos = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<String> alreadyPaid = new ArrayList<>();

        for (String processNum : listaProcess) {
            if (processNum == null || processNum.isBlank()) {
                continue;
            }

            Map<String, AttributeValue> item = repository.findFirstByProcessNum(processNum, userName);

            if (item == null) {
                notFound.add(processNum);
                continue;
            }

            String id = item.get("id").s();
            String currentStatus = item.get("status").s();

            if ("PAGO".equalsIgnoreCase(currentStatus)) {
                alreadyPaid.add(processNum);
                continue;
            }

            if (!"PENDING".equalsIgnoreCase(currentStatus)) {
                continue;
            }

            String processValue = item.get("processValue").s();

            total = total.add(new BigDecimal(processValue));

            repository.updateStatus(id, "PAGO");

            
            processosPagos.add(Map.of(
                    "processNum", AttributeValue.builder().s(item.get("processNum").s()).build(),
                    "processValue", AttributeValue.builder().n(processValue).build()
            ));
        }

        
        // Now we save the 'Pagamentos Pagos' inside table to use later for 'Historico Pagamentos'
        buildPagamentoAndSaveIt(processosPagos, total, userName);


        return Map.of(
                "total", total.toString(),
                "pagamentosPagos", processosPagos,
                "alreadyPaid", alreadyPaid,
                "notFound", notFound
        );
    }

    
    // Delete 'Pagamento' by Id
    public boolean deleteProcessoById(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");

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


    private Map<String, Object> toResponse(Map<String, AttributeValue> item) {
        return Map.of(
                "id", item.get("id").s(),
                "processNum", item.get("processNum").s(),
                "processValue", item.get("processValue").s(),
                "status", item.get("status").s(),
                "createdAt", item.get("createdAt").s()
        );
    }


    // Build 'Pagamento' that will be saved in our DynamoDB Table
    // Call repository with save method to perform this operation
    private void buildPagamentoAndSaveIt(List<Map<String, AttributeValue>> processosPagos, BigDecimal valorTotal, String userName){
        final String pagamentoId = UUID.randomUUID().toString();
        final String createdAt = Instant.now().toString();
        
        // Complete Map that will be saved in our Table
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(pagamentoId).build());
        item.put("PK", AttributeValue.builder().s("USER#" + userName).build());
        item.put("SK", AttributeValue.builder().s("PAGAMENTO#" + createdAt + "#" + pagamentoId).build());
        item.put("type", AttributeValue.builder().s("PAGAMENTO").build());
        item.put("userName", AttributeValue.builder().s(userName).build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());
        item.put("valorTotal", AttributeValue.builder().n(valorTotal.toString()).build());
        item.put("processosPagos", AttributeValue.builder()
                .l(processosPagos.stream()
                        .map(processo -> AttributeValue.builder().m(processo).build())
                        .toList())
                .build());

        this.repository.save(item);
                
    }




}