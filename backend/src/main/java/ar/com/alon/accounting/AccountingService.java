package ar.com.alon.accounting;

import ar.com.alon.security.*;
import ar.com.alon.shared.audit.AuditLog;
import ar.com.alon.shared.web.BusinessConflict;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AccountingService {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;
    private final Clock clock;
    private final AuditLog audit;
    private final AccountingReport reports;
    private final JsonMapper mapper;

    public AccountingService(
            JdbcTemplate jdbc,
            RequestIdentity identity,
            Clock clock,
            AuditLog audit,
            AccountingReport reports,
            JsonMapper mapper) {
        this.jdbc = jdbc;
        this.identity = identity;
        this.clock = clock;
        this.audit = audit;
        this.reports = reports;
        this.mapper = mapper;
    }

    public record Settings(
            boolean controlPeriodos,
            boolean cargaImpuestos,
            @NotNull @Size(max = 11) String cuit,
            @NotNull String condicionIva,
            @NotBlank @Size(min = 10, max = 500) String motivo) {}

    public record Change(
            @NotBlank String mes, @NotBlank @Size(min = 10, max = 500) String motivo) {}

    private void manage() {
        identity.require("contabilidad");
        if (!UserRoles.administrator(identity.user().get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo el superusuario o el gerente con Contabilidad puede cambiar opciones, abrir, cerrar o reabrir períodos.");
    }

    private void lock() {
        jdbc.queryForObject(
                "SELECT id FROM comercios WHERE id=? FOR UPDATE", Long.class, identity.storeId());
    }

    @Transactional
    public Map<String, Object> settings(Settings input) {
        manage();
        lock();
        if (!Set.of("RESPONSABLE_INSCRIPTO", "MONOTRIBUTO", "EXENTO", "SIN_CONFIGURAR")
                .contains(input.condicionIva()))
            throw new IllegalArgumentException("Condición de IVA inválida.");
        if ((input.cargaImpuestos() || !input.cuit().isBlank())
                && !FiscalInput.validCuit(input.cuit()))
            throw new IllegalArgumentException(
                    "Ingresá la CUIT válida del negocio para activar la carga con impuestos.");
        var before = reports.settings();
        jdbc.update(
                "UPDATE comercios SET control_periodos=?,carga_impuestos=?,cuit_contable=?,condicion_iva=? WHERE id=?",
                input.controlPeriodos(),
                input.cargaImpuestos(),
                input.cuit(),
                input.condicionIva(),
                identity.storeId());
        var after = reports.settings();
        audit.record(
                "CONTABILIDAD_CONFIG",
                identity.storeId(),
                "CAMBIO",
                before,
                Map.of("configuracion", after, "motivo", input.motivo()));
        return after;
    }

    @Transactional
    public Map<String, Object> open(Change input) {
        manage();
        lock();
        LocalDate month = AccountingPeriods.month(input.mes());
        var before = reports.state(month);
        jdbc.update(
                "INSERT INTO periodos_contables(comercio_id,mes,estado,actualizado_por,actualizado_en,motivo) VALUES(?,?,'ABIERTO',?,?,?) ON DUPLICATE KEY UPDATE estado='ABIERTO',actualizado_por=VALUES(actualizado_por),actualizado_en=VALUES(actualizado_en),motivo=VALUES(motivo)",
                identity.storeId(),
                java.sql.Date.valueOf(month),
                identity.userId(),
                Timestamp.valueOf(LocalDateTime.now(clock)),
                input.motivo());
        audit.record(
                "PERIODO_CONTABLE",
                identity.storeId(),
                "CERRADO".equals(before.get("estado")) ? "REAPERTURA" : "APERTURA",
                before,
                Map.of("mes", input.mes(), "motivo", input.motivo()));
        return reports.state(month);
    }

    @Transactional
    public Map<String, Object> close(Change input) {
        manage();
        lock();
        LocalDate month = AccountingPeriods.month(input.mes());
        var config = reports.settings();
        if (!AccountingReport.flag(config.get("control_periodos")))
            throw new BusinessConflict("Activá el control mensual para cerrar períodos.");
        if (!"ABIERTO".equals(reports.state(month).get("estado")))
            throw new BusinessConflict("El mes debe estar abierto antes de cerrarlo.");
        var report = reports.live(month);
        var summary = (Map<?, ?>) report.get("resumen");
        if (((Number) summary.get("cajas_abiertas")).longValue() != 0)
            throw new BusinessConflict(
                    "Hay cajas abiertas en ese mes. Cerrá esas sesiones antes del cierre mensual.");
        if (AccountingReport.flag(config.get("carga_impuestos"))
                && (((Number) summary.get("operaciones_sin_datos_fiscales")).longValue() != 0
                        || ((Number) summary.get("comprobantes_sin_controlar")).longValue() != 0
                        || ((Number) summary.get("ventas_anuladas_sin_nota_credito")).longValue()
                                != 0))
            throw new BusinessConflict(
                    "Hay operaciones sin datos fiscales o comprobantes sin controlar. Completalos antes de cerrar el período con impuestos.");
        Integer revision =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(revision),0)+1 FROM cierres_contables WHERE comercio_id=? AND mes=?",
                        Integer.class,
                        identity.storeId(),
                        java.sql.Date.valueOf(month));
        jdbc.update(
                "INSERT INTO cierres_contables(comercio_id,mes,revision,informe,cerrado_por,cerrado_en,motivo) VALUES(?,?,?,?,?,?,?)",
                identity.storeId(),
                java.sql.Date.valueOf(month),
                revision,
                mapper.writeValueAsString(report),
                identity.userId(),
                Timestamp.valueOf(LocalDateTime.now(clock)),
                input.motivo());
        jdbc.update(
                "UPDATE periodos_contables SET estado='CERRADO',actualizado_por=?,actualizado_en=?,motivo=? WHERE comercio_id=? AND mes=?",
                identity.userId(),
                Timestamp.valueOf(LocalDateTime.now(clock)),
                input.motivo(),
                identity.storeId(),
                java.sql.Date.valueOf(month));
        audit.record(
                "PERIODO_CONTABLE",
                identity.storeId(),
                "CIERRE",
                Map.of("mes", input.mes()),
                Map.of("mes", input.mes(), "revision", revision, "motivo", input.motivo()));
        return Map.of("mes", input.mes(), "estado", "CERRADO", "revision", revision);
    }

    public List<Map<String, Object>> history(String month) {
        identity.require("contabilidad");
        return jdbc.queryForList(
                "SELECT c.id,c.mes,c.revision,c.cerrado_en,c.motivo,u.usuario AS responsable FROM cierres_contables c JOIN usuarios u ON u.id=c.cerrado_por WHERE c.comercio_id=? AND c.mes=? ORDER BY c.revision DESC",
                identity.storeId(),
                java.sql.Date.valueOf(AccountingPeriods.month(month)));
    }
}
