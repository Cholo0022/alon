package ar.com.alon.accounting;

import ar.com.alon.security.*;
import java.math.*;
import java.sql.Date;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AccountingReport {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final JsonMapper mapper;

    public AccountingReport(
            JdbcTemplate jdbc, RequestIdentity identity, Clock clock, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.mapper = mapper;
    }

    public Map<String, Object> settings() {
        return jdbc.queryForMap(
                "SELECT nombre,cuit_contable,condicion_iva,control_periodos,carga_impuestos FROM comercios WHERE id=?",
                identity.storeId());
    }

    static boolean flag(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() != 0;
    }

    public Map<String, Object> state(LocalDate month) {
        var rows =
                jdbc.queryForList(
                        "SELECT p.*,u.usuario AS responsable FROM periodos_contables p JOIN usuarios u ON u.id=p.actualizado_por WHERE p.comercio_id=? AND p.mes=?",
                        identity.storeId(),
                        Date.valueOf(month));
        return rows.isEmpty() ? Map.of("estado", "SIN_ABRIR") : rows.getFirst();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> closed(long id) {
        identity.require("contabilidad");
        var closure =
                jdbc.queryForMap(
                        "SELECT id,revision,informe,cerrado_en FROM cierres_contables WHERE comercio_id=? AND id=?",
                        identity.storeId(),
                        id);
        var result =
                mapper.readValue(
                        closure.get("informe").toString(),
                        new TypeReference<Map<String, Object>>() {});
        result.put("fuente", "CIERRE");
        result.put("estado_periodo", "CERRADO");
        result.put("revision", closure.get("revision"));
        result.put("cerrado_en", closure.get("cerrado_en"));
        result.put("cierre_id", id);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> report(String month) {
        identity.require("contabilidad");
        LocalDate start = AccountingPeriods.month(month);
        if ("CERRADO".equals(state(start).get("estado"))) {
            var rows =
                    jdbc.queryForList(
                            "SELECT id,revision,informe,cerrado_en FROM cierres_contables WHERE comercio_id=? AND mes=? ORDER BY revision DESC LIMIT 1",
                            identity.storeId(),
                            Date.valueOf(start));
            if (!rows.isEmpty()) {
                var closure = rows.getFirst();
                var result =
                        mapper.readValue(
                                closure.get("informe").toString(),
                                new TypeReference<Map<String, Object>>() {});
                result.put("fuente", "CIERRE");
                result.put("estado_periodo", "CERRADO");
                result.put("revision", closure.get("revision"));
                result.put("cerrado_en", closure.get("cerrado_en"));
                result.put("cierre_id", closure.get("id"));
                return result;
            }
        }
        return live(start);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> live(LocalDate start) {
        long store = identity.storeId();
        Date from = Date.valueOf(start), until = Date.valueOf(start.plusMonths(1));
        var sales =
                jdbc.queryForList(
                        "SELECT v.id,v.fecha,v.numero,c.nombre AS tercero,v.tipo_venta,v.medio_pago,v.tipo_comprobante,v.total,v.costo_total,v.descuento,v.estado,(SELECT f.id FROM comprobantes_contables f WHERE f.comercio_id=v.comercio_id AND f.clase='VENTA' AND f.operacion_id=v.id AND f.tipo='FACTURA' LIMIT 1) AS fiscal_id FROM ventas v JOIN clientes c ON c.id=v.cliente_id AND c.comercio_id=v.comercio_id WHERE v.comercio_id=? AND v.fecha>=? AND v.fecha<? ORDER BY v.fecha,v.id",
                        store,
                        from,
                        until);
        var purchases =
                jdbc.queryForList(
                        "SELECT c.id,c.fecha,c.numero_documento,p.nombre AS tercero,p.cuit,c.tipo_documento,c.total,c.condicion_pago,c.medio_pago,(SELECT f.id FROM comprobantes_contables f WHERE f.comercio_id=c.comercio_id AND f.clase='COMPRA' AND f.operacion_id=c.id AND f.tipo='FACTURA' LIMIT 1) AS fiscal_id FROM compras c JOIN proveedores p ON p.id=c.proveedor_id AND p.comercio_id=c.comercio_id WHERE c.comercio_id=? AND c.fecha>=? AND c.fecha<? ORDER BY c.fecha,c.id",
                        store,
                        from,
                        until);
        var expenses =
                jdbc.queryForList(
                        "SELECT g.id,g.fecha,g.categoria,g.descripcion,g.medio_pago,g.importe,(SELECT f.id FROM comprobantes_contables f WHERE f.comercio_id=g.comercio_id AND f.clase='GASTO' AND f.operacion_id=g.id AND f.tipo IN ('FACTURA','INTERNO_NO_FISCAL') LIMIT 1) AS fiscal_id FROM gastos g WHERE g.comercio_id=? AND g.fecha>=? AND g.fecha<? ORDER BY g.fecha,g.id",
                        store,
                        from,
                        until);
        var cash =
                jdbc.queryForList(
                        "SELECT id,fecha_hora,tipo,categoria,medio_pago,referencia,descripcion,importe FROM movimientos_caja WHERE comercio_id=? AND fecha_hora>=? AND fecha_hora<? ORDER BY fecha_hora,id",
                        store,
                        from,
                        until);
        var documents =
                jdbc.queryForList(
                        "SELECT f.*,COALESCE(i.neto,0) AS neto_gravado,COALESCE(i.iva,0) AS iva,CASE WHEN f.tipo='NOTA_CREDITO' THEN -1 ELSE 1 END AS signo FROM comprobantes_contables f LEFT JOIN (SELECT comercio_id,comprobante_id,SUM(neto) neto,SUM(iva) iva FROM comprobantes_contables_iva GROUP BY comercio_id,comprobante_id) i ON i.comercio_id=f.comercio_id AND i.comprobante_id=f.id WHERE f.comercio_id=? AND f.periodo=? ORDER BY f.fecha,f.id",
                        store,
                        from);
        var rates =
                jdbc.queryForList(
                        "SELECT f.clase,f.tipo,f.letra,f.punto_venta,f.numero,f.tercero,i.alicuota,i.neto,i.iva,CASE WHEN f.tipo='NOTA_CREDITO' THEN -1 ELSE 1 END AS signo,f.controlado FROM comprobantes_contables_iva i JOIN comprobantes_contables f ON f.id=i.comprobante_id AND f.comercio_id=i.comercio_id WHERE f.comercio_id=? AND f.periodo=? ORDER BY f.fecha,f.id,i.alicuota",
                        store,
                        from);
        for (var document : documents) {
            BigDecimal sign = amount(document.get("signo"));
            document.put("total_signado", amount(document.get("total")).multiply(sign));
            document.put("iva_signado", amount(document.get("iva")).multiply(sign));
            document.put(
                    "credito_computable_signado",
                    amount(document.get("credito_computable")).multiply(sign));
        }
        for (var line : rates) {
            BigDecimal sign = amount(line.get("signo"));
            line.put("neto_signado", amount(line.get("neto")).multiply(sign));
            line.put("iva_signado", amount(line.get("iva")).multiply(sign));
        }
        var confirmed = sales.stream().filter(s -> s.get("estado").equals("CONFIRMADA")).toList();
        var controlled = documents.stream().filter(d -> flag(d.get("controlado"))).toList();
        BigDecimal revenues = sum(confirmed, "total"),
                costs = sum(confirmed, "costo_total"),
                spending = sum(expenses, "importe");
        BigDecimal debit =
                signed(
                        controlled.stream().filter(d -> d.get("clase").equals("VENTA")).toList(),
                        "iva");
        BigDecimal credit =
                signed(
                        controlled.stream().filter(d -> !d.get("clase").equals("VENTA")).toList(),
                        "credito_computable");
        long pending =
                confirmed.stream().filter(d -> d.get("fiscal_id") == null).count()
                        + purchases.stream()
                                .filter(
                                        d ->
                                                d.get("tipo_documento").equals("FACTURA")
                                                        && d.get("fiscal_id") == null)
                                .count()
                        + expenses.stream().filter(d -> d.get("fiscal_id") == null).count();
        long cancelledWithoutCredit =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM ventas v WHERE v.comercio_id=? AND v.fecha>=? AND v.fecha<? AND v.estado<>'CONFIRMADA' AND EXISTS(SELECT 1 FROM comprobantes_contables f WHERE f.comercio_id=v.comercio_id AND f.clase='VENTA' AND f.operacion_id=v.id AND f.tipo='FACTURA') AND (SELECT COALESCE(SUM(CASE WHEN n.tipo='NOTA_CREDITO' THEN -n.total ELSE n.total END),0) FROM comprobantes_contables n WHERE n.comercio_id=v.comercio_id AND n.clase='VENTA' AND n.operacion_id=v.id)<>0",
                        Long.class,
                        store,
                        from,
                        until);
        var summary = new LinkedHashMap<String, Object>();
        summary.put("ventas_registradas", revenues);
        summary.put("costo_historico_registrado", costs);
        summary.put("gastos_registrados", spending);
        summary.put("resultado_gestion", revenues.subtract(costs).subtract(spending));
        summary.put("compras_registradas", sum(purchases, "total"));
        summary.put("iva_debito_controlado", debit);
        summary.put("iva_credito_computable_controlado", credit);
        summary.put("diferencia_iva_parcial", debit.subtract(credit));
        summary.put(
                "percepciones_iva_compras",
                signed(
                        controlled.stream().filter(d -> !d.get("clase").equals("VENTA")).toList(),
                        "percepcion_iva"));
        summary.put(
                "percepciones_iibb_compras",
                signed(
                        controlled.stream().filter(d -> !d.get("clase").equals("VENTA")).toList(),
                        "percepcion_iibb"));
        summary.put("operaciones_sin_datos_fiscales", pending);
        summary.put("ventas_anuladas_sin_nota_credito", cancelledWithoutCredit);
        summary.put(
                "comprobantes_sin_controlar",
                documents.stream().filter(d -> !flag(d.get("controlado"))).count());
        summary.put(
                "cobros_caja",
                sum(
                        cash.stream().filter(d -> d.get("tipo").equals("INGRESO")).toList(),
                        "importe"));
        summary.put(
                "pagos_caja",
                sum(cash.stream().filter(d -> d.get("tipo").equals("EGRESO")).toList(), "importe"));
        summary.put(
                "cajas_abiertas",
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM cajas WHERE comercio_id=? AND fecha>=? AND fecha<? AND estado='ABIERTA'",
                        Long.class,
                        store,
                        from,
                        until));
        summary.put(
                "saldos_clientes_al_generar",
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(saldo_cuenta_corriente),0) FROM clientes WHERE comercio_id=?",
                        BigDecimal.class,
                        store));
        summary.put(
                "saldos_proveedores_al_generar",
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(saldo_actual),0) FROM proveedores WHERE comercio_id=?",
                        BigDecimal.class,
                        store));
        var result = new LinkedHashMap<String, Object>();
        result.put("mes", start.toString().substring(0, 7));
        result.put("generado_en", LocalDateTime.now(clock).toString());
        result.put("moneda", "ARS");
        result.put("configuracion", settings());
        result.put("fuente", "ACTUAL");
        result.put("estado_periodo", state(start).get("estado"));
        result.put("resumen", summary);
        result.put("ventas", sales);
        result.put("compras", purchases);
        result.put("gastos", expenses);
        result.put("caja", cash);
        result.put("fiscales", documents);
        result.put("alicuotas", rates);
        result.put(
                "notas",
                List.of(
                        "Informe auxiliar para revisión del contador. No es una declaración jurada, un Libro IVA presentado ni un estado contable legal.",
                        "Las ventas de gestión siguen siendo comprobantes internos no fiscales. Los comprobantes fiscales se registran desde documentos emitidos o recibidos fuera de Alon; no se solicita CAE ni se presenta información a ARCA.",
                        "El resultado de gestión usa ventas, costos históricos y gastos registrados, que pueden incluir impuestos. Las compras de mercadería no se restan nuevamente como gasto. No representa el resultado impositivo ni una ganancia neta contable.",
                        "El IVA se agrupa por mes de registración y solo toma comprobantes marcados como controlados. La diferencia parcial no incorpora saldos previos, retenciones ni todos los ajustes de la declaración. Las percepciones se muestran por separado.",
                        "Los saldos de clientes y proveedores corresponden al momento de generación; en un cierre quedan congelados con el informe.",
                        "Los remitos no se consideran facturas fiscales. Los gastos no fiscales quedan identificados para que el contador evalúe su respaldo y tratamiento."));
        return result;
    }

    private static BigDecimal amount(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    static BigDecimal sum(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> amount(r.get(key))).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal signed(List<Map<String, Object>> rows, String key) {
        return rows.stream()
                .map(r -> amount(r.get(key)).multiply(amount(r.get("signo"))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
