// Organiza las reglas de clientes y sus cuentas.
package ar.com.alon.customers;

import ar.com.alon.customers.dto.CustomerInput;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService extends StoreJdbcSupport {
    /** Recibe y conserva las dependencias necesarias para las reglas de clientes y crédito. */
    public CustomerService(
            JdbcTemplate jdbc, RequestIdentity identity, Clock clock, AuditLog audit) {
        super(jdbc, identity, clock, audit);
    }

    /**
     * Crea un cliente del comercio con código propio, datos de contacto y límite de crédito
     * validado.
     */
    @Transactional
    public Map<String, Object> createCustomer(CustomerInput input) {
        String dni = validateDni(input.dni(), 0);
        String name = required(input.name(), "El nombre del cliente es obligatorio.");
        String code = "CLI-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id =
                insert(
                        "INSERT INTO clientes (comercio_id,codigo,nombre,telefono,email,limite_credito,dni) VALUES (?,?,?,?,?,?,?)",
                        identity.storeId(),
                        code,
                        name,
                        value(input.phone()),
                        value(input.email()),
                        nonNegative(
                                input.creditLimit(), "El límite de crédito no puede ser negativo."),
                        dni);
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    /**
     * Bloquea y actualiza los datos y límite de crédito del cliente sin modificar su saldo de
     * cuenta corriente.
     */
    @Transactional
    public Map<String, Object> updateCustomer(long id, CustomerInput input) {
        String dni = validateDni(input.dni(), id);
        jdbc.queryForObject(
                "SELECT id FROM clientes WHERE id=? AND comercio_id=? FOR UPDATE",
                Long.class,
                id,
                identity.storeId());
        String name = required(input.name(), "El nombre del cliente es obligatorio.");
        jdbc.update(
                "UPDATE clientes SET nombre=?,telefono=?,email=?,limite_credito=?,dni=? WHERE id=? AND comercio_id=?",
                name,
                value(input.phone()),
                value(input.email()),
                nonNegative(input.creditLimit(), "El límite de crédito no puede ser negativo."),
                dni,
                id,
                identity.storeId());
        return Map.of("id", id, "nombre", name);
    }

    private String validateDni(String input, long excludeId) {
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
        String dni = input == null ? "" : input.trim();
        if (dni.isEmpty()) return null;
        if (!dni.matches("[0-9]{1,12}"))
            throw new IllegalArgumentException("El DNI debe contener solo números.");
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM clientes WHERE comercio_id=? AND dni=? AND id<>?",
                        Long.class,
                        identity.storeId(),
                        dni,
                        excludeId)
                > 0)
            throw new ar.com.alon.shared.web.BusinessConflict(
                    "Ya existe un cliente con ese DNI. Usá el cliente existente.");
        return dni;
    }

    /** Cambia el estado del cliente del comercio y conserva sus ventas y cuenta corriente. */
    @Transactional
    public Map<String, Object> setCustomerActive(long id, boolean active) {
        int changed =
                jdbc.update(
                        "UPDATE clientes SET estado=? WHERE id=? AND comercio_id=?",
                        active ? "ACTIVO" : "INACTIVO",
                        id,
                        identity.storeId());
        if (changed == 0) throw new IllegalArgumentException("No se encontró el cliente.");
        return Map.of("id", id, "estado", active ? "ACTIVO" : "INACTIVO");
    }
}
