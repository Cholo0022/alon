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
    public StoreOrderService(
            JdbcTemplate jdbc, RequestIdentity identity, Clock clock, AuditLog audit) {
        super(jdbc, identity, clock, audit);
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
}
