package ar.com.alon.storefront;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ar.com.alon.security.PasswordHasher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ShopSimpleResetTest {
    /**
     * El modo simple cambia solo la cuenta compradora identificada, sin consultar la contraseña
     * anterior.
     */
    @Test
    void resetReplacesOnlyMatchedBuyerPassword() {
        var jdbc = mock(JdbcTemplate.class);
        var hashes = mock(PasswordHasher.class);
        when(jdbc.queryForList(
                        "SELECT id FROM compradores_tienda WHERE comercio_id=? AND telefono IN (?,?,?) AND activo=TRUE FOR UPDATE",
                        Long.class,
                        1L,
                        "+3446236311",
                        "+543446236311",
                        "+5493446236311"))
                .thenReturn(List.of(2L));
        when(hashes.hash("1234")).thenReturn("hash-nuevo");
        var accounts = new ShopAccountService(jdbc, hashes, 1);
        accounts.resetPassword(new ShopAccountService.PasswordReset("3446236311", "1234"));
        verify(jdbc)
                .update(
                        "UPDATE compradores_tienda SET password_hash=? WHERE comercio_id=? AND id=? AND activo=TRUE",
                        "hash-nuevo",
                        1L,
                        2L);
        verify(hashes, never()).verify(any(), any());
        assertThatThrownBy(
                        () ->
                                accounts.resetPassword(
                                        new ShopAccountService.PasswordReset("3446236311", "123")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                accounts.resetPassword(
                                        new ShopAccountService.PasswordReset("1111111111", "1234")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
