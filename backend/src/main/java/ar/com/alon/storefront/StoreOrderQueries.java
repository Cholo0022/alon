// Consulta pedidos y compradores de la tienda web del comercio actual para mostrarlos en pantalla.
package ar.com.alon.storefront;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StoreOrderQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    /** Recibe y conserva las dependencias necesarias para el listado y contador de pedidos web. */
    public StoreOrderQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    /** Consulta pedidos web del comercio con estado, datos de cliente y total. */
    public List<Map<String, Object>> storeOrders() {
        return pages.query(
                "p.nombre_cliente,p.contacto,p.detalle",
                """
                SELECT p.id,p.nombre_cliente,p.contacto,p.detalle,p.total_estimado,p.estado,p.creado_en,p.medio_pago,p.venta_id,
                %s AS comentario_cliente
                FROM pedidos_tienda p WHERE p.comercio_id = ? ORDER BY p.creado_en DESC,p.id DESC
                """
                        .formatted(customerCommentSql("p")),
                identity.storeId());
    }

    /** Solo una venta confirmada de otro pedido acredita una compra web previa. */
    static String customerCommentSql(String alias) {
        return "CASE WHEN "
                + alias
                + ".comprador_id IS NULL THEN NULL WHEN EXISTS (SELECT 1 FROM pedidos_tienda anterior JOIN ventas v ON v.id=anterior.venta_id AND v.comercio_id=anterior.comercio_id WHERE anterior.comercio_id="
                + alias
                + ".comercio_id AND anterior.comprador_id="
                + alias
                + ".comprador_id AND anterior.id<>"
                + alias
                + ".id AND anterior.estado='CONFIRMADO' AND v.estado='CONFIRMADA') THEN 'Este cliente ya ha comprado en la web' ELSE 'Es cliente web nuevo' END";
    }

    /**
     * Consulta cantidad total de pedidos pendientes de confirmación, sin limitarla al tamaño de una
     * página.
     */
    public long pendingCount() {
        Long count =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pedidos_tienda WHERE comercio_id=? AND estado='PENDIENTE_CONFIRMACION'",
                        Long.class,
                        identity.storeId());
        return count == null ? 0 : count;
    }
}
