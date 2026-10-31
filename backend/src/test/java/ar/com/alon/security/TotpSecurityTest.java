package ar.com.alon.security;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TotpSecurityTest {
    @Test
    void matchesOfficialRfc6238Sha1Vectors(@TempDir Path directory) {
        var totp = new TotpSecurity(directory.resolve("key").toString());
        String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
        long[] seconds = {59, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L};
        String[] expected = {
            "94287082", "07081804", "14050471", "89005924", "69279037", "65353130"
        };
        for (int i = 0; i < seconds.length; i++)
            assertThat(totp.code(secret, seconds[i] / 30, 8)).isEqualTo(expected[i]);
        String code = totp.code(secret, 3, 6);
        assertThat(totp.matchingStep(secret, code, 90, -1)).isEqualTo(3);
        assertThat(totp.matchingStep(secret, code, 90, 3)).isEqualTo(-1);
        assertThat(totp.matchingStep(secret, code, 210, -1)).isEqualTo(-1);
    }

    @Test
    void encryptsSecretsWithAuthenticatedRandomNonces(@TempDir Path directory) {
        var crypto = new TotpSecurity(directory.resolve("key").toString());
        String secret = crypto.newSecret();
        assertThat(secret).matches("[A-Z2-7]{32}");
        String cipher = crypto.encrypt(secret);
        assertThat(cipher).doesNotContain(secret).isNotEqualTo(crypto.encrypt(secret));
        assertThat(crypto.decrypt(cipher)).isEqualTo(secret);
        byte[] damaged = Base64.getDecoder().decode(cipher);
        damaged[damaged.length - 1] ^= 1;
        assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(damaged)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(crypto.qr(secret, "owner")).startsWith("data:image/svg+xml;base64,");
        var wrong = new TotpSecurity(directory.resolve("missing").toString());
        assertThatThrownBy(() -> wrong.decrypt(cipher)).isInstanceOf(IllegalStateException.class);
        assertThat(wrong.hasKey()).isFalse();
    }
}
