package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;

import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void usesVersionedSaltedHashesAndValidatesOldPasswords() throws Exception {
        String password = "Una contraseña segura 2026";
        String hash = hasher.hash(password);
        assertThat(hash).startsWith("pbkdf2$600000$").isNotEqualTo(hasher.hash(password));
        assertThat(hasher.verify(password, hash)).isTrue();
        assertThat(hasher.verify("incorrecta", hash)).isFalse();
        byte[] salt = new byte[16];
        byte[] derived =
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(new PBEKeySpec("demo".toCharArray(), salt, 210_000, 256))
                        .getEncoded();
        String legacy =
                "pbkdf2$"
                        + Base64.getEncoder().encodeToString(salt)
                        + "$"
                        + Base64.getEncoder().encodeToString(derived);
        assertThat(hasher.verify("demo", legacy)).isTrue();
        assertThat(hasher.needsUpgrade(legacy)).isTrue();
        assertThat(hasher.verify(password, "pbkdf2$999999999$invalid$invalid")).isFalse();
    }
}
