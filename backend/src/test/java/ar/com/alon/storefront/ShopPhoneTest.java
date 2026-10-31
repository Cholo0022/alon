package ar.com.alon.storefront;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.PasswordHasher;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ShopPhoneTest {
    /**
     * Admite código de área y número y recupera cuentas que antes guardaban prefijos argentinos.
     */
    @Test
    void localPhoneFindsExistingArgentinianAccount() {
        assertThat(ShopAccountService.normalizePhone("3446236311")).isEqualTo("+3446236311");
        var jdbc = mock(JdbcTemplate.class);
        var hashes = mock(PasswordHasher.class);
        when(jdbc.queryForList(
                        "SELECT id,password_hash FROM compradores_tienda WHERE comercio_id=? AND telefono IN (?,?,?) AND activo=TRUE",
                        1L,
                        "+3446236311",
                        "+543446236311",
                        "+5493446236311"))
                .thenReturn(List.of(Map.of("id", 2L, "password_hash", "stored")));
        when(jdbc.queryForList(
                        "SELECT id,nombre,apellido,dni,domicilio,codigo_postal,ciudad,provincia,pais,telefono,telefono_verificado FROM compradores_tienda WHERE comercio_id=? AND id=? AND activo=TRUE",
                        1L,
                        2L))
                .thenReturn(List.of(Map.of("id", 2L, "nombre", "Comprador")));
        when(hashes.verify("12345678", "stored")).thenReturn(true);
        var service = new ShopAccountService(jdbc, hashes, 1);
        assertThat(service.login(new ShopAccountService.Login("3446236311", "12345678")))
                .containsEntry("id", 2L);
        assertThatThrownBy(
                        () ->
                                service.login(
                                        new ShopAccountService.Login("3446236311", "incorrecta")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
