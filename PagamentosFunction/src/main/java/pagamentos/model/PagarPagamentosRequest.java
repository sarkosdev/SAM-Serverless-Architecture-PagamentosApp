package pagamentos.model;

import java.util.List;

public record PagarPagamentosRequest(
        List<String> listaProcess
) {
}
