// Consulta proveedores y sus cuentas del comercio actual para mostrarlos en pantalla.
package ar.com.alon.suppliers;

import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.persistence.ReadPage;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SupplierQueries {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final ReadPage pages;

    /** Recibe y conserva las dependencias necesarias para los proveedores y sus buscadores. */
    public SupplierQueries(JdbcTemplate jdbc, RequestIdentity identity, Clock clock) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.pages = new ReadPage(jdbc);
    }

    /**
     * Consulta proveedores del comercio filtrados por estado, con identificación y saldo pendiente.
     */
    public List<Map<String, Object>> suppliers(boolean inactive) {
        return pages.query(
                "codigo,nombre,telefono,email",
                """
                SELECT id, codigo, nombre, cuit, especialidad, telefono, email, saldo_actual AS saldo_pendiente, estado
                FROM proveedores WHERE comercio_id = ? AND estado=? ORDER BY nombre,id
                """,
                identity.storeId(),
                inactive ? "INACTIVO" : "ACTIVO");
    }

    /** Consulta proveedores activos con código y nombre para los buscadores. */
    public List<Map<String, Object>> lookupSuppliers() {
        return pages.query(
                "codigo,nombre",
                "SELECT id,codigo,nombre FROM proveedores WHERE comercio_id=? AND estado='ACTIVO' ORDER BY nombre,id",
                identity.storeId());
    }
}
