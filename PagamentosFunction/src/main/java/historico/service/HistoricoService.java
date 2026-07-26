package historico.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import historico.repository.HistoricoRepository;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;


/**
 * 
 * HistoricoService Class - Service Layer
 */
public class HistoricoService {
    
    private final HistoricoRepository repository;

    public HistoricoService(HistoricoRepository repository){
        this.repository = repository;
    }

    // List 'Historico' by userName
    public List<Map<String, Object>> listHistoricoByUser(String userName) {
        if(userName.isEmpty() || userName == null) throw new IllegalArgumentException("userName is required");
    
        return repository.findAllHistoricoByUser(userName)
                .stream()
                .map(this::toResponseHistorico)
                .toList();
    }

    // Convert to response
    private Map<String, Object> toResponseHistorico(Map<String, AttributeValue> item) {
        List<Map<String, String>> processosPagos = new ArrayList<>();

        AttributeValue processosPagosAttr = item.get("processosPagos");

        if (processosPagosAttr != null && processosPagosAttr.l() != null) {
            for (AttributeValue processo : processosPagosAttr.l()) {
                Map<String, AttributeValue> processoMap = processo.m();

                processosPagos.add(Map.of(
                        "processNum", processoMap.get("processNum").s(),
                        "processValue", processoMap.get("processValue").n()
                ));
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", item.get("id").s());
        response.put("createdAt", item.get("createdAt").s());
        response.put("processosPagos", processosPagos);
        response.put("status", "PAGO");
        response.put("userName", item.get("userName").s());
        response.put("valorTotal", item.get("valorTotal").n());
        response.put("pdfKey", item.containsKey("pdfKey") ? item.get("pdfKey").s() : null);

        return response;
    }
}
