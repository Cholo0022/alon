// Recibe las solicitudes de usuarios y permisos y las deriva al servicio correspondiente.
package ar.com.alon.users;

import ar.com.alon.security.AccessSecurityService;
import ar.com.alon.security.BrowserSecurityFilter;
import ar.com.alon.security.LoginThrottle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService model;
    private final AccessSecurityService security;

    private final LoginThrottle throttle;

    /**
     * Recibe y conserva las dependencias necesarias para el acceso de gestión y la administración
     * de usuarios.
     */
    public AuthController(
            UserService model, LoginThrottle throttle, AccessSecurityService security) {
        this.security = security;
        this.model = model;
        this.throttle = throttle;
    }

    /**
     * Devuelve un usuario autorizado solo si completó la seguridad; en caso contrario informa la
     * etapa pendiente y el token CSRF sin conceder permisos.
     */
    @GetMapping("/status")
    public AuthStatus status(HttpServletRequest request) {
        Object user =
                request.getSession(false) == null
                        ? null
                        : request.getSession(false).getAttribute("alonUser");
        Map<String, Object> current =
                user instanceof Map<?, ?> saved ? model.sessionUser(saved) : Map.of();
        if (!current.isEmpty() && !security.validSession(current, request)) current = Map.of();
        return new AuthStatus(
                model.setupRequired(),
                SessionUser.from(current),
                BrowserSecurityFilter.token(request),
                security.challenge(request),
                security.license(current));
    }

    /**
     * Limita intentos, crea la primera cuenta administradora e inicia la activación obligatoria del
     * autenticador.
     */
    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthStatus setup(
            @Valid @RequestBody UserService.Credentials input,
            HttpServletRequest request,
            HttpServletResponse response) {
        throttle.check(request.getRemoteAddr());
        security.begin(model.setup(input), request, response);
        return status(request);
    }

    /**
     * Valida la contraseña del Gerente, exige el autenticador de alon o la aprobación del navegador
     * del empleado según el rol.
     */
    @PostMapping("/login")
    public AuthStatus login(
            @Valid @RequestBody UserService.Credentials input,
            HttpServletRequest request,
            HttpServletResponse response) {
        throttle.check(request.getRemoteAddr());
        security.begin(model.authenticate(input), request, response);
        return status(request);
    }

    /** Verifica el autenticador de alon y conserva el bloqueo por cuenta e IP. */
    @PostMapping("/super-login")
    public AuthStatus superLogin(@Valid @RequestBody SuperCode input, HttpServletRequest request) {
        throttle.check(request.getRemoteAddr());
        security.superLogin(input.code(), request);
        return status(request);
    }

    public record SuperCode(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 80)
                    String code) {}

    /** Invalida la sesión HTTP actual y confirma su cierre. */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return Map.of("ok", true);
    }

    /** Entrega el listado de usuarios del comercio sin datos secretos de autenticación. */
    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return model.users();
    }

    /** Recibe los datos del usuario nuevo y delega la validación y creación en el servicio. */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(@Valid @RequestBody UserService.UserInput input) {
        return model.createUser(input);
    }

    /**
     * Recibe la edición del usuario y delega los controles de rol, permisos y conservación del
     * gerente.
     */
    @PatchMapping("/users/{id}")
    public Map<String, Object> updateUser(
            @PathVariable long id, @Valid @RequestBody UserService.UserUpdateInput input) {
        return model.updateUser(id, input);
    }

    /**
     * Solicita cambiar el acceso de un usuario identificando a quien opera para impedir su propia
     * desactivación.
     */
    @PatchMapping("/users/{id}/active")
    public Map<String, Object> setActive(
            @PathVariable long id,
            @Valid @RequestBody ActiveInput input,
            HttpServletRequest request) {
        Map<?, ?> current = (Map<?, ?>) request.getSession(false).getAttribute("alonUser");
        model.setActive(id, input.active(), ((Number) current.get("id")).longValue());
        return Map.of("ok", true);
    }

    public record AuthStatus(
            boolean setupRequired,
            SessionUser user,
            String csrfToken,
            Map<String, Object> challenge,
            Map<String, Object> license) {}

    public record ActiveInput(@jakarta.validation.constraints.NotNull Boolean active) {}
}
