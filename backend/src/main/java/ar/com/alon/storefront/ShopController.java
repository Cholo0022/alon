// Recibe las solicitudes de pedidos y compradores de la tienda web y las deriva al servicio
// correspondiente.
package ar.com.alon.storefront;

import ar.com.alon.security.BrowserSecurityFilter;
import ar.com.alon.security.LoginThrottle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {
    private final ShopAccountService accounts;
    private final ShopService shop;
    private final LoginThrottle throttle;

    /**
     * Recibe y conserva las dependencias necesarias para el catálogo, acceso y pedidos de
     * compradores.
     */
    public ShopController(ShopAccountService accounts, ShopService shop, LoginThrottle throttle) {
        this.accounts = accounts;
        this.shop = shop;
        this.throttle = throttle;
    }

    /** Recibe búsqueda, rubro y página y entrega el catálogo público de la tienda. */
    @GetMapping("/catalog")
    public Map<String, Object> catalog(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "0") int page) {
        return shop.catalog(q.trim(), category, page);
    }

    /** Entrega la cuenta de comprador de la sesión y su token CSRF para operaciones posteriores. */
    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        return Map.of(
                "user",
                accounts.current(request),
                "csrfToken",
                BrowserSecurityFilter.token(request));
    }

    /** Limita intentos por dirección, registra al comprador e inicia su sesión. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(
            @Valid @RequestBody ShopAccountService.Registration input, HttpServletRequest request) {
        throttle.check("shop:" + request.getRemoteAddr());
        return accounts.start(accounts.register(input), request);
    }

    /** Limita intentos por dirección, valida las credenciales del comprador e inicia su sesión. */
    @PostMapping("/login")
    public Map<String, Object> login(
            @Valid @RequestBody ShopAccountService.Login input, HttpServletRequest request) {
        throttle.check("shop:" + request.getRemoteAddr());
        return accounts.start(accounts.login(input), request);
    }

    /**
     * Retira la cuenta de comprador de la sesión sin modificar otros accesos que pueda contener.
     */
    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        if (request.getSession(false) != null)
            request.getSession(false).removeAttribute("alonShopper");
        return Map.of("ok", true);
    }

    /** Exige comprador autenticado y delega el pedido con su clave para evitar duplicar envíos. */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> checkout(
            @Valid @RequestBody ShopService.Checkout input, HttpServletRequest request) {
        return shop.checkout(
                input, accounts.require(request), request.getHeader("Idempotency-Key"));
    }

    /**
     * Entrega los pedidos del comprador autenticado, sin permitir consultar los de otras cuentas.
     */
    @GetMapping("/orders")
    public List<Map<String, Object>> orders(HttpServletRequest request) {
        return shop.orders(((Number) accounts.require(request).get("id")).longValue());
    }

    /**
     * Entrega los datos actuales de los artículos solicitados para revalidar el carrito antes del
     * envío.
     */
    @PostMapping("/cart")
    public List<Map<String, Object>> cart(@Valid @RequestBody ShopService.CartInput input) {
        return shop.cart(input);
    }
}
