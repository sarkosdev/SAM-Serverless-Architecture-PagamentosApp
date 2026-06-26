package pagamentos.model;

public record CreatePagamentoRequest(
        String processNum,
        String processValue
) {
}
