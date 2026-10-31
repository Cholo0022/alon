// Consulta clientes y sus cuentas del comercio actual para mostrarlos en pantalla.
package ar.com.alon.customers;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    /** Recibe y conserva las dependencias necesarias para los clientes y sus buscadores. */
    public CustomerQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    /** Consulta clientes del comercio filtrados por estado, con sus saldos y límites. */
    public List<Map<String, Object>> customers(boolean inactive) {
        return pages.query(
                "codigo,nombre,telefono,email",
                """
                SELECT id, codigo, nombre, telefono, email, saldo_cuenta_corriente, limite_credito, estado
                FROM clientes WHERE comercio_id = ? AND estado=? ORDER BY nombre,id
                """,
                identity.storeId(),
                inactive ? "INACTIVO" : "ACTIVO");
    }

    /** Consulta clientes activos con código y nombre para los buscadores. */
    public List<Map<String, Object>> lookupCustomers() {
        return pages.query(
                "codigo,nombre",
                "SELECT id,codigo,nombre FROM clientes WHERE comercio_id=? AND estado='ACTIVO' ORDER BY nombre,id",
                identity.storeId());
    }
}
