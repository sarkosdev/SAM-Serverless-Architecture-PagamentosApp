package pagamentos.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import pagamentos.repository.PagamentoRepository;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/*
* PagarProcessosService Class - Service Layer
*/
public class PagarProcessosService {

    private static final DateTimeFormatter CREATED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.of("UTC"));

    private final PagamentoRepository repository;
    private final PdfGeneratorService pdfGeneratorService;
    private final S3Service s3Service;

    public PagarProcessosService(PagamentoRepository repository, PdfGeneratorService pdfGeneratorService, S3Service s3Service) {
        this.repository = repository;
        this.pdfGeneratorService = pdfGeneratorService;
        this.s3Service = s3Service;
    }

    public Map<String, Object> pagarProcessos(List<String> listaProcess, String userName) {
        if (listaProcess == null || listaProcess.isEmpty()) {
            throw new IllegalArgumentException("listaProcess is required");
        }

        if (userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("userName is required");
        }

        BigDecimal total = BigDecimal.ZERO;

        List<Map<String, AttributeValue>> processosPagosParaGuardar = new ArrayList<>();
        List<Map<String, Object>> processosPagosResposta = new ArrayList<>();
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

            processosPagosParaGuardar.add(Map.of(
                    "processNum", AttributeValue.builder().s(item.get("processNum").s()).build(),
                    "processValue", AttributeValue.builder().n(processValue).build()
            ));

            processosPagosResposta.add(Map.of(
                    "processNum", item.get("processNum").s(),
                    "processValue", processValue
            ));
        }

        

        String pdfKey = null;

        if (!processosPagosResposta.isEmpty()) {
            Instant agora = Instant.now();
            String createdAt = CREATED_AT_FORMATTER.format(agora);

            try {
                byte[] pdfBytes = pdfGeneratorService.gerarPdfPagamento(userName, processosPagosResposta, total, createdAt);
                String nomeFicheiro = "comprovativo_" + userName + "_" + FILE_NAME_FORMATTER.format(agora) + ".pdf";
                pdfKey = "pagamentos/" + userName + "/" + nomeFicheiro;
                s3Service.uploadPdf(pdfKey, pdfBytes);
            } catch (IOException e) {
                throw new RuntimeException("Erro ao gerar o PDF do pagamento", e);
            }
        }

        buildPagamentoAndSaveIt(processosPagosParaGuardar, total, userName, pdfKey);

        Map<String, Object> response = new HashMap<>();
        response.put("total", total.toString());
        response.put("pagamentosPagos", processosPagosResposta);
        response.put("alreadyPaid", alreadyPaid);
        response.put("notFound", notFound);
        response.put("pdfKey", pdfKey);

        return response;
    }

    private void buildPagamentoAndSaveIt(List<Map<String, AttributeValue>> processosPagosParaGuardar, BigDecimal valorTotal, String userName, String pdfKey) {
        final String pagamentoId = UUID.randomUUID().toString();
        final String createdAt = CREATED_AT_FORMATTER.format(Instant.now());

        System.out.println("valor do cena :: " + createdAt);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(pagamentoId).build());
        item.put("type", AttributeValue.builder().s("PAGAMENTO").build());
        item.put("userName", AttributeValue.builder().s(userName).build());
        item.put("createdAt", AttributeValue.builder().s(createdAt).build());
        item.put("valorTotal", AttributeValue.builder().n(valorTotal.toString()).build());
        item.put("processosPagos", AttributeValue.builder()
                .l(processosPagosParaGuardar.stream()
                        .map(processo -> AttributeValue.builder().m(processo).build())
                        .toList())
                .build());

        if (pdfKey != null) {
            item.put("pdfKey", AttributeValue.builder().s(pdfKey).build());
        }

        this.repository.save(item);
    }
}