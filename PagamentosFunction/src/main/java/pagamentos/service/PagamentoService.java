package pagamentos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    public Map<String, Object> createPagamento(CreatePagamentoRequest request, String userName) {
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
    public List<Map<String, Object>> listPagamentos(String userName) {
        if(userName.isEmpty()) throw new IllegalArgumentException("userName is required");

        return repository.findAll()
                .stream()
                .filter(pagamento -> pagamento.get("userName").equals(userName))
                .map(this::toResponse)
                .toList();
    }

    // Averiguar se pode ser eliminado
    // Get 'Pagamento' Entry from DynamoDB Table logic method
    public Map<String, Object> getPagamento(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");

        Map<String, AttributeValue> item = repository.findById(id);

        if (item == null) return null;

        return toResponse(item);
    }


    // List 'Pagamento' entries from 'Pagamentos' Table logic method with status PENDING or PAGO logic method
    public List<Map<String, Object>> listPagamentosByStatus(String status) {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");

        String normalizedStatus = status.toUpperCase();

        if (!normalizedStatus.equals("PENDING") && !normalizedStatus.equals("PAGO")) {
            throw new IllegalArgumentException("Invalid status. Allowed values: 'PENDING' or 'PAGO'");
        }

        return repository.findByStatus(normalizedStatus)
                .stream()
                .map(this::toResponse)
                .toList();
    }



    // Pagar Processos functionality logic method
    public Map<String, Object> pagarProcessos(List<String> listaProcess) {
        if (listaProcess == null || listaProcess.isEmpty()) {
            throw new IllegalArgumentException("listaProcess is required");
        }

        BigDecimal total = BigDecimal.ZERO;

        List<Map<String, Object>> pagamentosPagos = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        List<String> alreadyPaid = new ArrayList<>();

        for (String processNum : listaProcess) {
            if (processNum == null || processNum.isBlank()) {
                continue;
            }

            Map<String, AttributeValue> item = repository.findFirstByProcessNum(processNum);

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

            pagamentosPagos.add(Map.of(
                    "processNum", item.get("processNum").s(),
                    "processValue", processValue
            ));
        }

        return Map.of(
                "total", total.toString(),
                "pagamentosPagos", pagamentosPagos,
                "alreadyPaid", alreadyPaid,
                "notFound", notFound
        );
    }

    
    // Delete 'Pagamento' by Id
    public boolean deletePagamentoById(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");

        Map<String, AttributeValue> item = repository.findById(id);

        if (item == null) return false;

        repository.deleteById(id);
        return true;
    }


    // Delete 'Pagamento' from 'Pagamento' Table in DynamoDB by 'processNum'
    public boolean deletePagamentoByProcessNum(String processNum) {
        if (processNum == null || processNum.isBlank()) {
            throw new IllegalArgumentException("processNum is required");
        }

        Map<String, AttributeValue> item = repository.findFirstByProcessNum(processNum);

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
}