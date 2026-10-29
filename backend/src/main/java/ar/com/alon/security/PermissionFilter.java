package ar.com.alon.security;

import ar.com.alon.users.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PermissionFilter extends OncePerRequestFilter {
    private static final Set<String> AUTH_PUBLIC =
            Set.of("/api/v1/auth/status", "/api/v1/auth/setup", "/api/v1/auth/login");
    private final UserService auth;

    public PermissionFilter(UserService auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        if (!path.startsWith("/api/v1/")
                || path.equals("/api/v1/health")
                || AUTH_PUBLIC.contains(path)) {
            chain.doFilter(req, res);
            return;
        }
        Object sessionUser =
                req.getSession(false) == null
                        ? null
                        : req.getSession(false).getAttribute("alonUser");
        if (!(sessionUser instanceof Map<?, ?> user)) {
            reject(res, 401, "Iniciá sesión para continuar.");
            return;
        }
        Map<String, Object> current = auth.sessionUser(user);
        if (current.isEmpty()) {
            req.getSession(false).invalidate();
            reject(res, 401, "La sesión venció o el acceso fue revocado.");
            return;
        }
        req.getSession(false).setAttribute("alonUser", current);
        req.setAttribute("alonIdentity", current);
        String role = String.valueOf(current.get("rol"));
        req.setAttribute("alonRole", role);
        if ("DUENO".equals(role)) {
            chain.doFilter(req, res);
            return;
        }
        if ("/api/v1/auth/logout".equals(path)) {
            chain.doFilter(req, res);
            return;
        }
        String required = permissionFor(path);
        if ("usuarios".equals(required)) {
            reject(res, 403, "Solo el dueño puede administrar usuarios.");
            return;
        }
        Set<String> permissions =
                Arrays.stream(
                                String.valueOf(
                                                current.get("permisos") == null
                                                        ? ""
                                                        : current.get("permisos"))
                                        .split(","))
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toSet());
        if ("GET".equals(req.getMethod())
                && (path.equals("/api/v1/demo/lookups/customers")
                                && (permissions.contains("ventas")
                                        || permissions.contains("clientes"))
                        || path.equals("/api/v1/demo/lookups/suppliers")
                                && (permissions.contains("articulos")
                                        || permissions.contains("compras")
                                        || permissions.contains("proveedores")))) {
            chain.doFilter(req, res);
            return;
        }
        if ("/api/v1/demo/articles".equals(path)
                && "GET".equalsIgnoreCase(req.getMethod())
                && (permissions.contains("ventas") || permissions.contains("compras"))) {
            chain.doFilter(req, res);
            return;
        }
        if ("/api/v1/demo/categories".equals(path)
                && "GET".equalsIgnoreCase(req.getMethod())
                && (permissions.contains("articulos")
                        || permissions.contains("rubros")
                        || permissions.contains("compras"))) {
            chain.doFilter(req, res);
            return;
        }
        if (required == null || !permissions.contains(required)) {
            reject(res, 403, "Tu usuario no tiene acceso a esta sección.");
            return;
        }
        chain.doFilter(req, res);
    }

    private String permissionFor(String path) {
        if (path.startsWith("/api/v1/auth/users")) return "usuarios";
        if (path.startsWith("/api/v1/auth/")) return "usuarios";
        if (path.startsWith("/api/v1/operations/cash")
                || path.startsWith("/api/v1/demo/cash/")
                || path.startsWith("/api/v1/demo/reports/cash")) return "caja";
        if (path.startsWith("/api/v1/operations/sales") || path.startsWith("/api/v1/demo/sales"))
            return "ventas";
        if (path.startsWith("/api/v1/operations/customers")
                || path.startsWith("/api/v1/demo/customers")) return "clientes";
        if (path.startsWith("/api/v1/operations/categories")
                || path.startsWith("/api/v1/demo/categories")) return "rubros";
        if (path.startsWith("/api/v1/operations/articles")
                || path.startsWith("/api/v1/demo/articles")
                || path.startsWith("/api/v1/demo/stock-movements")) return "articulos";
        if (path.startsWith("/api/v1/operations/purchases")
                || path.startsWith("/api/v1/demo/purchases")) return "compras";
        if (path.startsWith("/api/v1/operations/suppliers")
                || path.startsWith("/api/v1/demo/suppliers")) return "proveedores";
        if (path.startsWith("/api/v1/operations/store-orders")
                || path.startsWith("/api/v1/demo/store-orders")) return "tienda";
        if (path.startsWith("/api/v1/demo/reports/")) return "informes";
        if (path.startsWith("/api/v1/demo/summary")) return "inicio";
        return null;
    }

    private void reject(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
