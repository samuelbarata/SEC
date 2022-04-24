package pt.ulisboa.tecnico.sec.candeeiros.shared;

import com.google.protobuf.ByteString;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class Nonce {
    private final byte[] bytes;
    private static int accountNumber = 0;

    private Nonce(byte[] bytes) {
        if (bytes.length != 16) {
            throw new NumberFormatException();
        }
        this.bytes = bytes;
    }

    public Nonce nextNonce() {
        byte[] bytes = this.bytes.clone();

        int i = -1;
        do {
            i++;
            bytes[i] += 1;
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

    public static Nonce newAccountNonce() {
        byte[] bytes = new byte[16];
        Arrays.fill(bytes, (byte) 0);
        bytes[15] = (byte) accountNumber;
        bytes[14] = (byte) (accountNumber / 256);
        bytes[13] = (byte) (accountNumber / 256*256);
        bytes[12] = (byte) (accountNumber / 256*256*256);
        accountNumber++;
        return new Nonce(bytes);
    }

    public static Nonce fromString(String nonce) {
        return new Nonce(Base64.getDecoder().decode(nonce));
    }

    public static Nonce decode(Bank.Nonce nonce) {
        return new Nonce(nonce.getNonceBytes().toByteArray());
    }

    public Bank.Nonce encode() {
        return Bank.Nonce.newBuilder().setNonceBytes(ByteString.copyFrom(bytes)).build();
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return new String(Base64.getEncoder().encode(bytes));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Nonce nonce = (Nonce) o;
        return Arrays.equals(bytes, nonce.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
