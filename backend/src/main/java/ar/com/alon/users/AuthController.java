// Recibe las solicitudes de usuarios y permisos y las deriva al servicio correspondiente.
package ar.com.alon.users;

import ar.com.alon.security.BrowserSecurityFilter;
import ar.com.alon.security.LoginThrottle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService model;

    private final LoginThrottle throttle;

    public AuthController(UserService model, LoginThrottle throttle) {
        this.model = model;
        this.throttle = throttle;
    }

    @GetMapping("/status")
    public AuthStatus status(HttpServletRequest request) {
        Object user =
                request.getSession(false) == null
                        ? null
                        : request.getSession(false).getAttribute("alonUser");
        Map<String, Object> current =
                user instanceof Map<?, ?> saved ? model.sessionUser(saved) : Map.of();
        if (request.getSession(false) != null)
            request.getSession(false).setAttribute("alonUser", current);
        return new AuthStatus(
                model.setupRequired(),
                SessionUser.from(current),
                BrowserSecurityFilter.token(request));
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionUser setup(
            @Valid @RequestBody UserService.Credentials input, HttpServletRequest request) {
        throttle.check(request.getRemoteAddr());
        return startSession(model.setup(input), request);
    }

    @PostMapping("/login")
    public SessionUser login(
            @Valid @RequestBody UserService.Credentials input, HttpServletRequest request) {
        throttle.check(request.getRemoteAddr());
        return startSession(model.authenticate(input), request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return Map.of("ok", true);
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return model.users();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(@Valid @RequestBody UserService.UserInput input) {
        return model.createUser(input);
    }

    @PatchMapping("/users/{id}")
    public Map<String, Object> updateUser(
            @PathVariable long id, @Valid @RequestBody UserService.UserUpdateInput input) {
        return model.updateUser(id, input);
    }

    @PatchMapping("/users/{id}/active")
    public Map<String, Object> setActive(
            @PathVariable long id,
            @Valid @RequestBody ActiveInput input,
            HttpServletRequest request) {
        Map<?, ?> current = (Map<?, ?>) request.getSession(false).getAttribute("alonUser");
        model.setActive(id, input.active(), ((Number) current.get("id")).longValue());
        return Map.of("ok", true);
    }

    private SessionUser startSession(Map<String, Object> user, HttpServletRequest request) {
        if (request.getSession(false) != null) request.changeSessionId();
        request.getSession(true).setAttribute("alonUser", user);
        return SessionUser.from(user);
    }

    public record AuthStatus(boolean setupRequired, SessionUser user, String csrfToken) {}

    public record ActiveInput(@jakarta.validation.constraints.NotNull Boolean active) {}
}
