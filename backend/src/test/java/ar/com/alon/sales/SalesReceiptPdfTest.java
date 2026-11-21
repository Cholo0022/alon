package ar.com.alon.sales;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.*;
import org.junit.jupiter.api.Test;

class SalesReceiptPdfTest {
    private Map<String, Object> data(int count, boolean longNames) {
        var data = new LinkedHashMap<String, Object>();
        data.put("negocio", "Ferretería Ejemplo");
        data.put("slogan", "Todo para tu proyecto");
        data.put("telefono_tienda", "3446 - 236311");
        data.put("numero", "V-000001");
        data.put("fecha", LocalDate.of(2026, 11, 4));
        data.put("cliente", "Consumidor final");
        data.put("tipo_venta", "CONTADO");
        data.put("medio_pago", "EFECTIVO");
        data.put("estado", "CONFIRMADA");
        data.put("subtotal", new BigDecimal("1250").multiply(BigDecimal.valueOf(count)));
        data.put("descuento", new BigDecimal("62.50").multiply(BigDecimal.valueOf(count)));
        data.put("total", new BigDecimal("1187.50").multiply(BigDecimal.valueOf(count)));
        var rows = new ArrayList<Map<String, Object>>();
        for (int i = 1; i <= count; i++)
            rows.add(
                    Map.of(
                            "codigo",
                            "A-" + i,
                            "nombre",
                            longNames
                                    ? "Artículo con descripción extensa, talle, color y referencia "
                                                    .repeat(3)
                                            + i
                                    : "Artículo de prueba " + i,
                            "cantidad",
                            new BigDecimal("2.5"),
                            "unidad",
                            "UNIDAD",
                            "precio_unitario",
                            new BigDecimal("500.00"),
                            "descuento_pct",
                            new BigDecimal("5"),
                            "total_linea",
                            new BigDecimal("1187.50")));
        data.put("lineas", rows);
        return data;
    }

    private static class Positions extends PDFTextStripper {
        float maxY;

        Positions() throws java.io.IOException {}

        @Override
        protected void processTextPosition(TextPosition text) {
            maxY = Math.max(maxY, text.getYDirAdj() + text.getHeightDir());
            super.processTextPosition(text);
        }
    }

    @Test
    void shortReceiptFitsUpperHalfA4() throws Exception {
        byte[] pdf = SalesReceiptPdf.create(data(4, false));
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(document.getPage(0).getMediaBox().getWidth())
                    .isEqualTo(PDRectangle.A4.getWidth());
            var positions = new Positions();
            String text = positions.getText(document);
            assertThat(text)
                    .contains(
                            "Ferretería Ejemplo",
                            "Celular: 3446 - 236311",
                            "V-000001",
                            "04/11/2026",
                            "Consumidor final",
                            "4.750,00",
                            "no fiscal");
            assertThat(positions.maxY).isLessThan(PDRectangle.A4.getHeight() / 2);
            if (Boolean.getBoolean("alon.receipt.preview")) {
                Path directory = Path.of("../output/pdf");
                Files.createDirectories(directory);
                Files.write(directory.resolve("venta-ejemplo.pdf"), pdf);
                ImageIO.write(
                        new PDFRenderer(document).renderImageWithDPI(0, 110),
                        "PNG",
                        directory.resolve("venta-ejemplo.png").toFile());
            }
        }
    }

    @Test
    void serviceReceiptFitsUpperHalfA4AndShowsCharges() throws Exception {
        var receipt =
                Map.<String, Object>ofEntries(
                        Map.entry("id", 42),
                        Map.entry("nombre", "Comercio Ejemplo"),
                        Map.entry("fecha", LocalDate.of(2026, 11, 17)),
                        Map.entry("periodo_desde", LocalDate.of(2026, 11, 1)),
                        Map.entry("meses", 2),
                        Map.entry("abono_unitario", new BigDecimal("1000")),
                        Map.entry("cargo_configuracion", new BigDecimal("500")),
                        Map.entry("subtotal", new BigDecimal("2500")),
                        Map.entry("bonificacion", new BigDecimal("10")),
                        Map.entry("total", new BigDecimal("2300")),
                        Map.entry("estado", "ANULADO"),
                        Map.entry("motivo_anulacion", "Error al cargar el pago"));
        try (var document = Loader.loadPDF(SalesReceiptPdf.createServiceReceipt(receipt))) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            var pageSize = document.getPage(0).getMediaBox();
            assertThat(pageSize.getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(pageSize.getHeight()).isEqualTo(PDRectangle.A4.getHeight());
            var positions = new Positions();
            assertThat(positions.getText(document))
                    .contains(
                            "RECIBO DE SERVICIO #42",
                            "RECIBO ANULADO",
                            "11/2026 a 12/2026",
                            "Configuración por única vez",
                            "2.300,00",
                            "Error al cargar el pago");
            assertThat(positions.maxY).isLessThan(PDRectangle.A4.getHeight() / 2);
        }
    }

    @Test
    void manyLongRowsPaginateWithoutClippingOrOmittingItems() throws Exception {
        try (var document = Loader.loadPDF(SalesReceiptPdf.create(data(80, true)))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            var positions = new Positions();
            String text = positions.getText(document);
            for (int i = 1; i <= 80; i++) assertThat(text).contains("A-" + i);
            assertThat(text).contains("95.000,00", "Continuación");
            assertThat(positions.maxY).isLessThan(PDRectangle.A4.getHeight() - 20);
            if (Boolean.getBoolean("alon.receipt.preview")) {
                Path directory = Path.of("../tmp/pdfs");
                Files.createDirectories(directory);
                ImageIO.write(
                        new PDFRenderer(document)
                                .renderImageWithDPI(document.getNumberOfPages() - 1, 110),
                        "PNG",
                        directory.resolve("venta-larga-ultima.png").toFile());
            }
        }
    }
}
