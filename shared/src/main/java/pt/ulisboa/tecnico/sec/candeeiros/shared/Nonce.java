package pt.ulisboa.tecnico.sec.candeeiros.shared;

import java.security.SecureRandom;
import java.util.Base64;

public class Nonce {
    private final byte[] bytes;

    private Nonce(byte[] bytes) {
        if (bytes.length != 16) {
            throw new NumberFormatException();
        }
        this.bytes = bytes;
    }

    public Nonce nextNonce() {
        byte[] bytes = this.bytes.clone();

        int i = 0;
        do {
            bytes[i] += 1;
            i++;
        } while (bytes[i] == 0 && i < bytes.length);
        return new Nonce(bytes);
    }

    public static Nonce newNonce(byte[] bytes) {
        return new Nonce(bytes.clone());
    }

    public static Nonce newNonce() {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[16];
        sr.nextBytes(bytes);
        return new Nonce(bytes);
    }

    public byte[] getBytes() {
        return bytes;
    }

    public static Nonce fromString(String nonce) {
        return new Nonce(Base64.getDecoder().decode(nonce));
    }

    @Override
    public String toString() {
        return new String(Base64.getEncoder().encode(bytes));
    }
}
