// Protege las solicitudes del navegador y comprueba el token de las operaciones que guardan datos.
package ar.com.alon.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class BrowserSecurityFilter extends OncePerRequestFilter {
    /**
     * Obtiene o crea en la sesión un token CSRF, sincronizando su generación para solicitudes
     * simultáneas.
     */
    public static String token(HttpServletRequest request) {
        var session = request.getSession(true);
        synchronized (session) {
            if (session.getAttribute("csrfToken") == null)
                session.setAttribute("csrfToken", UUID.randomUUID().toString());
            return (String) session.getAttribute("csrfToken");
        }
    }

    /**
     * Agrega encabezados de seguridad y evita caché en la API; exige un token CSRF válido para
     * solicitudes que modifican datos.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "same-origin");
        response.setHeader("X-Frame-Options", "DENY");
        if (request.getRequestURI().startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store");
            if (!java.util.Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
                var session = request.getSession(false);
                String expected =
                        session == null ? null : (String) session.getAttribute("csrfToken");
                String actual = request.getHeader("X-CSRF-Token");
                if (expected == null
                        || actual == null
                        || !MessageDigest.isEqual(
                                expected.getBytes(StandardCharsets.UTF_8),
                                actual.getBytes(StandardCharsets.UTF_8))) {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter()
                            .write(
                                    "{\"error\":\"La sesión de seguridad venció. Recargá la página.\"}");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
