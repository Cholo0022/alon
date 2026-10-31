package ar.com.alon.security;

import ar.com.alon.users.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/security")
public class AccessSecurityController {
    private final AccessSecurityService security;
    private final AuthController auth;
    private final LoginThrottle throttle;

    /**
     * Conecta los pasos de acceso y la administración de seguridad sin convertir sesiones
     * pendientes en permisos.
     */
    public AccessSecurityController(
            AccessSecurityService security, AuthController auth, LoginThrottle throttle) {
        this.security = security;
        this.auth = auth;
        this.throttle = throttle;
    }

    public record Verify(@Size(max = 80) String code, boolean saved) {}

    public record Enrollment(
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
            @Email @Size(max = 254) String email) {}

    public record EmailInput(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 80) String code) {}

    public record Reset(
            @NotBlank @Size(max = 128) String password, @NotBlank @Size(max = 80) String code) {}

    /**
     * Entrega QR y clave manual solo a una sesión con activación pendiente y contraseña comprobada.
     */
    @PostMapping("/enroll")
    public Map<String, Object> enrollment(HttpServletRequest request) {
        return security.enrollment(request);
    }

    /** Confirma el autenticador nuevo y pasa a la entrega temporal de códigos de recuperación. */
    @PostMapping("/confirm")
    public AuthController.AuthStatus confirm(
            @Valid @RequestBody Enrollment input, HttpServletRequest request) {
        security.confirmEnrollment(input.code(), input.email(), request);
        return auth.status(request);
    }

    /**
     * Verifica el segundo factor o consulta una aprobación vinculada exclusivamente a la sesión y
     * su cookie.
     */
    @PostMapping("/continue")
    public AuthController.AuthStatus advance(
            @Valid @RequestBody(required = false) Verify input, HttpServletRequest request) {
        security.advance(
                input == null ? null : input.code(), input != null && input.saved(), request);
        return auth.status(request);
    }

    /** Cancela la solicitud o activación pendiente y vuelve al formulario de credenciales. */
    @PostMapping("/cancel")
    public AuthController.AuthStatus cancel(HttpServletRequest request) {
        security.cancel(request);
        return auth.status(request);
    }

    /** Entrega al gerente sus datos de cuenta y los accesos del mismo comercio. */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return security.dashboard();
    }

    /** Entrega al gerente la cantidad de solicitudes para el aviso de la cabecera. */
    @GetMapping("/pending-count")
    public Map<String, Object> count() {
        return Map.of("count", security.pendingCount());
    }

    /** Aplica una decisión del gerente sin permitir operar sobre dispositivos de otro comercio. */
    @PostMapping("/browsers/{id}/{action}")
    public Map<String, Object> decide(
            @PathVariable String id, @PathVariable String action, HttpServletRequest request) {
        security.decide(id, action, request);
        return Map.of("ok", true);
    }

    /** Guarda el correo del usuario gerente después de una verificación reciente. */
    @PutMapping("/email")
    public Map<String, Object> email(
            @Valid @RequestBody EmailInput input, HttpServletRequest request) {
        security.email(input.email(), input.code(), request);
        return Map.of("ok", true);
    }

    /** Reemplaza los códigos de emergencia y los entrega una vez al navegador autorizado. */
    @PostMapping("/recovery")
    public Map<String, Object> recovery(
            @Valid @RequestBody Verify input, HttpServletRequest request) {
        return Map.of("recoveryCodes", security.regenerate(input.code(), request));
    }

    /** Requiere nuevamente contraseña y factor antes de comenzar el cambio de celular. */
    @PostMapping("/reset")
    public AuthController.AuthStatus reset(
            @Valid @RequestBody Reset input, HttpServletRequest request) {
        throttle.check(request.getRemoteAddr());
        security.reset(input.password(), input.code(), request);
        return auth.status(request);
    }
}
