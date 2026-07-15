package historico.service;

import java.util.List;
import java.util.Map;

import historico.repository.HistoricoRepository;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;


/**
 * 
 * HistoricoService Class
 */
public class HistoricoService {
    
    private final HistoricoRepository repository;

    public HistoricoService(HistoricoRepository repository){
        this.repository = repository;
    }


    // List 'Historico' by userName
    public List<Map<String, Object>> listHistoricoByUser(String userName) {
        if(userName.isEmpty()) throw new IllegalArgumentException("userName is required");
    
        return repository.findAllHistoricoByUser(userName)
                .stream()
                .map(this::toResponseHistorico)
                .toList();
    }










    private Map<String, Object> toResponseHistorico(Map<String, AttributeValue> item) {
        return Map.of(
                "id", item.get("id").s(),
                "createdAt", item.get("createdAt").s(),
                "processosPagos", item.get("processosPagos").s(),
                "status", item.get("status").s(),
                "type", item.get("type").s(),
                "userName", item.get("userName").s(),
                "valorTotal", item.get("valorTotal")  
        );
    }
}
