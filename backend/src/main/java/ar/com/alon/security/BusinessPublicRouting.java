package ar.com.alon.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/**
 * Resuelve únicamente hosts de comercios provisionados para el proxy interno y sus certificados.
 */
@RestController
@ConditionalOnProperty(name = "alon.provisioning.enabled", havingValue = "true")
class BusinessPublicRouting {
    private final JdbcTemplate jdbc;
    private final String domain;

    BusinessPublicRouting(
            JdbcTemplate jdbc, @Value("${alon.provisioning.public-domain:}") String domain) {
        this.jdbc = jdbc;
        this.domain = domain;
    }

    @GetMapping("/internal/business-route")
    ResponseEntity<Void> route(
            @RequestHeader(value = "X-Forwarded-Host", required = false) String host) {
        return resolve(host);
    }

    @GetMapping("/internal/business-certificate")
    ResponseEntity<Void> certificate(@RequestParam("domain") String host) {
        return resolve(host);
    }

    private ResponseEntity<Void> resolve(String target) {
        if (this.domain.isBlank()
                || target == null
                || target.length() > 253
                || !target.endsWith("." + this.domain)) return ResponseEntity.notFound().build();
        String label = target.substring(0, target.length() - this.domain.length() - 1);
        if (!label.endsWith(".negocios")) return resolveShort(label);
        label = label.substring(0, label.length() - ".negocios".length());
        if (!label.matches("[a-z0-9-]{1,57}-[0-9]{5}")) return ResponseEntity.notFound().build();
        int port = Integer.parseInt(label.substring(label.length() - 5));
        long id = port - 20000L;
        if (id < 1 || id > 40000) return ResponseEntity.notFound().build();
        var slugs =
                jdbc.queryForList(
                        """
                        SELECT n.url_slug FROM plataforma_negocios n
                        JOIN plataforma_instalaciones i ON i.negocio_id=n.id
                        WHERE n.id=? AND i.puerto=? AND i.estado='LISTO' AND i.iniciar=TRUE
                          AND n.eliminado_en IS NULL AND n.baja_iniciada_en IS NULL
                          AND n.estado IN ('ACTIVO','PRUEBA')
                        """,
                        String.class,
                        id,
                        port);
        if (slugs.isEmpty()
                || !target.equals(BusinessLinks.legacyHost(id, slugs.getFirst(), this.domain)))
            return ResponseEntity.notFound().build();
        return upstream(port);
    }

    private ResponseEntity<Void> resolveShort(String label) {
        if (!label.matches("[a-z0-9]+(?:-[a-z0-9]+)*")
                || label.length() > 63
                || BusinessLinks.reservedHost(label)) return ResponseEntity.notFound().build();
        var ports =
                jdbc.queryForList(
                        """
                        SELECT i.puerto FROM plataforma_negocios n
                        JOIN plataforma_instalaciones i ON i.negocio_id=n.id
                        WHERE TRIM(TRAILING '-' FROM LEFT(n.url_slug, 63))=?
                          AND i.puerto=20000+n.id AND n.id BETWEEN 1 AND 40000
                          AND i.estado='LISTO' AND i.iniciar=TRUE
                          AND n.eliminado_en IS NULL AND n.baja_iniciada_en IS NULL
                          AND n.estado IN ('ACTIVO','PRUEBA')
                          AND (SELECT COUNT(*) FROM plataforma_negocios r
                               WHERE TRIM(TRAILING '-' FROM LEFT(r.url_slug, 63))=?)=1
                        """,
                        Integer.class,
                        label,
                        label);
        return ports.size() == 1 ? upstream(ports.getFirst()) : ResponseEntity.notFound().build();
    }

    private static ResponseEntity<Void> upstream(int port) {
        return ResponseEntity.noContent()
                .header("X-Alon-Upstream-Port", Integer.toString(port))
                .build();
    }
}
