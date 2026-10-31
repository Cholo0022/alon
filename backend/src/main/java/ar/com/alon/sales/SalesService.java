// Guarda la venta, el movimiento de caja y la salida de stock en una misma operación.
package ar.com.alon.sales;

import ar.com.alon.cash.CashLedger;
import ar.com.alon.sales.dto.SaleInput;
import ar.com.alon.sales.dto.SaleLine;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import ar.com.alon.shared.web.BusinessConflict;
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
public class SalesService extends StoreJdbcSupport {
    private final CashLedger cashLedger;
    private final StockRepository stock;

    /**
     * Recibe y conserva las dependencias necesarias para las ventas, sus reversos y movimientos
     * asociados.
     */
    public SalesService(
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
     * Valida artículos, cantidades, descuentos y stock; registra la venta y sus movimientos de
     * stock y caja o deuda dentro de una transacción.
     */
    @Transactional
    public Map<String, Object> createSale(SaleInput input) {
        return createSale(input, null, Map.of());
    }

    /** Los precios del pedido revisado se conservan al confirmar su venta web. */

    /**
     * Registra una venta vinculada a un pedido web usando sus precios acordados y verificando que
     * cada artículo tenga precio válido.
     */
    @Transactional
    public Map<String, Object> createWebSale(
            SaleInput input, long orderId, Map<Long, BigDecimal> prices) {
        identity.require("tienda");
        if (orderId <= 0
                || input.lines().stream().anyMatch(line -> !prices.containsKey(line.articleId())))
            throw new IllegalArgumentException("El detalle del pedido web no es válido.");
        return createSale(input, orderId, prices);
    }

    /**
     * Valida artículos, cantidades, descuentos y stock; registra la venta y sus movimientos de
     * stock y caja o deuda dentro de una transacción.
     */
    private Map<String, Object> createSale(
            SaleInput input, Long orderId, Map<Long, BigDecimal> prices) {
        assertBelongs("clientes", input.customerId());
        LocalDate date = input.date() == null ? LocalDate.now(clock) : input.date();
        String saleType = enumValue(input.saleType(), "CONTADO", "CONTADO", "CUENTA_CORRIENTE");
        String payment =
                enumValue(
                        input.paymentMethod(),
                        "EFECTIVO",
                        "EFECTIVO",
                        "TRANSFERENCIA",
                        "MERCADO_PAGO",
                        "CUENTA_CORRIENTE");
        if (saleType.equals("CUENTA_CORRIENTE")) payment = "CUENTA_CORRIENTE";
        else if (payment.equals("CUENTA_CORRIENTE"))
            throw new IllegalArgumentException(
                    "Elegí cuenta corriente como tipo de venta para registrar una deuda.");
        if (input.lines() == null || input.lines().isEmpty())
            throw new IllegalArgumentException("Agregá al menos un artículo a la venta.");

        BigDecimal subtotal = BigDecimal.ZERO,
                discount = BigDecimal.ZERO,
                total = BigDecimal.ZERO,
                costTotal = BigDecimal.ZERO;
        record PricedLine(
                long articleId,
                BigDecimal quantity,
                BigDecimal price,
                BigDecimal cost,
                BigDecimal discountPercent,
                BigDecimal lineTotal) {}
        java.util.ArrayList<PricedLine> lines = new java.util.ArrayList<>();
        java.util.Map<Long, BigDecimal> reservedStock = new java.util.HashMap<>();
        input.lines().stream()
                .map(SaleLine::articleId)
                .distinct()
                .sorted()
                .forEach(this.stock::lockArticle);
        for (SaleLine line : input.lines()) {
            BigDecimal quantity =
                    positiveQuantity(line.quantity(), "La cantidad vendida debe ser mayor a cero.");
            Map<String, Object> article = this.stock.lockArticle(line.articleId());
            BigDecimal stock = new BigDecimal(article.get("stock").toString());
            BigDecimal reserved =
                    reservedStock.getOrDefault(line.articleId(), BigDecimal.ZERO).add(quantity);
            if (stock.compareTo(reserved) < 0)
                throw new BusinessConflict(
                        "Stock insuficiente para "
                                + article.get("nombre")
                                + ". Disponible: "
                                + stock.toPlainString());
            reservedStock.put(line.articleId(), reserved);
            BigDecimal price =
                    orderId == null
                            ? new BigDecimal(article.get("precio_venta").toString())
                            : nonNegative(
                                    prices.get(line.articleId()),
                                    "El precio del pedido no es válido.");
            BigDecimal cost = new BigDecimal(article.get("costo").toString());
            BigDecimal percent =
                    nonNegative(
                            line.discountPercent() == null
                                    ? BigDecimal.ZERO
                                    : line.discountPercent(),
                            "El descuento no puede ser negativo.");
            if (percent.signum() > 0) identity.require("descuentos");
            if (percent.compareTo(new BigDecimal("100")) > 0)
                throw new IllegalArgumentException("El descuento no puede superar el 100%.");
            BigDecimal gross = quantity.multiply(price).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineDiscount =
                    gross.multiply(percent).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = gross.subtract(lineDiscount);
            subtotal = subtotal.add(gross);
            discount = discount.add(lineDiscount);
            total = total.add(lineTotal);
            costTotal = costTotal.add(quantity.multiply(cost));
            lines.add(new PricedLine(line.articleId(), quantity, price, cost, percent, lineTotal));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        discount = discount.setScale(2, RoundingMode.HALF_UP);
        total = total.setScale(2, RoundingMode.HALF_UP);
        costTotal = costTotal.setScale(2, RoundingMode.HALF_UP);
        if (saleType.equals("CUENTA_CORRIENTE")) {
            Map<String, Object> customer =
                    jdbc.queryForMap(
                            "SELECT saldo_cuenta_corriente,limite_credito FROM clientes WHERE id=? AND comercio_id=? FOR UPDATE",
                            input.customerId(),
                            identity.storeId());
            BigDecimal due =
                    new BigDecimal(customer.get("saldo_cuenta_corriente").toString()).add(total);
            if (due.compareTo(new BigDecimal(customer.get("limite_credito").toString())) > 0)
                throw new BusinessConflict(
                        "La venta supera el límite de crédito disponible del cliente.");
        }
        Long cashId = null;
        if (!payment.equals("CUENTA_CORRIENTE")) cashId = cashLedger.requireOpenCash(date);
        String number =
                (orderId == null ? "V-" : "VW-")
                        + date.toString().replace("-", "")
                        + "-"
                        + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long saleId =
                insert(
                        "INSERT INTO ventas (comercio_id,numero,fecha,fecha_hora,cliente_id,tipo_venta,medio_pago,tipo_comprobante,subtotal,descuento,total,costo_total,origen,pedido_tienda_id) VALUES (?,?,?,?,?,?,?,'INTERNO_NO_FISCAL',?,?,?,?,?,?)",
                        identity.storeId(),
                        number,
                        Date.valueOf(date),
                        Timestamp.valueOf(date.atTime(LocalTime.now(clock))),
                        input.customerId(),
                        saleType,
                        payment,
                        subtotal,
                        discount,
                        total,
                        costTotal,
                        orderId == null ? "LOCAL" : "WEB",
                        orderId);
        int row = 1;
        for (PricedLine line : lines) {
            jdbc.update(
                    "INSERT INTO venta_detalle (venta_id,renglon,articulo_id,cantidad,precio_unitario,costo_unitario,descuento_pct,total_linea) VALUES (?,?,?,?,?,?,?,?)",
                    saleId,
                    row++,
                    line.articleId(),
                    line.quantity(),
                    line.price(),
                    line.cost(),
                    line.discountPercent(),
                    line.lineTotal());
            jdbc.update(
                    "UPDATE articulos SET stock=stock-? WHERE id=? AND comercio_id=?",
                    line.quantity(),
                    line.articleId(),
                    identity.storeId());
            this.stock.addStockMovement(
                    line.articleId(),
                    "VENTA",
                    line.quantity().negate(),
                    number,
                    orderId == null ? "Salida por venta" : "Venta web · pedido #" + orderId);
        }
        if (saleType.equals("CUENTA_CORRIENTE"))
            jdbc.update(
                    "UPDATE clientes SET saldo_cuenta_corriente=saldo_cuenta_corriente+? WHERE id=? AND comercio_id=?",
                    total,
                    input.customerId(),
                    identity.storeId());
        else
            cashLedger.addCashMovement(
                    cashId,
                    "INGRESO",
                    "VENTA",
                    payment,
                    date,
                    total,
                    number,
                    orderId == null ? "Cobro de venta" : "Cobro de venta web · pedido #" + orderId);
        return Map.of(
                "id",
                saleId,
                "numero",
                number,
                "total",
                total,
                "tipo_comprobante",
                "INTERNO_NO_FISCAL");
    }

    /**
     * Revierte una venta confirmada, repone stock y contrapartidas de cobro o deuda y audita la
     * anulación sin borrar el comprobante.
     */
    @Transactional
    public Map<String, Object> cancelSale(long id) {
        Map<String, Object> sale =
                jdbc.queryForMap(
                        "SELECT id,numero,fecha,cliente_id,tipo_venta,medio_pago,total,estado FROM ventas WHERE id=? AND comercio_id=? FOR UPDATE",
                        id,
                        identity.storeId());
        String status = String.valueOf(sale.get("estado"));
        if (!"CONFIRMADA".equals(status))
            throw new BusinessConflict("Esta venta ya está cancelada o no se puede cancelar.");
        BigDecimal total = new BigDecimal(sale.get("total").toString());
        String paymentMethod = String.valueOf(sale.get("medio_pago"));
        boolean currentAccount = "CUENTA_CORRIENTE".equals(paymentMethod);
        if (currentAccount) {
            Map<String, Object> customer =
                    jdbc.queryForMap(
                            "SELECT saldo_cuenta_corriente FROM clientes WHERE id=? AND comercio_id=? FOR UPDATE",
                            sale.get("cliente_id"),
                            identity.storeId());
            BigDecimal balance = new BigDecimal(customer.get("saldo_cuenta_corriente").toString());
            if (balance.compareTo(total) < 0)
                throw new BusinessConflict(
                        "No se puede cancelar: la deuda de esta venta ya fue saldada total o parcialmente.");
            jdbc.update(
                    "UPDATE clientes SET saldo_cuenta_corriente=saldo_cuenta_corriente-? WHERE id=? AND comercio_id=?",
                    total,
                    sale.get("cliente_id"),
                    identity.storeId());
        }
        Long cashId = null;
        if (!currentAccount) {
            List<Long> originalCash =
                    jdbc.query(
                            "SELECT id FROM movimientos_caja WHERE comercio_id=? AND categoria='VENTA' AND tipo='INGRESO' AND referencia=? AND medio_pago=? ORDER BY id LIMIT 1 FOR UPDATE",
                            (rs, row) -> rs.getLong(1),
                            identity.storeId(),
                            sale.get("numero"),
                            paymentMethod);
            if (originalCash.isEmpty())
                throw new BusinessConflict(
                        "No se encontró el movimiento de caja original de esta venta.");
            LocalDate today = LocalDate.now(clock);
            cashId = cashLedger.requireOpenCash(today);
        }
        List<Map<String, Object>> lines =
                jdbc.queryForList(
                        "SELECT articulo_id,cantidad FROM venta_detalle WHERE venta_id=? ORDER BY renglon",
                        id);
        if (lines.isEmpty())
            throw new BusinessConflict("La venta no tiene artículos para devolver al stock.");
        LocalDate reversalDate = LocalDate.now(clock);
        for (Map<String, Object> line : lines) {
            long articleId = ((Number) line.get("articulo_id")).longValue();
            BigDecimal quantity = new BigDecimal(line.get("cantidad").toString());
            Map<String, Object> article =
                    jdbc.queryForMap(
                            "SELECT id FROM articulos WHERE id=? AND comercio_id=? FOR UPDATE",
                            articleId,
                            identity.storeId());
            jdbc.update(
                    "UPDATE articulos SET stock=stock+? WHERE id=? AND comercio_id=?",
                    quantity,
                    articleId,
                    identity.storeId());
            this.stock.addStockMovement(
                    articleId,
                    "ANULACION_VENTA",
                    quantity,
                    "ANUL-" + sale.get("numero"),
                    "Devolución de stock por cancelación de venta");
        }
        if (!currentAccount)
            cashLedger.addCashMovement(
                    cashId,
                    "EGRESO",
                    "VENTA_ANULADA",
                    paymentMethod,
                    reversalDate,
                    total,
                    "ANUL-" + sale.get("numero"),
                    "Devolución del cobro por cancelación de venta " + sale.get("numero"));
        jdbc.update(
                "UPDATE ventas SET estado='CANCELADA' WHERE id=? AND comercio_id=?",
                id,
                identity.storeId());
        jdbc.update(
                "UPDATE pedidos_tienda SET estado='CANCELADO' WHERE venta_id=? AND comercio_id=? AND estado='CONFIRMADO'",
                id,
                identity.storeId());
        return Map.of(
                "id",
                id,
                "numero",
                sale.get("numero"),
                "estado",
                "CANCELADA",
                "total_reintegrado",
                total,
                "articulos_devueltos",
                lines.size());
    }
}
