package ar.com.alon.accounting;

import ar.com.alon.security.*;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.persistence.StoreJdbcSupport;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FiscalService extends StoreJdbcSupport {
    public FiscalService(JdbcTemplate jdbc, RequestIdentity identity, Clock clock, AuditLog audit) {
        super(jdbc, identity, clock, audit);
    }

    private void authorize(String clase) {
        String permission =
                switch (clase) {
                    case "COMPRA" -> "compras";
                    case "VENTA" -> "ventas";
                    case "GASTO" -> "caja";
                    default -> "contabilidad";
                };
        if (!identity.permits("contabilidad") && !identity.permits(permission))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No tenés permiso para cargar estos datos fiscales.");
    }

    public Map<String, Object> document(long id) {
        var row =
                jdbc.queryForMap(
                        "SELECT * FROM comprobantes_contables WHERE comercio_id=? AND id=?",
                        identity.storeId(),
                        id);
        authorize(String.valueOf(row.get("clase")));
        row.put(
                "alicuotas",
                jdbc.queryForList(
                        "SELECT alicuota,neto,iva FROM comprobantes_contables_iva WHERE comercio_id=? AND comprobante_id=? ORDER BY alicuota",
                        identity.storeId(),
                        id));
        return row;
    }

    @Transactional
    public Map<String, Object> save(Long id, FiscalInput input) {
        authorize(input.clase());
        if (input.controlado() && !identity.permits("contabilidad"))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "El permiso Contabilidad es necesario para marcar datos fiscales como controlados.");
        input.validate();
        AccountingPeriods.check(jdbc, identity.storeId(), AccountingPeriods.month(input.periodo()));
        var config =
                jdbc.queryForMap(
                        "SELECT carga_impuestos,cuit_contable,condicion_iva FROM comercios WHERE id=?",
                        identity.storeId());
        if (!Boolean.TRUE.equals(config.get("carga_impuestos"))
                && !(config.get("carga_impuestos") instanceof Number n && n.intValue() != 0))
            throw new IllegalArgumentException(
                    "El modo de carga con impuestos está desactivado. El gerente puede activarlo desde Contabilidad.");
        if (!"RESPONSABLE_INSCRIPTO".equals(config.get("condicion_iva"))
                && input.creditoComputable().signum() != 0)
            throw new IllegalArgumentException(
                    "La condición de IVA del negocio no permite computar ese crédito fiscal.");
        String cuit = String.valueOf(config.get("cuit_contable"));
        if (!FiscalInput.validCuit(cuit))
            throw new IllegalArgumentException(
                    "Configurá la CUIT del negocio antes de cargar impuestos.");
        Map<String, Object> before = id == null ? Map.of() : document(id);
        if (id != null) {
            AccountingPeriods.check(
                    jdbc,
                    identity.storeId(),
                    Date.valueOf(before.get("periodo").toString()).toLocalDate());
        }
        if (input.operacionId() != null) {
            String table =
                    switch (input.clase()) {
                        case "VENTA" -> "ventas";
                        case "COMPRA" -> "compras";
                        case "GASTO" -> "gastos";
                        default -> throw new IllegalArgumentException("Clase inválida.");
                    };
            String amount = input.clase().equals("GASTO") ? "importe" : "total";
            var operation =
                    jdbc.queryForMap(
                            "SELECT "
                                    + amount
                                    + " importe FROM "
                                    + table
                                    + " WHERE comercio_id=? AND id=?",
                            identity.storeId(),
                            input.operacionId());
            if (input.tipo().equals("FACTURA") || input.tipo().equals("INTERNO_NO_FISCAL")) {
                if (new java.math.BigDecimal(operation.get("importe").toString())
                                .compareTo(input.total())
                        != 0)
                    throw new IllegalArgumentException(
                            "El total fiscal no coincide con el total de la operación vinculada.");
                Long duplicates =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM comprobantes_contables WHERE comercio_id=? AND clase=? AND operacion_id=? AND tipo IN ('FACTURA','INTERNO_NO_FISCAL') AND id<>?",
                                Long.class,
                                identity.storeId(),
                                input.clase(),
                                input.operacionId(),
                                id == null ? 0L : id);
                if (duplicates != null && duplicates > 0)
                    throw new IllegalArgumentException(
                            "La operación ya tiene un comprobante principal. Modificá ese registro.");
            }
        }
        String emitter =
                input.clase().equals("VENTA")
                        ? cuit
                        : (input.documentoTipo().equals("CUIT")
                                ? input.documento()
                                : "SIN_IDENTIFICAR");
        var args =
                new ArrayList<Object>(
                        List.of(
                                identity.storeId(),
                                input.clase(),
                                input.tipo(),
                                input.letra(),
                                input.puntoVenta(),
                                input.numero(),
                                emitter,
                                input.tercero().trim(),
                                input.documentoTipo(),
                                input.documento(),
                                Date.valueOf(input.fecha()),
                                Date.valueOf(AccountingPeriods.month(input.periodo()))));
        args.add(input.operacionId());
        args.addAll(
                List.of(
                        input.comprobanteAsociado(),
                        input.exento(),
                        input.noGravado(),
                        input.otrosTributos(),
                        input.percepcionIva(),
                        input.percepcionIibb(),
                        input.creditoComputable(),
                        input.total(),
                        input.controlado(),
                        input.autorizacionTipo(),
                        input.autorizacionCodigo(),
                        input.observaciones(),
                        identity.userId(),
                        Timestamp.valueOf(LocalDateTime.now(clock))));
        long saved;
        if (id == null)
            saved =
                    insert(
                            "INSERT INTO comprobantes_contables(comercio_id,clase,tipo,letra,punto_venta,numero,emisor_documento,tercero,documento_tipo,documento,fecha,periodo,operacion_id,comprobante_asociado,exento,no_gravado,otros_tributos,percepcion_iva,percepcion_iibb,credito_computable,total,controlado,autorizacion_tipo,autorizacion_codigo,observaciones,usuario_id,actualizado_en) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                            args.toArray());
        else {
            args.add(id);
            args.add(identity.storeId());
            jdbc.update(
                    "UPDATE comprobantes_contables SET comercio_id=?,clase=?,tipo=?,letra=?,punto_venta=?,numero=?,emisor_documento=?,tercero=?,documento_tipo=?,documento=?,fecha=?,periodo=?,operacion_id=?,comprobante_asociado=?,exento=?,no_gravado=?,otros_tributos=?,percepcion_iva=?,percepcion_iibb=?,credito_computable=?,total=?,controlado=?,autorizacion_tipo=?,autorizacion_codigo=?,observaciones=?,usuario_id=?,actualizado_en=? WHERE id=? AND comercio_id=?",
                    args.toArray());
            saved = id;
            jdbc.update(
                    "DELETE FROM comprobantes_contables_iva WHERE comercio_id=? AND comprobante_id=?",
                    identity.storeId(),
                    saved);
        }
        for (var line : input.alicuotas())
            jdbc.update(
                    "INSERT INTO comprobantes_contables_iva(comercio_id,comprobante_id,alicuota,neto,iva) VALUES(?,?,?,?,?)",
                    identity.storeId(),
                    saved,
                    line.alicuota(),
                    line.neto(),
                    line.iva());
        var result = document(saved);
        audit.record(
                "COMPROBANTE_CONTABLE", saved, id == null ? "ALTA" : "CORRECCION", before, result);
        return result;
    }
}
