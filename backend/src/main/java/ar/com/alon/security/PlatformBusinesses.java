package ar.com.alon.security;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/super/businesses")
@Transactional
public class PlatformBusinesses {
    private final JdbcTemplate jdbc;
    private final RequestIdentity identity;

    @org.springframework.beans.factory.annotation.Value("${alon.provisioning.public-domain:}")
    private String publicDomain = "";

    public PlatformBusinesses(JdbcTemplate jdbc, RequestIdentity identity) {
        this.jdbc = jdbc;
        this.identity = identity;
    }

    private void authorize() {
        if (!"SUPER".equals(identity.user().get("rol")))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Solo Alon puede administrar negocios.");
    }

    @GetMapping
    public Map<String, Object> report() {
        authorize();
        return Map.of(
                "businesses",
                jdbc.queryForList(
                        """
                        SELECT n.*, i.estado AS instalacion_estado, i.gerente_usuario,
                               COALESCE((SELECT SUM(CASE WHEN m.tipo='CARGO' THEN m.importe ELSE -m.importe END)
                                 FROM plataforma_movimientos m WHERE m.negocio_id=n.id AND m.anulado=FALSE),0) saldo
                        FROM plataforma_negocios n
                        LEFT JOIN plataforma_instalaciones i ON i.negocio_id=n.id
                        ORDER BY n.nombre,n.id
                        """),
                "receipts",
                jdbc.queryForList(
                        """
                        SELECT negocio_id,fecha,periodo_desde,meses,cargo_configuracion,total
                        FROM plataforma_recibos WHERE estado='EMITIDO'
                        ORDER BY periodo_desde
                        """),
                "movements",
                jdbc.queryForList(
                        "SELECT m.*,n.nombre FROM plataforma_movimientos m JOIN plataforma_negocios n ON n.id=m.negocio_id ORDER BY m.fecha DESC,m.id DESC"),
                "rates",
                jdbc.queryForList(
                        "SELECT negocio_id,desde,importe FROM plataforma_abonos ORDER BY negocio_id,desde"));
    }

    public Map<String, Object> create(@Valid @RequestBody Business input) {
        authorize();
        if ("BAJA".equals(input.estado()))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Registrá el negocio en preparación, prueba o activo. La baja se confirma desde el listado.");
        String slug = BusinessLinks.slug(input.nombre());
        if (!publicDomain.isBlank()) {
            String label = BusinessLinks.publicLabel(slug);
            jdbc.queryForObject("SELECT id FROM comercios WHERE id=1 FOR UPDATE", Long.class);
            if (BusinessLinks.reservedHost(label)
                    || jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM plataforma_negocios WHERE TRIM(TRAILING '-' FROM LEFT(url_slug, 63))=?",
                                    Long.class,
                                    label)
                            > 0)
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Ese nombre de enlace ya está reservado. Elegí un nombre comercial distinto.");
        }
        jdbc.update(
                "INSERT INTO plataforma_negocios(nombre,razon_social,cuit,rubro,localidad,responsable,telefono,email,plan,abono,estado,alta,vencimiento,gestion_url,tienda_url,base_referencia,respaldo,notas) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                values(input));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        saveRate(id, input.alta().withDayOfMonth(1), input.abono());
        if (!"PRUEBA".equals(input.estado()) && input.cargo_configuracion() != null)
            jdbc.update(
                    "UPDATE plataforma_negocios SET cargo_configuracion=? WHERE id=?",
                    input.cargo_configuracion(),
                    id);
        String management = BusinessLinks.management(id, slug, publicDomain);
        String shop = BusinessLinks.shop(id, slug, publicDomain);
        jdbc.update(
                "UPDATE plataforma_negocios SET url_slug=?,gestion_url=?,tienda_url=? WHERE id=?",
                slug,
                management,
                shop,
                id);
        audit(id, "ALTA");
        return Map.of("id", id, "managementUrl", management, "shopUrl", shop);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(
            @PathVariable long id,
            @RequestParam(required = false) String abonoDesde,
            @Valid @RequestBody Business input) {
        authorize();
        var rows =
                jdbc.queryForList(
                        "SELECT estado,abono,baja_iniciada_en FROM plataforma_negocios WHERE id=? FOR UPDATE",
                        id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el negocio.");
        var current = rows.getFirst();
        if (current.get("baja_iniciada_en") != null)
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La baja definitiva ya fue iniciada. No se puede modificar este negocio.");
        if (!current.get("estado").equals(input.estado()))
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cambiá el estado desde el listado de negocios para aplicar también el control de acceso.");
        BigDecimal prior = (BigDecimal) current.get("abono");
        if (prior.compareTo(input.abono()) != 0) {
            YearMonth currentMonth = YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires"));
            YearMonth effective =
                    abonoDesde == null ? currentMonth : PlatformReceipts.parsePeriod(abonoDesde);
            if (effective.isBefore(currentMonth)
                    || effective.isBefore(YearMonth.from(input.alta())))
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Elegí un mes vigente a partir del alta del negocio.");
            LocalDate start = effective.atDay(1);
            jdbc.update("DELETE FROM plataforma_abonos WHERE negocio_id=? AND desde>=?", id, start);
            saveRate(id, start, input.abono());
            jdbc.update(
                    "INSERT INTO plataforma_auditoria(usuario_id,negocio_id,accion,detalle) VALUES(?,?,'CAMBIO_ABONO',?)",
                    identity.userId(),
                    id,
                    prior.toPlainString()
                            + " → "
                            + input.abono().toPlainString()
                            + " desde "
                            + effective);
        }
        var args = new ArrayList<Object>(Arrays.asList(values(input)));
        args.add(id);
        jdbc.update(
                "UPDATE plataforma_negocios SET nombre=?,razon_social=?,cuit=?,rubro=?,localidad=?,responsable=?,telefono=?,email=?,plan=?,abono=?,estado=?,alta=?,vencimiento=?,gestion_url=?,tienda_url=?,base_referencia=?,respaldo=?,notas=? WHERE id=?",
                args.toArray());
        audit(id, "EDICION");
        return Map.of("ok", true);
    }

    private void saveRate(long id, LocalDate start, BigDecimal amount) {
        jdbc.update(
                "INSERT INTO plataforma_abonos(negocio_id,desde,importe) VALUES(?,?,?)",
                id,
                start,
                amount);
    }

    @PostMapping("/{id}/movements")
    public Map<String, Object> movement(@PathVariable long id, @Valid @RequestBody Movement input) {
        authorize();
        exists(id);
        jdbc.update(
                "INSERT INTO plataforma_movimientos(negocio_id,tipo,fecha,importe,concepto) VALUES(?,?,?,?,?)",
                id,
                input.tipo(),
                input.fecha(),
                input.importe(),
                input.concepto().trim());
        audit(id, input.tipo());
        return Map.of("ok", true);
    }

    private void exists(long id) {
        if (jdbc.queryForObject(
                        "SELECT COUNT(*) FROM plataforma_negocios WHERE id=?", Integer.class, id)
                != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró el negocio.");
    }

    private void audit(long id, String action) {
        jdbc.update(
                "INSERT INTO plataforma_auditoria(usuario_id,negocio_id,accion) VALUES(?,?,?)",
                identity.userId(),
                id,
                action);
    }

    private Object[] values(Business b) {
        return new Object[] {
            b.nombre().trim(),
            b.razon_social(),
            b.cuit(),
            b.rubro(),
            b.localidad(),
            b.responsable(),
            b.telefono(),
            b.email(),
            b.plan(),
            b.abono(),
            b.estado(),
            b.alta(),
            b.vencimiento(),
            b.gestion_url(),
            b.tienda_url(),
            b.base_referencia(),
            b.respaldo(),
            b.notas()
        };
    }

    public record Business(
            @NotBlank @Size(max = 160) String nombre,
            @NotNull @Size(max = 160) String razon_social,
            @NotNull @Pattern(regexp = "[0-9]{11}|") String cuit,
            @NotNull @Size(max = 100) String rubro,
            @NotNull @Size(max = 160) String localidad,
            @NotNull @Size(max = 160) String responsable,
            @NotNull @Size(max = 40) String telefono,
            @NotNull @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String plan,
            @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal abono,
            @NotNull @Pattern(regexp = "PREPARACION|PRUEBA|ACTIVO|SUSPENDIDO|BAJA") String estado,
            @NotNull LocalDate alta,
            LocalDate vencimiento,
            @NotNull @Size(max = 500) @Pattern(regexp = "https?://[^\\s]+|") String gestion_url,
            @NotNull @Size(max = 500) @Pattern(regexp = "https?://[^\\s]+|") String tienda_url,
            @NotNull @Size(max = 160) String base_referencia,
            LocalDate respaldo,
            @NotNull @Size(max = 2000) String notas,
            @JsonInclude(JsonInclude.Include.NON_NULL)
                    @DecimalMin("0")
                    @Digits(integer = 13, fraction = 2)
                    BigDecimal cargo_configuracion) {
        public Business withTrialMonths(int months) {
            return new Business(
                    nombre,
                    razon_social,
                    cuit,
                    rubro,
                    localidad,
                    responsable,
                    telefono,
                    email,
                    plan,
                    abono,
                    estado,
                    alta,
                    alta.plusMonths(months),
                    gestion_url,
                    tienda_url,
                    base_referencia,
                    respaldo,
                    notas,
                    null);
        }
    }

    public record Movement(
            @NotNull @Pattern(regexp = "CARGO|COBRO") String tipo,
            @NotNull LocalDate fecha,
            @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal importe,
            @NotBlank @Size(max = 300) String concepto) {}
}
