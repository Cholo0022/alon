// Registra entradas y salidas de dinero en la caja abierta del comercio.
package ar.com.alon.cash;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.web.BusinessConflict;
import java.math.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CashLedger {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;

    /** Recibe y conserva las dependencias necesarias para el registro de movimientos en caja. */
    public CashLedger(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
    }

    /**
     * Busca y bloquea la caja abierta de la fecha dentro del comercio; devuelve su id o rechaza la
     * operación si no existe.
     */
    public long requireOpenCash(LocalDate date) {
        List<Long> ids =
                jdbc.query(
                        "SELECT id FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA' FOR UPDATE",
                        (rs, row) -> rs.getLong(1),
                        identity.storeId(),
                        Date.valueOf(date));
        if (ids.isEmpty())
            throw new BusinessConflict(
                    "Abrí la caja de esa fecha antes de registrar el movimiento.");
        return ids.getFirst();
    }

    /**
     * Registra un ingreso o egreso con categoría, medio, importe, referencia y hora del negocio en
     * la caja indicada.
     */
    public void addCashMovement(
            long cashId,
            String type,
            String category,
            String method,
            LocalDate date,
            BigDecimal amount,
            String reference,
            String description) {
        jdbc.update(
                "INSERT INTO movimientos_caja (comercio_id,caja_id,tipo,categoria,medio_pago,fecha_hora,importe,referencia,descripcion) VALUES (?,?,?,?,?,?,?,?,?)",
                identity.storeId(),
                cashId,
                type,
                category,
                method,
                Timestamp.valueOf(date.atTime(LocalTime.now(clock))),
                amount,
                reference,
                description);
    }
}
