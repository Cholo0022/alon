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

    public ShopController(ShopAccountService accounts, ShopService shop, LoginThrottle throttle) {
        this.accounts = accounts;
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

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest request) {
        return Map.of(
                "user",
                accounts.current(request),
                "csrfToken",
                BrowserSecurityFilter.token(request));
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(
            @Valid @RequestBody ShopAccountService.Registration input, HttpServletRequest request) {
        throttle.check("shop:" + request.getRemoteAddr());
        return accounts.start(accounts.register(input), request);
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @Valid @RequestBody ShopAccountService.Login input, HttpServletRequest request) {
        throttle.check("shop:" + request.getRemoteAddr());
        return accounts.start(accounts.login(input), request);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        if (request.getSession(false) != null)
            request.getSession(false).removeAttribute("alonShopper");
        return Map.of("ok", true);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> checkout(
            @Valid @RequestBody ShopService.Checkout input, HttpServletRequest request) {
        return shop.checkout(
                input, accounts.require(request), request.getHeader("Idempotency-Key"));
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(HttpServletRequest request) {
        return shop.orders(((Number) accounts.require(request).get("id")).longValue());
    }

    @PostMapping("/cart")
    public List<Map<String, Object>> cart(@Valid @RequestBody ShopService.CartInput input) {
        return shop.cart(input);
    }
}
