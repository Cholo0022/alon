package ar.com.alon.storefront;

import ar.com.alon.security.BrowserSecurityFilter;
import ar.com.alon.security.LoginThrottle;
import ar.com.alon.storefront.dto.GuestCheckoutInput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Tienda pública sin cuentas. Los datos personales y el historial solo se consultan en gestión. */
@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {
    private final ShopService shop;
    private final LoginThrottle throttle;

    public ShopController(ShopService shop, LoginThrottle throttle) {
        this.shop = shop;
        this.throttle = throttle;
    }

    @GetMapping("/catalog")
    public Map<String, Object> catalog(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "0") int page) {
        return shop.catalog(q.trim(), category, page);
    }

    /** La sesión anónima solo conserva el token CSRF, no identifica a un comprador. */
    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).removeAttribute("alonShopper");
            request.getSession(false).removeAttribute("alonShopperCredential");
        }
        return Map.of("csrfToken", BrowserSecurityFilter.token(request));
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> checkout(
            @Valid @RequestBody GuestCheckoutInput input, HttpServletRequest request) {
        // Se limita por sesión anónima, para no agrupar a todos los clientes detrás del proxy.
        throttle.check("shop-order:" + request.getSession(true).getId());
        return shop.checkoutGuest(input, request.getHeader("Idempotency-Key"));
    }

    @PostMapping("/cart")
    public List<Map<String, Object>> cart(@Valid @RequestBody ShopService.CartInput input) {
        return shop.cart(input);
    }

    @PostMapping({"/login", "/register", "/password", "/password/reset", "/logout"})
    @ResponseStatus(HttpStatus.GONE)
    public Map<String, String> retiredAccounts() {
        return Map.of(
                "error",
                "La tienda recibe pedidos sin cuenta. Volvé al catálogo y completá tus datos al enviar el pedido.");
    }

    @GetMapping("/orders")
    @ResponseStatus(HttpStatus.GONE)
    public Map<String, String> retiredPublicHistory() {
        return Map.of(
                "error", "El seguimiento del pedido se coordina directamente con el vendedor.");
    }
}
