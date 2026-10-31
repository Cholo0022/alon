package ar.com.alon.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TotpSecurity {
    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /**
     * Conserva la ubicación externa a la base donde se guarda la clave de cifrado del autenticador.
     */
    public TotpSecurity(@Value("${alon.security-key-file:./data/security/mfa.key}") String file) {
        keyFile = Path.of(file).toAbsolutePath();
    }

    /** Genera bytes criptográficos y los representa como un token opaco apto para cookies. */
    public String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Genera un secreto Base32 de 160 bits compatible con aplicaciones TOTP. */
    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        StringBuilder result = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 255);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(BASE32.charAt((buffer >> bits) & 31));
            }
        }
        return result.toString();
    }

    /**
     * Deriva el código HOTP para un intervalo; permite ocho dígitos para comprobar los vectores
     * oficiales RFC 6238.
     */
    public String code(String secret, long step, int digits) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            int buffer = 0, bits = 0;
            for (char c : secret.toCharArray()) {
                int value = BASE32.indexOf(c);
                if (value < 0) throw new IllegalArgumentException("Secreto inválido");
                buffer = (buffer << 5) | value;
                bits += 5;
                if (bits >= 8) {
                    bits -= 8;
                    bytes.write((buffer >> bits) & 255);
                }
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(bytes.toByteArray(), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 15;
            int value = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            return String.format(
                    Locale.ROOT, "%0" + digits + "d", value % (digits == 8 ? 100000000 : 1000000));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo validar el autenticador.", e);
        }
    }

    /**
     * Valida seis dígitos con un intervalo de tolerancia y rechaza códigos de intervalos ya
     * utilizados.
     */
    public long matchingStep(String secret, String supplied, long seconds, long lastStep) {
        if (supplied == null || !supplied.matches("[0-9]{6}")) return -1;
        long current = seconds / 30;
        for (long step = current - 1; step <= current + 1; step++)
            if (step > lastStep
                    && MessageDigest.isEqual(
                            code(secret, step, 6).getBytes(StandardCharsets.US_ASCII),
                            supplied.getBytes(StandardCharsets.US_ASCII))) return step;
        return -1;
    }

    /**
     * Calcula SHA-256 para persistir tokens y códigos de recuperación de alta entropía sin su valor
     * original.
     */
    public String hash(String input) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Carga la clave existente; crea una nueva solo al cifrar el primer secreto, nunca al intentar
     * descifrar uno existente.
     */
    private synchronized byte[] key(boolean create) throws java.io.IOException {
        if (!Files.exists(keyFile)) {
            if (!create)
                throw new IllegalStateException(
                        "Falta la clave de seguridad. Restaurá el archivo de clave respaldado.");
            Files.createDirectories(keyFile.getParent());
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            Files.write(keyFile, bytes, StandardOpenOption.CREATE_NEW);
            var posix = Files.getFileAttributeView(keyFile, PosixFileAttributeView.class);
            if (posix != null) posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
            var acl = Files.getFileAttributeView(keyFile, AclFileAttributeView.class);
            if (acl != null)
                acl.setAcl(
                        List.of(
                                AclEntry.newBuilder()
                                        .setType(AclEntryType.ALLOW)
                                        .setPrincipal(Files.getOwner(keyFile))
                                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                                        .build()));
        }
        byte[] bytes = Files.readAllBytes(keyFile);
        if (bytes.length != 32)
            throw new IllegalStateException("La clave de seguridad no tiene un formato válido.");
        return bytes;
    }

    /**
     * Cifra un secreto con AES-GCM y un nonce nuevo; la clave permanece fuera de la base de datos.
     */
    public String encrypt(String secret) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key(true), "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder()
                    .encodeToString(
                            ByteBuffer.allocate(12 + encrypted.length)
                                    .put(nonce)
                                    .put(encrypted)
                                    .array());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo proteger el autenticador.", e);
        }
    }

    /**
     * Descifra y verifica la integridad del secreto; falla cerrado si falta la clave o se alteraron
     * los datos.
     */
    public String decrypt(String value) {
        try {
            byte[] bytes = Base64.getDecoder().decode(value);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key(false), "AES"),
                    new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
            return new String(
                    cipher.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)),
                    StandardCharsets.US_ASCII);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo leer el autenticador. Revisá la clave de seguridad del servidor.",
                    e);
        }
    }

    /** Construye el QR localmente para que el secreto nunca se envíe a un generador externo. */
    public String qr(String secret, String username) {
        try {
            String label =
                    java.net.URLEncoder.encode("Alon:" + username, StandardCharsets.UTF_8)
                            .replace("+", "%20");
            String uri =
                    "otpauth://totp/"
                            + label
                            + "?secret="
                            + secret
                            + "&issuer=Alon&algorithm=SHA1&digits=6&period=30";
            var matrix = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 0, 0);
            int size = matrix.getWidth();
            var svg =
                    new StringBuilder(
                            "<svg xmlns='http://www.w3.org/2000/svg' shape-rendering='crispEdges' viewBox='0 0 "
                                    + size
                                    + " "
                                    + size
                                    + "'><rect width='100%' height='100%' fill='white'/><path fill='black' d='");
            for (int y = 0; y < size; y++)
                for (int x = 0; x < size; x++)
                    if (matrix.get(x, y))
                        svg.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
            svg.append("'/></svg>");
            return "data:image/svg+xml;base64,"
                    + Base64.getEncoder()
                            .encodeToString(svg.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo generar el QR.", error);
        }
    }

    /** Comprueba que la clave ya exista antes de añadir secretos a una instalación protegida. */
    public boolean hasKey() {
        return Files.isRegularFile(keyFile);
    }
}
