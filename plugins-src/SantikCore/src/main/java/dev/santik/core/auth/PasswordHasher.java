package dev.santik.core.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/** PBKDF2-HMAC-SHA256 с солью. Формат: pbkdf2$iter$saltHex$hashHex */
public final class PasswordHasher {
    private static final SecureRandom RNG = new SecureRandom();
    private final int iterations;

    public PasswordHasher(int iterations) { this.iterations = iterations; }

    public String hash(String password) {
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        return "pbkdf2$" + iterations + "$" + HexFormat.of().formatHex(salt) + "$"
                + HexFormat.of().formatHex(derive(password, salt, iterations));
    }

    public boolean verify(String password, String stored) {
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4 || !p[0].equals("pbkdf2")) return false;
            byte[] expected = HexFormat.of().parseHex(p[3]);
            byte[] actual = derive(password, HexFormat.of().parseHex(p[2]), Integer.parseInt(p[1]));
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] derive(String password, byte[] salt, int iter) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iter, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
