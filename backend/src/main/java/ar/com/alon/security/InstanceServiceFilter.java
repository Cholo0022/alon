package ar.com.alon.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Control local de acceso, también para sesiones existentes y pedidos públicos de la tienda. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class InstanceServiceFilter extends OncePerRequestFilter {
    private final String file;

    public InstanceServiceFilter(
            @Value("${alon.service-state-file:${ALON_SERVICE_STATE_FILE:}}") String file) {
        this.file = file;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (file.isBlank() || !request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        response.setHeader("X-Alon-Service-Control", "enabled");
        response.setHeader("Cache-Control", "no-store");
        if (request.getRequestURI().equals("/api/v1/health")) {
            try {
                response.setHeader("X-Alon-Service-State", InstanceTerms.state(file, java.time.Clock.system(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))));
            } catch (IOException exception) {
                response.setHeader("X-Alon-Service-State", "UNAVAILABLE");
            }
            chain.doFilter(request, response);
            return;
        }
        String state;
        try {
            state = InstanceTerms.state(file, java.time.Clock.system(java.time.ZoneId.of("America/Argentina/Buenos_Aires")));
            response.setHeader("X-Alon-Service-State", state);
        } catch (IOException exception) {
            reject(response, 503, "No se pudo verificar la habilitación de este negocio.");
            return;
        }
        if (!Set.of("ACTIVO", "PRUEBA").contains(state)) {
            reject(
                    response,
                    403,
                    switch (state) {
                        case "PRUEBA_VENCIDA" -> "El período de prueba venció. Contactá a Alon para activar el servicio.";
                        case "BAJA" -> "Este negocio fue dado de baja. Contactá a Alon.";
                        case "SUSPENDIDO" ->
                                "Este negocio está inhabilitado temporalmente. Contactá a Alon.";
                        default ->
                                "Este negocio está en preparación y todavía no está habilitado para operar.";
                    });
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
