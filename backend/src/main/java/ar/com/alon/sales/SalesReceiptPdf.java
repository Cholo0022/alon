package ar.com.alon.sales;

import java.awt.Color;
import java.io.*;
import java.math.BigDecimal;
import java.text.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;

/** Imprime un comprobante interno compacto, en la mitad superior de A4 cuando el contenido cabe. */
public final class SalesReceiptPdf {
    private SalesReceiptPdf() {}

    public static byte[] create(Map<String, Object> data) throws IOException {
        try (var document = new PDDocument();
                var out = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Venta " + value(data.get("numero")));
            document.getDocumentInformation().setAuthor(value(data.get("negocio")));
            try (var page = new Receipt(document, data)) {
                page.render();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    public static byte[] createServiceReceipt(Map<String, Object> data) throws IOException {
        try (var document = new PDDocument();
                var out = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("Recibo Alon #" + value(data.get("id")));
            document.getDocumentInformation().setAuthor("Alon");
            try (var page = new Receipt(document, data)) {
                page.renderServiceReceipt();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private static final class Receipt implements AutoCloseable {
        private final PDDocument document;
        private final Map<String, Object> data;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final DecimalFormat money =
                new DecimalFormat(
                        "#,##0.00",
                        DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
        private final DecimalFormat quantity =
                new DecimalFormat(
                        "#,##0.###",
                        DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")));
        private PDPageContentStream content;
        private float y;
        private final float left = 32, right = PDRectangle.A4.getWidth() - 32;

        Receipt(PDDocument document, Map<String, Object> data) {
            this.document = document;
            this.data = data;
        }

        private String clean(String text) throws IOException {
            var result = new StringBuilder();
            for (int cp : text.codePoints().toArray()) {
                String part = Character.isISOControl(cp) ? " " : new String(Character.toChars(cp));
                try {
                    regular.encode(part);
                    result.append(part);
                } catch (IllegalArgumentException unsupported) {
                    result.append('?');
                }
            }
            return result.toString();
        }

        private float width(String text, float size, boolean strong) throws IOException {
            return (strong ? bold : regular).getStringWidth(text) * size / 1000;
        }

        private List<String> wrap(String text, float max, float size, boolean strong)
                throws IOException {
            text = clean(text);
            var lines = new ArrayList<String>();
            String line = "";
            for (String word : text.split(" +")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (width(candidate, size, strong) <= max) {
                    line = candidate;
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line);
                    line = "";
                }
                for (int cp : word.codePoints().toArray()) {
                    String ch = new String(Character.toChars(cp));
                    if (!line.isEmpty() && width(line + ch, size, strong) > max) {
                        lines.add(line);
                        line = "";
                    }
                    line += ch;
                }
            }
            if (!line.isEmpty() || lines.isEmpty()) lines.add(line);
            return lines;
        }

        private void text(String text, float x, float baseline, float size, boolean strong)
                throws IOException {
            content.beginText();
            content.setFont(strong ? bold : regular, size);
            content.newLineAtOffset(x, baseline);
            content.showText(clean(text));
            content.endText();
        }

        private void paragraph(String text, float size, boolean strong) throws IOException {
            for (String line : wrap(text, right - left, size, strong)) {
                text(line, left, y, size, strong);
                y -= size + 4;
            }
        }

        private void rule() throws IOException {
            content.setStrokingColor(new Color(190, 198, 205));
            content.setLineWidth(.5f);
            content.moveTo(left, y);
            content.lineTo(right, y);
            content.stroke();
            y -= 12;
        }

        private void page() throws IOException {
            newPage();
            paragraph(value(data.get("negocio")), 15, true);
            saleHeader();
        }

        private void newPage() throws IOException {
            if (content != null) content.close();
            var page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = PDRectangle.A4.getHeight() - 32;
            content.setNonStrokingColor(Color.BLACK);
        }

        private void saleHeader() throws IOException {
            if (!value(data.get("slogan")).isBlank())
                paragraph(value(data.get("slogan")), 9, false);
            if (!value(data.get("telefono_tienda")).isBlank())
                paragraph("Celular: " + value(data.get("telefono_tienda")), 9, false);
            y -= 3;
            rule();
            paragraph(
                    "COMPROBANTE DE VENTA"
                            + (document.getNumberOfPages() > 1 ? " - Continuación" : ""),
                    11,
                    true);
            if ("CANCELADA".equals(data.get("estado"))) paragraph("VENTA CANCELADA", 12, true);
            paragraph(
                    "Número: "
                            + value(data.get("numero"))
                            + "   |   Fecha: "
                            + LocalDate.parse(value(data.get("fecha")).substring(0, 10))
                                    .format(DateTimeFormatter.ofPattern("dd/MM/yy")),
                    9,
                    false);
            paragraph("Cliente: " + value(data.get("cliente")), 9, false);
            paragraph(
                    "Condición: "
                            + value(data.get("tipo_venta")).replace('_', ' ')
                            + "   |   Pago: "
                            + value(data.get("medio_pago")).replace('_', ' '),
                    9,
                    false);
            y -= 5;
        }

        private void tableHeader() throws IOException {
            content.setNonStrokingColor(new Color(232, 237, 241));
            content.addRect(left, y - 17, right - left, 20);
            content.fill();
            content.setNonStrokingColor(Color.BLACK);
            text("Código", left + 4, y - 10, 8, true);
            text("Artículo", 96, y - 10, 8, true);
            text("Cantidad", 282, y - 10, 8, true);
            text("P. unitario", 338, y - 10, 8, true);
            text("Dto. %", 420, y - 10, 8, true);
            text("Importe", 490, y - 10, 8, true);
            y -= 28;
        }

        private void serviceHeader() throws IOException {
            newPage();
            paragraph("Alon", 17, true);
            rule();
            paragraph(
                    "RECIBO DE SERVICIO #"
                            + value(data.get("id"))
                            + (document.getNumberOfPages() > 1 ? " - Continuación" : ""),
                    12,
                    true);
            if ("ANULADO".equals(data.get("estado"))) paragraph("RECIBO ANULADO", 12, true);
            paragraph("Negocio: " + value(data.get("nombre")), 10, false);
        }

        private void renderServiceReceipt() throws IOException {
            serviceHeader();
            var date = DateTimeFormatter.ofPattern("dd/MM/yy");
            var month = DateTimeFormatter.ofPattern("MM/yyyy");
            LocalDate start = LocalDate.parse(value(data.get("periodo_desde")));
            int months = ((Number) data.get("meses")).intValue();
            paragraph("Fecha: " + LocalDate.parse(value(data.get("fecha"))).format(date), 9, false);
            paragraph(
                    "Período cubierto: "
                            + start.format(month)
                            + " a "
                            + start.plusMonths(months - 1L).format(month)
                            + " ("
                            + months
                            + " meses)",
                    9,
                    false);
            String detail = value(data.get("abonos_detalle"));
            if (detail.isBlank()) {
                summary("Abono mensual", decimal(data.get("abono_unitario")), false);
            } else {
                for (String line : detail.lines().toList()) {
                    if (y < PDRectangle.A4.getHeight() / 2 + 48) serviceHeader();
                    paragraph(line, 9, false);
                }
            }
            if (y < PDRectangle.A4.getHeight() / 2 + 162) serviceHeader();
            BigDecimal setup = decimal(data.get("cargo_configuracion"));
            if (setup.signum() > 0) summary("Configuración por única vez", setup, false);
            summary("Subtotal", decimal(data.get("subtotal")), false);
            BigDecimal discount = decimal(data.get("bonificacion"));
            if (discount.signum() > 0)
                summary(
                        "Bonificación del abono (" + quantity.format(discount) + " %)",
                        decimal(data.get("subtotal")).subtract(decimal(data.get("total"))),
                        false);
            rule();
            summary("TOTAL ARS", decimal(data.get("total")), true);
            if ("ANULADO".equals(data.get("estado")))
                paragraph("Motivo: " + value(data.get("motivo_anulacion")), 9, false);
            paragraph("Recibo interno del servicio Alon.", 8, false);
        }

        @SuppressWarnings("unchecked")
        void render() throws IOException {
            page();
            tableHeader();
            var rows = (List<Map<String, Object>>) data.getOrDefault("lineas", List.of());
            for (var row : rows) {
                var code = wrap(value(row.get("codigo")), 52, 8, false);
                var name = wrap(value(row.get("nombre")), 176, 8.5f, false);
                var qty =
                        wrap(
                                quantity.format(decimal(row.get("cantidad")))
                                        + " "
                                        + unit(value(row.get("unidad"))),
                                44,
                                8,
                                false);
                float height = Math.max(Math.max(code.size(), name.size()), qty.size()) * 11 + 9;
                if (y - height < 95) {
                    page();
                    tableHeader();
                }
                for (int i = 0; i < code.size(); i++)
                    text(code.get(i), left + 4, y - i * 11, 8, false);
                for (int i = 0; i < name.size(); i++)
                    text(name.get(i), 96, y - i * 11, 8.5f, false);
                for (int i = 0; i < qty.size(); i++) text(qty.get(i), 282, y - i * 11, 8, false);
                String unit = money.format(decimal(row.get("precio_unitario")));
                text(unit, 410 - width(unit, 8, false), y, 8, false);
                String discount = quantity.format(decimal(row.get("descuento_pct")));
                text(discount, 455 - width(discount, 8, false), y, 8, false);
                String total = money.format(decimal(row.get("total_linea")));
                text(total, right - 4 - width(total, 8, false), y, 8, false);
                y -= height;
                rule();
            }
            if (y < 135) {
                page();
            }
            y -= 3;
            summary("Subtotal", decimal(data.get("subtotal")), false);
            if (decimal(data.get("descuento")).signum() != 0)
                summary("Descuentos", decimal(data.get("descuento")), false);
            summary("TOTAL ARS", decimal(data.get("total")), true);
            y -= 5;
            paragraph("Comprobante interno no fiscal. No válido como factura fiscal.", 8, false);
        }

        private String unit(String value) {
            return switch (value) {
                case "UNIDAD" -> "un";
                case "KILO" -> "kg";
                case "LITRO" -> "l";
                case "METRO" -> "m";
                case "CAJA" -> "caja";
                default -> value;
            };
        }

        private void summary(String label, BigDecimal amount, boolean strong) throws IOException {
            float size = strong ? 13 : 9;
            String number = "$ " + money.format(amount);
            text(label, left, y, size, strong);
            text(number, right - width(number, size, strong), y, size, strong);
            y -= size + 7;
        }

        @Override
        public void close() throws IOException {
            if (content != null) content.close();
        }
    }
}
