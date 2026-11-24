// Permite revisar pedidos web y convertirlos en ventas cuando se confirman.
package ar.com.alon.storefront;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import ar.com.alon.shared.validation.DecimalRules;
import ar.com.alon.shared.web.BusinessConflict;
import ar.com.alon.stock.StockRepository;
import ar.com.alon.storefront.dto.StoreOrderInput;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreOrderService extends StoreJdbcSupport {
    private final ar.com.alon.sales.SalesService sales;
    private final ar.com.alon.customers.CustomerService customers;

    /**
     * Recibe y conserva las dependencias necesarias para las reglas de revisión y conversión de
     * pedidos en ventas.
     */
    public StoreOrderService(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            Clock clock,
            AuditLog audit,
            ar.com.alon.sales.SalesService sales,
            ar.com.alon.customers.CustomerService customers) {
        super(jdbc, identity, clock, audit);
        this.sales = sales;
        this.customers = customers;
    }

    /**
     * Guarda un pedido ingresado desde gestión con datos de cliente y total estimado, pendiente de
     * confirmación.
     */
    @Transactional
    public Map<String, Object> createStoreOrder(StoreOrderInput input) {
        long id =
                insert(
                        "INSERT INTO pedidos_tienda (comercio_id,nombre_cliente,contacto,detalle,total_estimado,estado) VALUES (?,?,?,?,?,'PENDIENTE_CONFIRMACION')",
                        identity.storeId(),
                        required(input.customerName(), "El nombre del cliente es obligatorio."),
                        required(input.contact(), "El contacto es obligatorio."),
                        required(input.details(), "El detalle del pedido es obligatorio."),
                        nonNegative(
                                input.estimatedTotal(),
                                "El total estimado no puede ser negativo."));
        return Map.of("id", id, "estado", "PENDIENTE_CONFIRMACION");
    }

    /**
     * Devuelve cabecera y renglones del pedido del comercio, junto con disponibilidad y estado de
     * caja para revisarlo.
     */
    public Map<String, Object> detail(long id) {
        var order =
                jdbc.queryForMap(
                        "SELECT p.id,p.nombre_cliente,p.contacto,p.dni,p.domicilio,p.provincia,p.ciudad,p.detalle,p.total_estimado,p.estado,p.creado_en,p.confirmado_en,p.medio_pago,p.venta_id,"
                                + StoreOrderQueries.customerCommentSql("p")
                                + " AS comentario_cliente FROM pedidos_tienda p WHERE p.comercio_id=? AND p.id=?",
                        identity.storeId(),
                        id);
        var lines =
                jdbc.queryForList(
                        "SELECT d.id,d.articulo_id,d.nombre_articulo,d.unidad,d.cantidad,d.precio_unitario,d.incluido,a.stock,a.unidad AS unidad_actual,a.tipo_producto,a.estado AS estado_articulo FROM pedido_tienda_detalle d JOIN articulos a ON a.id=d.articulo_id AND a.comercio_id=d.comercio_id WHERE d.comercio_id=? AND d.pedido_id=? ORDER BY d.articulo_id",
                        identity.storeId(),
                        id);
        order.put("lines", lines);
        order.put(
                "cash_open",
                jdbc.queryForObject(
                                "SELECT COUNT(*) FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA'",
                                Integer.class,
                                identity.storeId(),
                                java.sql.Date.valueOf(LocalDate.now(clock)))
                        > 0);
        return order;
    }

    /**
     * Bloquea un pedido pendiente, excluye el renglón indicado, recalcula su total y audita el
     * cambio.
     */
    @Transactional
    public Map<String, Object> exclude(long id, long lineId) {
        identity.require("tienda");
        var before = lockPending(id);
        int changed =
                jdbc.update(
                        "UPDATE pedido_tienda_detalle SET incluido=FALSE WHERE comercio_id=? AND pedido_id=? AND id=? AND incluido=TRUE",
                        identity.storeId(),
                        id,
                        lineId);
        if (changed == 0)
            throw new IllegalArgumentException(
                    "El artículo ya fue quitado o no pertenece al pedido.");
        recalculate(id);
        audit.record("pedido_tienda", id, "QUITAR_ARTICULO", before, detail(id));
        return Map.of("id", id, "ok", true);
    }

    @Transactional
    public Map<String, Object> updateQuantity(long id, long lineId, BigDecimal quantity) {
        identity.require("tienda");
        lockPending(id);
        var before = detail(id);
        var line =
                jdbc.queryForMap(
                        "SELECT a.tipo_producto,a.unidad,d.cantidad FROM pedido_tienda_detalle d "
                                + "JOIN articulos a ON a.id=d.articulo_id AND a.comercio_id=d.comercio_id "
                                + "WHERE d.comercio_id=? AND d.pedido_id=? AND d.id=? AND d.incluido=TRUE",
                        identity.storeId(),
                        id,
                        lineId);
        BigDecimal normalized =
                DecimalRules.quantity(
                        quantity,
                        true,
                        "Ingresá una cantidad mayor que cero dentro del límite permitido.");
        DecimalRules.requireWholeUnits(line.get("unidad"), normalized);
        if (java.util.Set.of("INDUMENTARIA", "CALZADO").contains(line.get("tipo_producto"))
                && normalized.stripTrailingZeros().scale() > 0)
            throw new IllegalArgumentException("Ingresá una cantidad entera de prendas o pares.");
        if (normalized.compareTo((BigDecimal) line.get("cantidad")) == 0) return before;
        jdbc.update(
                "UPDATE pedido_tienda_detalle SET cantidad=? WHERE comercio_id=? AND pedido_id=? AND id=?",
                normalized,
                identity.storeId(),
                id,
                lineId);
        recalculate(id);
        var after = detail(id);
        audit.record("pedido_tienda", id, "MODIFICAR_CANTIDAD", before, after);
        return after;
    }

    /** Descuenta stock y conserva el costo de los artículos al confirmar. */
    @Transactional
    public Map<String, Object> confirm(long id) {
        identity.require("tienda");
        var before = lockPending(id);
        var lines = includedLines(id);
        requireIncludedLines(lines);
        var stock = new StockRepository(jdbc, identity, clock);
        for (var line : lines) {
            long articleId = ((Number) line.get("articulo_id")).longValue();
            var article = stock.lockArticle(articleId);
            var quantity = (BigDecimal) line.get("cantidad");
            DecimalRules.requireWholeUnits(article.get("unidad"), quantity);
            if (((BigDecimal) article.get("stock")).compareTo(quantity) < 0)
                throw new BusinessConflict(
                        "Stock insuficiente para " + article.get("nombre") + ".");
            jdbc.update(
                    "UPDATE articulos SET stock=stock-? WHERE comercio_id=? AND id=?",
                    quantity,
                    identity.storeId(),
                    articleId);
            stock.addStockMovement(
                    articleId,
                    "PEDIDO_WEB",
                    quantity.negate(),
                    "PEDIDO-" + id,
                    "Salida por confirmación de pedido web");
            jdbc.update(
                    "UPDATE pedido_tienda_detalle SET costo_confirmado=? WHERE comercio_id=? AND pedido_id=? AND id=?",
                    article.get("costo"),
                    identity.storeId(),
                    id,
                    line.get("id"));
        }
        recalculate(id);
        jdbc.update(
                "UPDATE pedidos_tienda SET estado='PENDIENTE_PAGO',confirmado_por=?,confirmado_en=? WHERE comercio_id=? AND id=?",
                identity.userId(),
                Timestamp.valueOf(LocalDateTime.now(clock)),
                identity.storeId(),
                id);
        audit.record("pedido_tienda", id, "CONFIRMAR", before, detail(id));
        return Map.of("id", id, "estado", "PENDIENTE_PAGO");
    }

    /** Registra la venta y su cobro en la caja de la fecha actual. */
    @Transactional
    public Map<String, Object> pay(long id, String paymentMethod) {
        identity.require("tienda");
        var before = lockOrder(id);
        if (!"PENDIENTE_PAGO".equals(before.get("estado")))
            throw new BusinessConflict("El pedido no está pendiente de pago.");
        return settle(id, paymentMethod, before, true);
    }

    /**
     * Exige pedido pendiente, medio válido y caja abierta; crea la venta vinculada con los
     * renglones incluidos y conserva sus precios acordados.
     */
    @Transactional
    public Map<String, Object> approve(long id, String paymentMethod) {
        identity.require("tienda");
        var before = lockPending(id);
        return settle(id, paymentMethod, before, false);
    }

    private Map<String, Object> settle(
            long id, String paymentMethod, Map<String, Object> before, boolean stockReserved) {
        String payment = required(paymentMethod, "Elegí el medio de pago antes de confirmar.");
        if (!java.util.Set.of("EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO").contains(payment))
            throw new IllegalArgumentException("El medio de pago no es válido.");
        new ar.com.alon.cash.CashLedger(jdbc, identity, clock)
                .requireOpenCash(LocalDate.now(clock));
        var lines = includedLines(id);
        requireIncludedLines(lines);
        var order =
                jdbc.queryForMap(
                        "SELECT comprador_id,nombre_cliente,contacto FROM pedidos_tienda WHERE comercio_id=? AND id=?",
                        identity.storeId(),
                        id);
        if (!(order.get("comprador_id") instanceof Number buyerId))
            throw new IllegalArgumentException("El pedido no tiene un comprador vinculado.");
        var buyer =
                jdbc.queryForMap(
                        "SELECT cliente_id,dni FROM compradores_tienda WHERE comercio_id=? AND id=? FOR UPDATE",
                        identity.storeId(),
                        buyerId.longValue());
        long customerId;
        var matches =
                jdbc.queryForList(
                        "SELECT id FROM clientes WHERE comercio_id=? AND dni=? ORDER BY id FOR UPDATE",
                        identity.storeId(),
                        buyer.get("dni"));
        if (matches.size() > 1)
            throw new BusinessConflict(
                    "Hay varios clientes con ese DNI. Revisá los duplicados antes de confirmar el pedido.");
        if (!matches.isEmpty()) customerId = ((Number) matches.getFirst().get("id")).longValue();
        else if (buyer.get("cliente_id") instanceof Number existing)
            customerId = existing.longValue();
        else {
            customerId =
                    ((Number)
                                    customers
                                            .createCustomer(
                                                    new ar.com.alon.customers.dto.CustomerInput(
                                                            String.valueOf(
                                                                    order.get("nombre_cliente")),
                                                            String.valueOf(order.get("contacto")),
                                                            "",
                                                            BigDecimal.ZERO,
                                                            String.valueOf(buyer.get("dni"))))
                                            .get("id"))
                            .longValue();
        }
        jdbc.update(
                "UPDATE compradores_tienda SET cliente_id=? WHERE comercio_id=? AND id=?",
                customerId,
                identity.storeId(),
                buyerId.longValue());
        var saleLines = new java.util.ArrayList<ar.com.alon.sales.dto.SaleLine>();
        var prices = new java.util.HashMap<Long, BigDecimal>();
        var costs = new java.util.HashMap<Long, BigDecimal>();
        for (var line : lines) {
            long article = ((Number) line.get("articulo_id")).longValue();
            saleLines.add(
                    new ar.com.alon.sales.dto.SaleLine(
                            article, (BigDecimal) line.get("cantidad"), BigDecimal.ZERO));
            prices.put(article, (BigDecimal) line.get("precio_unitario"));
            if (stockReserved) costs.put(article, (BigDecimal) line.get("costo_confirmado"));
        }
        var sale =
                sales.createWebSale(
                        new ar.com.alon.sales.dto.SaleInput(
                                customerId, LocalDate.now(clock), "CONTADO", payment, saleLines),
                        id,
                        prices,
                        costs);
        recalculate(id);
        jdbc.update(
                "UPDATE pedidos_tienda SET estado='CONFIRMADO',confirmado_por=COALESCE(confirmado_por,?),confirmado_en=COALESCE(confirmado_en,?),medio_pago=?,venta_id=? WHERE comercio_id=? AND id=?",
                identity.userId(),
                Timestamp.valueOf(LocalDateTime.now(clock)),
                payment,
                sale.get("id"),
                identity.storeId(),
                id);
        audit.record(
                "pedido_tienda", id, stockReserved ? "COBRAR" : "CONFIRMAR", before, detail(id));
        return Map.of(
                "id",
                id,
                "estado",
                "CONFIRMADO",
                "venta_id",
                sale.get("id"),
                "numero_venta",
                sale.get("numero"));
    }

    private List<Map<String, Object>> includedLines(long id) {
        return jdbc.queryForList(
                "SELECT id,articulo_id,cantidad,precio_unitario,costo_confirmado FROM pedido_tienda_detalle WHERE comercio_id=? AND pedido_id=? AND incluido=TRUE ORDER BY articulo_id,id",
                identity.storeId(),
                id);
    }

    private void requireIncludedLines(List<Map<String, Object>> lines) {
        if (lines.isEmpty())
            throw new IllegalArgumentException(
                    "El pedido debe incluir al menos un artículo. Los pedidos antiguos sin detalle no se pueden confirmar aquí.");
    }

    /** Rechaza el pedido impago y repone el stock si ya se había confirmado. */
    @Transactional
    public Map<String, Object> cancel(long id) {
        identity.require("tienda");
        var before = lockOrder(id);
        if ("PENDIENTE_PAGO".equals(before.get("estado"))) {
            var stock = new StockRepository(jdbc, identity, clock);
            for (var line : includedLines(id)) {
                long articleId = ((Number) line.get("articulo_id")).longValue();
                var quantity = (BigDecimal) line.get("cantidad");
                jdbc.update(
                        "UPDATE articulos SET stock=stock+? WHERE comercio_id=? AND id=?",
                        quantity,
                        identity.storeId(),
                        articleId);
                stock.addStockMovement(
                        articleId,
                        "ANULACION_PEDIDO_WEB",
                        quantity,
                        "PEDIDO-" + id,
                        "Devolución de stock por rechazo de pedido web");
            }
        } else {
            requirePending(before);
        }
        jdbc.update(
                "UPDATE pedidos_tienda SET estado='CANCELADO' WHERE comercio_id=? AND id=?",
                identity.storeId(),
                id);
        audit.record("pedido_tienda", id, "CANCELAR", before, detail(id));
        return Map.of("id", id, "estado", "CANCELADO");
    }

    /** Bloquea comercio y pedido y rechaza modificarlo si ya fue confirmado o cancelado. */
    private Map<String, Object> lockPending(long id) {
        var order = lockOrder(id);
        requirePending(order);
        return order;
    }

    private void requirePending(Map<String, Object> order) {
        if (!"PENDIENTE_CONFIRMACION".equals(order.get("estado")))
            throw new BusinessConflict("El pedido ya fue confirmado o cancelado.");
    }

    private Map<String, Object> lockOrder(long id) {
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        return jdbc.queryForMap(
                "SELECT id,estado FROM pedidos_tienda WHERE comercio_id=? AND id=? FOR UPDATE",
                identity.storeId(),
                id);
    }

    /**
     * Actualiza el total estimado del pedido sumando solo los renglones que permanecen incluidos.
     */
    private void recalculate(long id) {
        jdbc.update(
                "UPDATE pedidos_tienda SET total_estimado=(SELECT COALESCE(SUM(ROUND(cantidad*precio_unitario,2)),0) FROM pedido_tienda_detalle WHERE comercio_id=? AND pedido_id=? AND incluido=TRUE) WHERE comercio_id=? AND id=?",
                identity.storeId(),
                id,
                identity.storeId(),
                id);
    }
}
