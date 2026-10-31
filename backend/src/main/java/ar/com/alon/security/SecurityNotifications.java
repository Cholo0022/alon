package ar.com.alon.security;

import java.sql.Timestamp;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class SecurityNotifications {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ObjectProvider<JavaMailSender> sender;
    private final String host, from;

    /**
     * Prepara el buzón de salida; las credenciales SMTP se leen del entorno, nunca de formularios
     * ni del código.
     */
    public SecurityNotifications(
            JdbcTemplate jdbc,
            Clock clock,
            ObjectProvider<JavaMailSender> sender,
            @Value("${spring.mail.host:}") String host,
            @Value("${alon.security-mail-from:}") String from) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.sender = sender;
        this.host = host;
        this.from = from;
    }

    /** Informa si hay un servidor y remitente configurados para enviar los avisos pendientes. */
    public boolean configured() {
        return !host.isBlank() && !from.isBlank() && sender.getIfAvailable() != null;
    }

    /**
     * Encola el aviso a las direcciones de las cuentas administradoras activas del comercio, sin
     * incluir secretos ni códigos de acceso.
     */
    public void owners(long store, String subject, String text) {
        for (var user :
                jdbc.queryForList(
                        "SELECT security_email FROM usuarios WHERE comercio_id=? AND rol IN ('DUENO','SUPER') AND activo=TRUE AND security_email<>''",
                        store))
            jdbc.update(
                    "INSERT INTO security_mail_queue(comercio_id,destino,asunto,contenido,creado_en) VALUES(?,?,?,?,?)",
                    store,
                    user.get("security_email"),
                    subject,
                    text,
                    Timestamp.from(clock.instant()));
    }

    /**
     * Envía lotes pequeños con hasta tres intentos; si SMTP falta, conserva los avisos y permite
     * seguir usando las aprobaciones de Alon.
     */
    @Scheduled(fixedDelay = 30000)
    public void deliver() {
        if (!configured()) return;
        for (var row :
                jdbc.queryForList(
                        "SELECT q.id,q.destino,q.asunto,q.contenido FROM security_mail_queue q WHERE q.enviado_en IS NULL AND q.intentos<3 AND EXISTS(SELECT 1 FROM usuarios u WHERE u.comercio_id=q.comercio_id AND u.security_email=q.destino AND u.activo=TRUE AND u.rol IN ('DUENO','SUPER')) ORDER BY q.id LIMIT 10")) {
            jdbc.update(
                    "UPDATE security_mail_queue SET intentos=intentos+1 WHERE id=?", row.get("id"));
            try {
                var mail = new SimpleMailMessage();
                mail.setFrom(from);
                mail.setTo(row.get("destino").toString());
                mail.setSubject(row.get("asunto").toString());
                mail.setText(row.get("contenido").toString());
                sender.getObject().send(mail);
                jdbc.update(
                        "UPDATE security_mail_queue SET enviado_en=? WHERE id=?",
                        Timestamp.from(clock.instant()),
                        row.get("id"));
            } catch (org.springframework.mail.MailException error) {
                org.slf4j.LoggerFactory.getLogger(getClass())
                        .warn(
                                "No se pudo enviar un aviso de seguridad; revisar la configuración SMTP. Aviso {}",
                                row.get("id"));
            }
        }
    }
}
