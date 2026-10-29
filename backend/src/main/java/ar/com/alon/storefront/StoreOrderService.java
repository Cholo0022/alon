package ar.com.alon.storefront;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import ar.com.alon.storefront.dto.StoreOrderInput;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoreOrderService extends StoreJdbcSupport {
    private final ar.com.alon.sales.SalesService sales;
    private final ar.com.alon.customers.CustomerService customers;

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

    public Map<String, Object> detail(long id) {
        var order =
                jdbc.queryForMap(
                        "SELECT id,nombre_cliente,contacto,dni,domicilio,detalle,total_estimado,estado,creado_en,confirmado_en,medio_pago,venta_id FROM pedidos_tienda WHERE comercio_id=? AND id=?",
                        identity.storeId(),
                        id);
        var lines =
                jdbc.queryForList(
                        "SELECT d.id,d.articulo_id,d.nombre_articulo,d.unidad,d.cantidad,d.precio_unitario,d.incluido,a.stock,a.estado AS estado_articulo FROM pedido_tienda_detalle d JOIN articulos a ON a.id=d.articulo_id AND a.comercio_id=d.comercio_id WHERE d.comercio_id=? AND d.pedido_id=? ORDER BY d.articulo_id",
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
    public Map<String, Object> approve(long id, String paymentMethod) {
        identity.require("tienda");
        var before = lockPending(id);
        String payment = required(paymentMethod, "Elegí el medio de pago antes de confirmar.");
        if (!java.util.Set.of("EFECTIVO", "TRANSFERENCIA", "MERCADO_PAGO").contains(payment))
            throw new IllegalArgumentException("El medio de pago no es válido.");
        new ar.com.alon.cash.CashLedger(jdbc, identity, clock)
                .requireOpenCash(LocalDate.now(clock));
        var lines =
                jdbc.queryForList(
                        "SELECT articulo_id,cantidad,nombre_articulo,precio_unitario FROM pedido_tienda_detalle WHERE comercio_id=? AND pedido_id=? AND incluido=TRUE ORDER BY articulo_id",
                        identity.storeId(),
                        id);
        if (lines.isEmpty())
            throw new IllegalArgumentException(
                    "El pedido debe incluir al menos un artículo. Los pedidos antiguos sin detalle no se pueden confirmar aquí.");
        var order =
                jdbc.queryForMap(
                        "SELECT comprador_id,nombre_cliente,contacto FROM pedidos_tienda WHERE comercio_id=? AND id=?",
                        identity.storeId(),
                        id);
        if (!(order.get("comprador_id") instanceof Number buyerId))
            throw new IllegalArgumentException("El pedido no tiene un comprador vinculado.");
        var buyer =
                jdbc.queryForMap(
                        "SELECT cliente_id FROM compradores_tienda WHERE comercio_id=? AND id=? FOR UPDATE",
                        identity.storeId(),
                        buyerId.longValue());
        long customerId;
        if (buyer.get("cliente_id") instanceof Number existing) customerId = existing.longValue();
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
                                                            BigDecimal.ZERO))
                                            .get("id"))
                            .longValue();
            jdbc.update(
                    "UPDATE compradores_tienda SET cliente_id=? WHERE comercio_id=? AND id=?",
                    customerId,
                    identity.storeId(),
                    buyerId.longValue());
        }
        var saleLines = new java.util.ArrayList<ar.com.alon.sales.dto.SaleLine>();
        var prices = new java.util.HashMap<Long, BigDecimal>();
        for (var line : lines) {
            long article = ((Number) line.get("articulo_id")).longValue();
            saleLines.add(
                    new ar.com.alon.sales.dto.SaleLine(
                            article, (BigDecimal) line.get("cantidad"), BigDecimal.ZERO));
            prices.put(article, (BigDecimal) line.get("precio_unitario"));
        }
        var sale =
                sales.createWebSale(
                        new ar.com.alon.sales.dto.SaleInput(
                                customerId, LocalDate.now(clock), "CONTADO", payment, saleLines),
                        id,
                        prices);
        recalculate(id);
        jdbc.update(
                "UPDATE pedidos_tienda SET estado='CONFIRMADO',confirmado_por=?,confirmado_en=?,medio_pago=?,venta_id=? WHERE comercio_id=? AND id=?",
                identity.userId(),
                Timestamp.valueOf(LocalDateTime.now(clock)),
                payment,
                sale.get("id"),
                identity.storeId(),
                id);
        audit.record("pedido_tienda", id, "CONFIRMAR", before, detail(id));
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

    @Transactional
    public Map<String, Object> cancel(long id) {
        identity.require("tienda");
        var before = lockPending(id);
        jdbc.update(
                "UPDATE pedidos_tienda SET estado='CANCELADO' WHERE comercio_id=? AND id=?",
                identity.storeId(),
                id);
        audit.record("pedido_tienda", id, "CANCELAR", before, detail(id));
        return Map.of("id", id, "estado", "CANCELADO");
    }

    private Map<String, Object> lockPending(long id) {
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        var order =
                jdbc.queryForMap(
                        "SELECT id,estado FROM pedidos_tienda WHERE comercio_id=? AND id=? FOR UPDATE",
                        identity.storeId(),
                        id);
        if (!"PENDIENTE_CONFIRMACION".equals(order.get("estado")))
            throw new ar.com.alon.shared.web.BusinessConflict(
                    "El pedido ya fue confirmado o cancelado.");
        return order;
    }

    private void recalculate(long id) {
        jdbc.update(
                "UPDATE pedidos_tienda SET total_estimado=(SELECT COALESCE(SUM(ROUND(cantidad*precio_unitario,2)),0) FROM pedido_tienda_detalle WHERE comercio_id=? AND pedido_id=? AND incluido=TRUE) WHERE comercio_id=? AND id=?",
                identity.storeId(),
                id,
                identity.storeId(),
                id);
    }
}
