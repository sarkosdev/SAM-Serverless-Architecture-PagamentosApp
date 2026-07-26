package pagamentos.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * PdfGeneratorService Service Class - Service Layer
 */
public class PdfGeneratorService {

    public byte[] gerarPdfPagamento(String userName, List<Map<String, Object>> processosPagos, BigDecimal total, String createdAt) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 50;
                float y = page.getMediaBox().getHeight() - margin;
                float lineHeight = 18;

                var fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                var fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Título
                content.beginText();
                content.setFont(fontBold, 16);
                content.newLineAtOffset(margin, y);
                content.showText("Comprovativo de Pagamento");
                content.endText();
                y -= lineHeight * 2;

                // Info do utilizador e data
                content.beginText();
                content.setFont(fontRegular, 11);
                content.newLineAtOffset(margin, y);
                content.showText("Credor: " + userName);
                content.endText();
                y -= lineHeight;

                content.beginText();
                content.setFont(fontRegular, 11);
                content.newLineAtOffset(margin, y);
                content.showText("Data: " + createdAt);
                content.endText();
                y -= lineHeight * 2;

                // Cabeçalho da tabela
                content.beginText();
                content.setFont(fontBold, 11);
                content.newLineAtOffset(margin, y);
                content.showText("Nº Processo");
                content.newLineAtOffset(200, 0);
                content.showText("Valor Pago");
                content.endText();
                y -= lineHeight;

                content.moveTo(margin, y + 5);
                content.lineTo(page.getMediaBox().getWidth() - margin, y + 5);
                content.stroke();
                y -= 5;

                // Linhas dos processos pagos
                for (Map<String, Object> processo : processosPagos) {
                    if (y < margin + lineHeight) {
                        // Se não houver espaço, podia adicionar nova página aqui (omitted para simplicidade)
                        break;
                    }

                    content.beginText();
                    content.setFont(fontRegular, 10);
                    content.newLineAtOffset(margin, y);
                    content.showText(String.valueOf(processo.get("processNum")));
                    content.newLineAtOffset(200, 0);
                    content.showText(String.valueOf(processo.get("processValue")) + " EUR");
                    content.endText();
                    y -= lineHeight;
                }

                y -= lineHeight;
                content.moveTo(margin, y + 5);
                content.lineTo(page.getMediaBox().getWidth() - margin, y + 5);
                content.stroke();
                y -= lineHeight;

                // Total
                content.beginText();
                content.setFont(fontBold, 12);
                content.newLineAtOffset(margin, y);
                content.showText("Total: " + total.toString() + " EUR");
                content.endText();
            }

            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}