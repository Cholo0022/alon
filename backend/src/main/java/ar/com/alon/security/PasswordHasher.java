// Protege las contraseñas y permite verificar claves guardadas con formatos anteriores.
package ar.com.alon.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {
    private static final int ITERATIONS = 600_000;
    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return "pbkdf2$"
                + ITERATIONS
                + "$"
                + Base64.getEncoder().encodeToString(salt)
                + "$"
                + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }

    public boolean verify(String password, String stored) {
        if (password == null || password.length() > 128 || stored == null) return false;
        try {
            String[] parts = stored.split("\\$");
            if (!"pbkdf2".equals(parts[0]) || (parts.length != 3 && parts.length != 4))
                return false;
            int iterations = parts.length == 3 ? 210_000 : Integer.parseInt(parts[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            byte[] salt = Base64.getDecoder().decode(parts[parts.length - 2]);
            byte[] expected = Base64.getDecoder().decode(parts[parts.length - 1]);
            return salt.length == 16
                    && expected.length == 32
                    && MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean needsUpgrade(String stored) {
        return !stored.startsWith("pbkdf2$" + ITERATIONS + "$");
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo procesar la contraseña.", exception);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }
}
