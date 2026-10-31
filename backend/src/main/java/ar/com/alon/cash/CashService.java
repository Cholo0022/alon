// Abre y cierra cajas y controla los importes cobrados, pagados y registrados como gastos.
package ar.com.alon.cash;

import ar.com.alon.cash.dto.CashCloseInput;
import ar.com.alon.cash.dto.CashOpenInput;
import ar.com.alon.cash.dto.ExpenseInput;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import ar.com.alon.shared.web.BusinessConflict;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashService extends StoreJdbcSupport {
    private final CashLedger cashLedger;

    public CashService(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            Clock clock,
            AuditLog audit,
            CashLedger cashLedger) {
        super(jdbc, identity, clock, audit);
        this.cashLedger = cashLedger;
    }

    @Transactional
    public Map<String, Object> openCash(CashOpenInput input) {
        LocalDate date = input.date() == null ? LocalDate.now(clock) : input.date();
        BigDecimal opening =
                nonNegative(input.openingBalance(), "El saldo inicial no puede ser negativo.");
        Integer openCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA'",
                        Integer.class,
                        identity.storeId(),
                        Date.valueOf(date));
        if (openCount != null && openCount > 0)
            throw new BusinessConflict(
                    "Ya hay una caja abierta para esa fecha. Cerrala antes de volver a abrir.");
        Integer session =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(numero_sesion),0)+1 FROM cajas WHERE comercio_id=? AND fecha=?",
                        Integer.class,
                        identity.storeId(),
                        Date.valueOf(date));
        int sessionNumber = session == null ? 1 : session;
        long id =
                insert(
                        "INSERT INTO cajas (comercio_id,fecha,numero_sesion,abierto_en,saldo_inicial,estado) VALUES (?,?,?,?,?,'ABIERTA')",
                        identity.storeId(),
                        Date.valueOf(date),
                        sessionNumber,
                        java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(clock)),
                        opening);
        return Map.of(
                "id",
                id,
                "fecha",
                date.toString(),
                "numero_sesion",
                sessionNumber,
                "estado",
                "ABIERTA");
    }

    @Transactional
    public Map<String, Object> addExpense(ExpenseInput input) {
        LocalDate date = input.date() == null ? LocalDate.now(clock) : input.date();
        BigDecimal amount = positive(input.amount(), "El importe del gasto debe ser mayor a cero.");
        String method =
                enumValue(
                        input.paymentMethod(),
                        "EFECTIVO",
                        "EFECTIVO",
                        "TRANSFERENCIA",
                        "MERCADO_PAGO");
        long cashId = cashLedger.requireOpenCash(date);
        String description = required(input.description(), "El detalle del gasto es obligatorio.");
        long id =
                insert(
                        "INSERT INTO gastos (comercio_id,caja_id,fecha,categoria,descripcion,medio_pago,importe) VALUES (?,?,?,?,?,?,?)",
                        identity.storeId(),
                        cashId,
                        Date.valueOf(date),
                        required(input.category(), "La categoría es obligatoria."),
                        description,
                        method,
                        amount);
        cashLedger.addCashMovement(
                cashId, "EGRESO", "GASTO", method, date, amount, "GASTO-" + id, description);
        return Map.of("id", id, "importe", amount);
    }

    public Map<String, Object> cashSummary(LocalDate date) {
        Map<String, Object> cash =
                jdbc.queryForMap(
                        "SELECT id,numero_sesion,saldo_inicial FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA'",
                        identity.storeId(),
                        Date.valueOf(date));
        long id = ((Number) cash.get("id")).longValue();
        BigDecimal opening = new BigDecimal(cash.get("saldo_inicial").toString());
        Map<String, Object> report =
                jdbc.queryForMap(
                        "SELECT "
                                + "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND categoria='VENTA' AND medio_pago='EFECTIVO' THEN importe WHEN tipo='EGRESO' AND categoria='VENTA_ANULADA' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END),0) ventas_efectivo,"
                                + "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND categoria='VENTA' AND medio_pago='MERCADO_PAGO' THEN importe WHEN tipo='EGRESO' AND categoria='VENTA_ANULADA' AND medio_pago='MERCADO_PAGO' THEN -importe ELSE 0 END),0) ventas_mercado_pago,"
                                + "COALESCE(SUM(CASE WHEN tipo='EGRESO' AND categoria<>'VENTA_ANULADA' AND medio_pago='EFECTIVO' THEN importe ELSE 0 END),0) egresos_efectivo,"
                                + "COALESCE(SUM(CASE WHEN tipo='EGRESO' AND categoria<>'VENTA_ANULADA' AND medio_pago='MERCADO_PAGO' THEN importe ELSE 0 END),0) egresos_mercado_pago,"
                                + "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND medio_pago='MERCADO_PAGO' THEN importe WHEN tipo='EGRESO' AND medio_pago='MERCADO_PAGO' THEN -importe ELSE 0 END),0) mercado_pago_esperado "
                                + "FROM movimientos_caja WHERE caja_id=?",
                        id);
        BigDecimal expected =
                opening.add(
                        jdbc.queryForObject(
                                "SELECT COALESCE(SUM(CASE WHEN tipo='INGRESO' AND medio_pago='EFECTIVO' THEN importe WHEN tipo='EGRESO' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END),0) FROM movimientos_caja WHERE caja_id=?",
                                BigDecimal.class,
                                id));
        Map<String, Object> result = new java.util.LinkedHashMap<>(report);
        result.put("id", id);
        result.put("fecha", date.toString());
        result.put("numero_sesion", cash.get("numero_sesion"));
        result.put("saldo_inicial", opening);
        result.put("efectivo_esperado", expected);
        return result;
    }

    @Transactional
    public Map<String, Object> closeCash(CashCloseInput input) {
        LocalDate date = input.date() == null ? LocalDate.now(clock) : input.date();
        Map<String, Object> cash =
                jdbc.queryForMap(
                        "SELECT id,numero_sesion,saldo_inicial FROM cajas WHERE comercio_id=? AND fecha=? AND estado='ABIERTA' FOR UPDATE",
                        identity.storeId(),
                        Date.valueOf(date));
        long id = ((Number) cash.get("id")).longValue();
        BigDecimal expected =
                new BigDecimal(cash.get("saldo_inicial").toString())
                        .add(
                                jdbc.queryForObject(
                                        "SELECT COALESCE(SUM(CASE WHEN tipo='INGRESO' AND medio_pago='EFECTIVO' THEN importe WHEN tipo='EGRESO' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END),0) FROM movimientos_caja WHERE caja_id=?",
                                        BigDecimal.class,
                                        id));
        BigDecimal counted =
                nonNegative(input.countedCash(), "El efectivo contado no puede ser negativo.");
        BigDecimal difference = counted.subtract(expected);
        jdbc.update(
                "UPDATE cajas SET cerrado_en=?,efectivo_esperado=?,efectivo_contado=?,diferencia=?,estado='CERRADA' WHERE id=?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(clock)),
                expected,
                counted,
                difference,
                id);
        Map<String, Object> report =
                jdbc.queryForMap(
                        "SELECT "
                                + "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND categoria='VENTA' AND medio_pago='EFECTIVO' THEN importe WHEN tipo='EGRESO' AND categoria='VENTA_ANULADA' AND medio_pago='EFECTIVO' THEN -importe ELSE 0 END),0) ventas_efectivo,"
                                + "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND categoria='VENTA' AND medio_pago='MERCADO_PAGO' THEN importe WHEN tipo='EGRESO' AND categoria='VENTA_ANULADA' AND medio_pago='MERCADO_PAGO' THEN -importe ELSE 0 END),0) ventas_mercado_pago,"
                                + "COALESCE(SUM(CASE WHEN tipo='EGRESO' AND categoria<>'VENTA_ANULADA' AND medio_pago='EFECTIVO' THEN importe ELSE 0 END),0) egresos_efectivo,"
                                + "COALESCE(SUM(CASE WHEN tipo='EGRESO' AND categoria<>'VENTA_ANULADA' AND medio_pago='MERCADO_PAGO' THEN importe ELSE 0 END),0) egresos_mercado_pago,"
                                + "COALESCE(SUM(CASE WHEN tipo='INGRESO' AND medio_pago='MERCADO_PAGO' THEN importe WHEN tipo='EGRESO' AND medio_pago='MERCADO_PAGO' THEN -importe ELSE 0 END),0) mercado_pago_esperado "
                                + "FROM movimientos_caja WHERE caja_id=?",
                        id);
        BigDecimal opening = new BigDecimal(cash.get("saldo_inicial").toString());
        Map<String, Object> result = new java.util.LinkedHashMap<>(report);
        result.put("id", id);
        result.put("fecha", date.toString());
        result.put("numero_sesion", cash.get("numero_sesion"));
        result.put("saldo_inicial", opening);
        result.put("efectivo_esperado", expected);
        result.put("efectivo_contado", counted);
        result.put("diferencia", difference);
        return result;
    }
}
