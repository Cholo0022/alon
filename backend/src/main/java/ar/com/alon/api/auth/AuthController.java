package ar.com.alon.api.auth;

import java.util.List;
import java.util.Map;

import ar.com.alon.model.AuthModel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthModel model;

    public AuthController(AuthModel model) { this.model = model; }

    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest request) {
        Object user = request.getSession(false) == null ? null : request.getSession(false).getAttribute("alonUser");
        return Map.of("setupRequired", model.setupRequired(), "user", user == null ? Map.of() : user);
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> setup(@RequestBody AuthModel.Credentials input, HttpServletRequest request) {
        return startSession(model.setup(input), request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AuthModel.Credentials input, HttpServletRequest request) {
        return startSession(model.authenticate(input), request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        return Map.of("ok", true);
    }

    @GetMapping("/users")
    public List<Map<String,Object>> users() { return model.users(); }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> createUser(@RequestBody AuthModel.UserInput input) { return model.createUser(input); }

    @PatchMapping("/users/{id}")
    public Map<String,Object> updateUser(@PathVariable long id, @RequestBody AuthModel.UserUpdateInput input) {
        return model.updateUser(id, input);
    }

    @PatchMapping("/users/{id}/active")
    public Map<String,Object> setActive(@PathVariable long id, @RequestBody ActiveInput input, HttpServletRequest request) {
        Map<?,?> current = (Map<?,?>) request.getSession(false).getAttribute("alonUser");
        model.setActive(id, input.active(), ((Number) current.get("id")).longValue());
        return Map.of("ok", true);
    }

    private Map<String,Object> startSession(Map<String,Object> user, HttpServletRequest request) {
        if (request.getSession(false) != null) request.changeSessionId();
        request.getSession(true).setAttribute("alonUser", user);
        return user;
    }

    public record ActiveInput(boolean active) {}
}
