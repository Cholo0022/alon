// Registra compras y reparte los gastos para calcular el costo y el stock resultantes.
package ar.com.alon.purchases;

import static ar.com.alon.articles.ArticlePricing.calculateSalePrice;

import ar.com.alon.cash.CashLedger;
import ar.com.alon.purchases.dto.PurchaseInput;
import ar.com.alon.purchases.dto.PurchaseLineInput;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import ar.com.alon.stock.StockRepository;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseService extends StoreJdbcSupport {
    private final CashLedger cashLedger;
    private final StockRepository stock;

    /**
     * Recibe y conserva las dependencias necesarias para las compras, costos, stock y pagos de
     * proveedores.
     */
    public PurchaseService(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            Clock clock,
            AuditLog audit,
            StockRepository stock,
            CashLedger cashLedger) {
        super(jdbc, identity, clock, audit);
        this.stock = stock;
        this.cashLedger = cashLedger;
    }

    /**
     * Devuelve el proveedor activo sin nombre del comercio o lo crea bajo bloqueo para evitar
     * duplicados concurrentes.
     */
    @Transactional
    public Map<String, Object> defaultSupplier() {
        // Serializa la creación para evitar duplicados por comercio.
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        var rows =
                jdbc.queryForList(
                        "SELECT id,codigo,nombre FROM proveedores WHERE comercio_id=? AND LOWER(nombre)=LOWER(?) AND estado='ACTIVO' ORDER BY id LIMIT 1",
                        identity.storeId(),
                        "Proveedor sin nombre");
        if (!rows.isEmpty()) return rows.getFirst();
        long id =
                insert(
                        "INSERT INTO proveedores(comercio_id,codigo,nombre,cuit,especialidad,telefono,email) VALUES(?,?,?,?,?,?,?)",
                        identity.storeId(),
                        "PROV-"
                                + java.util
                                        .UUID
                                        .randomUUID()
                                        .toString()
                                        .substring(0, 8)
                                        .toUpperCase(),
                        "Proveedor sin nombre",
                        "",
                        "",
                        "",
                        "");
        return jdbc.queryForMap(
                "SELECT id,codigo,nombre FROM proveedores WHERE id=? AND comercio_id=?",
                id,
                identity.storeId());
    }

    /**
     * Registra cabecera, renglones, stock y deuda o pago de la compra. La decisión explícita de
     * costo modifica solo costo o conserva precios; las solicitudes anteriores mantienen el cálculo
     * histórico.
     */
    @Transactional
    public Map<String, Object> createPurchase(PurchaseInput input) {
        assertBelongs("proveedores", input.supplierId());
        if (input.lines() == null || input.lines().isEmpty())
            throw new IllegalArgumentException("Ingresá al menos un artículo en la factura.");
        LocalDate date = input.date() == null ? LocalDate.now(clock) : input.date();
        String docType = enumValue(input.documentType(), "FACTURA", "FACTURA", "REMITO");
        String docNumber =
                required(input.documentNumber(), "El número de comprobante es obligatorio.");
        BigDecimal extras =
                nonNegative(
                        input.extraCosts() == null ? BigDecimal.ZERO : input.extraCosts(),
                        "Los gastos adicionales no pueden ser negativos.");
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalQuantity = BigDecimal.ZERO;
        record PreparedLine(
                PurchaseLineInput line,
                BigDecimal quantity,
                BigDecimal cost,
                BigDecimal lineTotal,
                Map<String, Object> article) {}
        java.util.ArrayList<PreparedLine> lines = new java.util.ArrayList<>();
        input.lines().stream()
                .map(PurchaseLineInput::articleId)
                .distinct()
                .sorted()
                .forEach(this.stock::lockArticle);
        for (PurchaseLineInput line : input.lines()) {
            if (line == null)
                throw new IllegalArgumentException("Hay un artículo incompleto en la factura.");
            BigDecimal quantity =
                    positiveQuantity(
                            line.quantity(), "La cantidad comprada debe ser mayor a cero.");
            BigDecimal cost =
                    nonNegative(line.unitCost(), "El costo unitario no puede ser negativo.");
            Map<String, Object> article = this.stock.lockArticle(line.articleId());
            BigDecimal lineTotal = quantity.multiply(cost).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            totalQuantity = totalQuantity.add(quantity);
            lines.add(new PreparedLine(line, quantity, cost, lineTotal, article));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(extras).setScale(2, RoundingMode.HALF_UP);
        String paymentType =
                enumValue(
                        input.paymentType(),
                        "CONTADO",
                        "CONTADO",
                        "EFECTIVO_CAJA",
                        "EFECTIVO",
                        "CUENTA_CORRIENTE");
        BigDecimal paid =
                !"CUENTA_CORRIENTE".equals(paymentType)
                        ? total
                        : nonNegative(
                                input.paid() == null ? BigDecimal.ZERO : input.paid(),
                                "El importe pagado no puede ser negativo.");
        if (paid.compareTo(total) > 0)
            throw new IllegalArgumentException("El pago no puede superar el total de la compra.");
        String paymentMethod =
                enumValue(
                        input.paymentMethod(),
                        "EFECTIVO",
                        "EFECTIVO",
                        "TRANSFERENCIA",
                        "MERCADO_PAGO",
                        "CUENTA_CORRIENTE");
        if ("CUENTA_CORRIENTE".equals(paymentType) && paid.signum() == 0)
            paymentMethod = "CUENTA_CORRIENTE";
        else if ("CUENTA_CORRIENTE".equals(paymentMethod))
            throw new IllegalArgumentException("Elegí un medio de pago válido.");
        if ("EFECTIVO_CAJA".equals(paymentType) && !"EFECTIVO".equals(paymentMethod))
            throw new IllegalArgumentException(
                    "Efectivo de caja debe utilizar el medio de pago Efectivo.");
        // El pago externo se registra con el proveedor, sin descontarlo del arqueo.
        // Se conserva el comportamiento de las solicitudes anteriores que usaban CONTADO.
        boolean fromCash =
                "EFECTIVO_CAJA".equals(paymentType)
                        || paid.signum() > 0 && !"EFECTIVO".equals(paymentType);
        Long cashId = fromCash ? cashLedger.requireOpenCash(date) : null;
        long purchaseId =
                insert(
                        "INSERT INTO compras (comercio_id,proveedor_id,tipo_documento,numero_documento,fecha,subtotal,gastos_adicionales,total,pagado,saldo,estado,condicion_pago,medio_pago) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        identity.storeId(),
                        input.supplierId(),
                        docType,
                        docNumber,
                        Date.valueOf(date),
                        subtotal,
                        extras,
                        total,
                        paid,
                        total.subtract(paid),
                        paid.compareTo(total) == 0 ? "PAGADA" : "PENDIENTE",
                        "CONTADO".equals(paymentType) ? "EFECTIVO_CAJA" : paymentType,
                        paymentMethod);
        BigDecimal allocatedExtras = BigDecimal.ZERO;
        int row = 1;
        for (PreparedLine prepared : lines) {
            BigDecimal allocated;
            if (row == lines.size()) allocated = extras.subtract(allocatedExtras);
            else {
                BigDecimal weight =
                        subtotal.signum() > 0
                                ? prepared.lineTotal().divide(subtotal, 8, RoundingMode.HALF_UP)
                                : prepared.quantity()
                                        .divide(totalQuantity, 8, RoundingMode.HALF_UP);
                allocated = extras.multiply(weight).setScale(2, RoundingMode.DOWN);
                allocatedExtras = allocatedExtras.add(allocated);
            }
            BigDecimal landedValue = prepared.lineTotal().add(allocated);
            jdbc.update(
                    "INSERT INTO compra_detalle (compra_id,renglon,articulo_id,cantidad,costo_unitario,total_linea) VALUES (?,?,?,?,?,?)",
                    purchaseId,
                    row++,
                    prepared.line().articleId(),
                    prepared.quantity(),
                    prepared.cost(),
                    prepared.lineTotal());
            Map<String, Object> article = this.stock.lockArticle(prepared.line().articleId());
            BigDecimal stock = new BigDecimal(article.get("stock").toString());
            BigDecimal oldCost = new BigDecimal(article.get("costo").toString());
            BigDecimal weightedCost =
                    stock.multiply(oldCost)
                            .add(landedValue)
                            .divide(stock.add(prepared.quantity()), 2, RoundingMode.HALF_UP);
            BigDecimal freight = new BigDecimal(article.get("flete_unitario").toString());
            BigDecimal freightPercent = new BigDecimal(article.get("flete_porcentaje").toString());
            BigDecimal margin = new BigDecimal(article.get("margen_porcentaje").toString());
            Object roundFlag = article.get("redondeo_precio");
            boolean roundPrice =
                    roundFlag instanceof Boolean flag
                            ? flag
                            : roundFlag instanceof Number number && number.intValue() != 0;
            BigDecimal salePrice =
                    calculateSalePrice(weightedCost, freight, freightPercent, margin, roundPrice);
            if (input.updateCost() == null) {
                // Compatibilidad con solicitudes anteriores a la confirmación de costo.
                jdbc.update(
                        "UPDATE articulos SET stock=stock+?,costo=?,precio_venta=? WHERE id=? AND comercio_id=?",
                        prepared.quantity(),
                        weightedCost,
                        salePrice,
                        prepared.line().articleId(),
                        identity.storeId());
            } else if (input.updateCost()) {
                jdbc.update(
                        "UPDATE articulos SET stock=stock+?,costo=? WHERE id=? AND comercio_id=?",
                        prepared.quantity(),
                        prepared.cost(),
                        prepared.line().articleId(),
                        identity.storeId());
            } else {
                jdbc.update(
                        "UPDATE articulos SET stock=stock+? WHERE id=? AND comercio_id=?",
                        prepared.quantity(),
                        prepared.line().articleId(),
                        identity.storeId());
            }
            this.stock.addStockMovement(
                    prepared.line().articleId(),
                    "COMPRA",
                    prepared.quantity(),
                    docNumber,
                    "Ingreso por " + docType.toLowerCase(java.util.Locale.ROOT));
        }
        jdbc.update(
                "UPDATE proveedores SET saldo_actual=saldo_actual+? WHERE id=? AND comercio_id=?",
                total.subtract(paid),
                input.supplierId(),
                identity.storeId());
        if (paid.signum() > 0) {
            String receipt = "PAG-COMPRA-" + purchaseId;

            insert(
                    "INSERT INTO pagos_proveedores (comercio_id,compra_id,proveedor_id,caja_id,fecha,medio_pago,importe,numero_recibo) VALUES (?,?,?,?,?,?,?,?)",
                    identity.storeId(),
                    purchaseId,
                    input.supplierId(),
                    cashId,
                    Date.valueOf(date),
                    paymentMethod,
                    paid,
                    receipt);
            if (cashId != null)
                cashLedger.addCashMovement(
                        cashId,
                        "EGRESO",
                        "PAGO_PROVEEDOR",
                        paymentMethod,
                        date,
                        paid,
                        receipt,
                        "Pago de compra " + docNumber);
        }
        return Map.of(
                "id",
                purchaseId,
                "numero_documento",
                docNumber,
                "articulos",
                lines.size(),
                "subtotal",
                subtotal,
                "total",
                total,
                "saldo",
                total.subtract(paid));
    }

    /**
     * Corrige una compra reconstruyendo cantidades y costo ponderado, ajusta saldo del proveedor y
     * audita; rechaza cambios incompatibles con pagos o salidas posteriores.
     */
    @Transactional
    public Map<String, Object> updatePurchase(long id, PurchaseInput input) {
        Map<String, Object> current =
                jdbc.queryForMap(
                        "SELECT proveedor_id,fecha,total,pagado,numero_documento,gastos_adicionales FROM compras WHERE id=? AND comercio_id=? FOR UPDATE",
                        id,
                        identity.storeId());
        long supplierId = ((Number) current.get("proveedor_id")).longValue();
        if (input.supplierId() != supplierId)
            throw new IllegalArgumentException(
                    "No se puede cambiar el proveedor de una factura existente.");
        if (input.lines() == null || input.lines().isEmpty())
            throw new IllegalArgumentException("Ingresá al menos un artículo en la factura.");
        BigDecimal extras =
                nonNegative(
                        input.extraCosts() == null ? BigDecimal.ZERO : input.extraCosts(),
                        "Los gastos adicionales no pueden ser negativos.");
        Map<Long, BigDecimal[]> newLines = new java.util.LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int row = 1;
        for (PurchaseLineInput line : input.lines()) {
            BigDecimal qty =
                    positiveQuantity(
                            line.quantity(), "La cantidad comprada debe ser mayor a cero.");
            BigDecimal cost =
                    nonNegative(line.unitCost(), "El costo unitario no puede ser negativo.");
            this.stock.lockArticle(line.articleId());
            BigDecimal lineTotal = qty.multiply(cost).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            BigDecimal[] previous = newLines.get(line.articleId());
            if (previous == null)
                newLines.put(line.articleId(), new BigDecimal[] {qty, cost, lineTotal});
            else {
                BigDecimal mergedQuantity = previous[0].add(qty);
                BigDecimal mergedTotal = previous[2].add(lineTotal);
                newLines.put(
                        line.articleId(),
                        new BigDecimal[] {
                            mergedQuantity,
                            mergedTotal.divide(mergedQuantity, 8, RoundingMode.HALF_UP),
                            mergedTotal
                        });
            }
            row++;
        }
        BigDecimal total = subtotal.add(extras).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = new BigDecimal(current.get("pagado").toString());
        if (total.compareTo(paid) < 0)
            throw new IllegalArgumentException(
                    "El total no puede ser menor a lo que ya se pagó de esta factura.");
        List<Map<String, Object>> oldLines =
                jdbc.queryForList(
                        "SELECT articulo_id,cantidad,costo_unitario,total_linea FROM compra_detalle WHERE compra_id=? ORDER BY renglon",
                        id);
        Map<Long, BigDecimal> oldQuantities = new java.util.TreeMap<>();
        Map<Long, BigDecimal> oldValues = new java.util.TreeMap<>();
        var oldAllocation =
                PurchaseValuation.allocate(
                        oldLines.stream()
                                .map(
                                        line ->
                                                new PurchaseValuation.Line(
                                                        ((Number) line.get("articulo_id"))
                                                                .longValue(),
                                                        new BigDecimal(
                                                                line.get("cantidad").toString()),
                                                        new BigDecimal(
                                                                line.get("total_linea")
                                                                        .toString())))
                                .toList(),
                        new BigDecimal(current.get("gastos_adicionales").toString()));
        oldAllocation.forEach(
                line -> {
                    oldQuantities.merge(line.articleId(), line.quantity(), BigDecimal::add);
                    oldValues.merge(line.articleId(), line.value(), BigDecimal::add);
                });
        var allocation =
                PurchaseValuation.allocate(
                        newLines.entrySet().stream()
                                .map(
                                        entry ->
                                                new PurchaseValuation.Line(
                                                        entry.getKey(),
                                                        entry.getValue()[0],
                                                        entry.getValue()[2]))
                                .toList(),
                        extras);
        Map<Long, BigDecimal> newValues = new java.util.TreeMap<>();
        allocation.forEach(line -> newValues.put(line.articleId(), line.value()));
        java.util.Set<Long> articleIds = new java.util.TreeSet<>(oldQuantities.keySet());
        articleIds.addAll(newLines.keySet());
        for (Long articleId : articleIds) {
            BigDecimal before = oldQuantities.getOrDefault(articleId, BigDecimal.ZERO);
            BigDecimal after =
                    newLines.containsKey(articleId) ? newLines.get(articleId)[0] : BigDecimal.ZERO;
            BigDecimal delta = after.subtract(before);
            // Las ventas anteriores conservan el costo que tenían al registrarse.
            Integer outflows =
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM movimientos_stock WHERE comercio_id=? AND articulo_id=? AND cantidad<0 AND fecha_hora>=?",
                            Integer.class,
                            identity.storeId(),
                            articleId,
                            current.get("fecha"));
            if (outflows != null && outflows > 0)
                throw new IllegalArgumentException(
                        "Hay salidas posteriores de este artículo. Registrá una corrección en lugar de editar la compra.");
            Map<String, Object> article = this.stock.lockArticle(articleId);
            BigDecimal stock = new BigDecimal(article.get("stock").toString());
            BigDecimal weighted =
                    PurchaseValuation.revisedCost(
                            stock,
                            new BigDecimal(article.get("costo").toString()),
                            before,
                            oldValues.getOrDefault(articleId, BigDecimal.ZERO),
                            after,
                            newValues.getOrDefault(articleId, BigDecimal.ZERO));
            BigDecimal freight = new BigDecimal(article.get("flete_unitario").toString());
            BigDecimal freightPercent = new BigDecimal(article.get("flete_porcentaje").toString());
            BigDecimal margin = new BigDecimal(article.get("margen_porcentaje").toString());
            Object roundFlag = article.get("redondeo_precio");
            boolean roundPrice =
                    Boolean.TRUE.equals(roundFlag)
                            || roundFlag instanceof Number number && number.intValue() != 0;
            jdbc.update(
                    "UPDATE articulos SET stock=?,costo=?,precio_venta=? WHERE id=? AND comercio_id=?",
                    stock.add(delta),
                    weighted,
                    calculateSalePrice(weighted, freight, freightPercent, margin, roundPrice),
                    articleId,
                    identity.storeId());
            if (delta.signum() != 0)
                this.stock.addStockMovement(
                        articleId,
                        "AJUSTE_COMPRA",
                        delta,
                        current.get("numero_documento").toString(),
                        "Corrección de compra " + id);
        }
        audit.record(
                "compra", id, "CORRECCION", Map.of("cabecera", current, "lineas", oldLines), input);
        jdbc.update("DELETE FROM compra_detalle WHERE compra_id=?", id);
        row = 1;
        for (Map.Entry<Long, BigDecimal[]> entry : newLines.entrySet()) {
            BigDecimal[] values = entry.getValue();
            jdbc.update(
                    "INSERT INTO compra_detalle (compra_id,renglon,articulo_id,cantidad,costo_unitario,total_linea) VALUES (?,?,?,?,?,?)",
                    id,
                    row++,
                    entry.getKey(),
                    values[0],
                    values[1].setScale(2, RoundingMode.HALF_UP),
                    values[2]);
        }
        BigDecimal saldo = total.subtract(paid);
        String status =
                saldo.signum() == 0 ? "PAGADA" : paid.signum() > 0 ? "PARCIAL" : "PENDIENTE";
        jdbc.update(
                "UPDATE compras SET tipo_documento=?,numero_documento=?,fecha=?,subtotal=?,gastos_adicionales=?,total=?,saldo=?,estado=? WHERE id=? AND comercio_id=?",
                enumValue(input.documentType(), "FACTURA", "FACTURA", "REMITO"),
                required(input.documentNumber(), "El número de comprobante es obligatorio."),
                Date.valueOf(input.date() == null ? LocalDate.now(clock) : input.date()),
                subtotal,
                extras,
                total,
                saldo,
                status,
                id,
                identity.storeId());
        BigDecimal oldBalance = new BigDecimal(current.get("total").toString()).subtract(paid);
        jdbc.update(
                "UPDATE proveedores SET saldo_actual=saldo_actual+? WHERE id=? AND comercio_id=?",
                saldo.subtract(oldBalance),
                supplierId,
                identity.storeId());
        return Map.of("id", id, "total", total, "pagado", paid, "saldo", saldo);
    }
}
