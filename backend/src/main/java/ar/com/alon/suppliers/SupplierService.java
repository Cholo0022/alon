package ar.com.alon.suppliers;

import ar.com.alon.cash.CashLedger;
import ar.com.alon.security.RequestIdentity;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import ar.com.alon.shared.web.BusinessConflict;
import ar.com.alon.suppliers.dto.SupplierInput;
import ar.com.alon.suppliers.dto.SupplierReceiptInput;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService extends StoreJdbcSupport {
    private final CashLedger cashLedger;

    public SupplierService(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            Clock clock,
            AuditLog audit,
            CashLedger cashLedger) {
        super(jdbc, identity, clock, audit);
        this.cashLedger = cashLedger;
    }

    @Transactional
    public Map<String, Object> createSupplier(SupplierInput input) {
        String name = required(input.name(), "El nombre del proveedor es obligatorio.");
        String code =
                "PROV-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long id =
                insert(
                        "INSERT INTO proveedores (comercio_id,codigo,nombre,cuit,especialidad,telefono,email) VALUES (?,?,?,?,?,?,?)",
                        identity.storeId(),
                        code,
                        name,
                        value(input.taxId()),
                        value(input.specialty()),
                        value(input.phone()),
                        value(input.email()));
        return Map.of("id", id, "codigo", code, "nombre", name);
    }

    @Transactional
    public Map<String, Object> updateSupplier(long id, SupplierInput input) {
        jdbc.queryForObject(
                "SELECT id FROM proveedores WHERE id=? AND comercio_id=? FOR UPDATE",
                Long.class,
                id,
                identity.storeId());
        String name = required(input.name(), "El nombre del proveedor es obligatorio.");
        jdbc.update(
                "UPDATE proveedores SET nombre=?,cuit=?,especialidad=?,telefono=?,email=? WHERE id=? AND comercio_id=?",
                name,
                value(input.taxId()),
                value(input.specialty()),
                value(input.phone()),
                value(input.email()),
                id,
                identity.storeId());
        return Map.of("id", id, "nombre", name);
    }

    @Transactional
    public Map<String, Object> setSupplierActive(long id, boolean active) {
        int changed =
                jdbc.update(
                        "UPDATE proveedores SET estado=? WHERE id=? AND comercio_id=?",
                        active ? "ACTIVO" : "INACTIVO",
                        id,
                        identity.storeId());
        if (changed == 0) throw new IllegalArgumentException("No se encontró el proveedor.");
        return Map.of("id", id, "estado", active ? "ACTIVO" : "INACTIVO");
    }

    @Transactional
    public Map<String, Object> createSupplierReceipt(SupplierReceiptInput input) {
        assertBelongs("proveedores", input.supplierId());
        LocalDate date = input.date() == null ? LocalDate.now(clock) : input.date();
        String method =
                enumValue(
                        input.paymentMethod(),
                        "EFECTIVO",
                        "EFECTIVO",
                        "TRANSFERENCIA",
                        "MERCADO_PAGO");
        BigDecimal amount =
                positive(input.amount(), "El importe del recibo debe ser mayor a cero.");
        Map<String, Object> supplier =
                jdbc.queryForMap(
                        "SELECT id,nombre,saldo_actual FROM proveedores WHERE id=? AND comercio_id=? FOR UPDATE",
                        input.supplierId(),
                        identity.storeId());
        BigDecimal supplierBalance = new BigDecimal(supplier.get("saldo_actual").toString());
        if (amount.compareTo(supplierBalance) > 0)
            throw new IllegalArgumentException(
                    "El importe supera el saldo pendiente del proveedor.");
        Long cashId = cashLedger.requireOpenCash(date);
        List<Map<String, Object>> invoices =
                jdbc.queryForList(
                        "SELECT id,numero_documento,total,pagado,saldo FROM compras WHERE comercio_id=? AND proveedor_id=? AND saldo>0 ORDER BY fecha,id FOR UPDATE",
                        identity.storeId(),
                        input.supplierId());
        String receipt =
                "REC-"
                        + date.toString().replace("-", "")
                        + "-"
                        + java.util
                                .UUID
                                .randomUUID()
                                .toString()
                                .substring(0, 8)
                                .toUpperCase(java.util.Locale.ROOT);
        BigDecimal remaining = amount;
        int applied = 0;
        for (Map<String, Object> invoice : invoices) {
            if (remaining.signum() == 0) break;
            BigDecimal due = new BigDecimal(invoice.get("saldo").toString());
            BigDecimal appliedAmount = remaining.min(due).setScale(2, RoundingMode.HALF_UP);
            long purchaseId = ((Number) invoice.get("id")).longValue();
            BigDecimal paidTotal =
                    new BigDecimal(invoice.get("pagado").toString()).add(appliedAmount);
            BigDecimal balance = due.subtract(appliedAmount);
            jdbc.update(
                    "UPDATE compras SET pagado=?,saldo=?,estado=? WHERE id=?",
                    paidTotal,
                    balance,
                    balance.signum() == 0 ? "PAGADA" : "PENDIENTE",
                    purchaseId);
            insert(
                    "INSERT INTO pagos_proveedores (comercio_id,compra_id,proveedor_id,caja_id,fecha,medio_pago,importe,numero_recibo) VALUES (?,?,?,?,?,?,?,?)",
                    identity.storeId(),
                    purchaseId,
                    input.supplierId(),
                    cashId,
                    Date.valueOf(date),
                    method,
                    appliedAmount,
                    receipt);
            remaining = remaining.subtract(appliedAmount);
            applied++;
        }
        if (remaining.signum() > 0)
            throw new BusinessConflict(
                    "No se encontró una factura pendiente para aplicar el pago completo.");
        jdbc.update(
                "UPDATE proveedores SET saldo_actual=saldo_actual-? WHERE id=? AND comercio_id=?",
                amount,
                input.supplierId(),
                identity.storeId());
        cashLedger.addCashMovement(
                cashId,
                "EGRESO",
                "PAGO_PROVEEDOR",
                method,
                date,
                amount,
                receipt,
                "Recibo de pago a " + supplier.get("nombre"));
        return Map.of(
                "recibo",
                receipt,
                "proveedor",
                supplier.get("nombre"),
                "proveedor_id",
                input.supplierId(),
                "fecha",
                date.toString(),
                "medio_pago",
                method,
                "importe",
                amount,
                "saldo_anterior",
                supplierBalance,
                "saldo_restante",
                supplierBalance.subtract(amount),
                "facturas_aplicadas",
                applied);
    }
}
