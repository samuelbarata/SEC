package pt.ulisboa.tecnico.sec.candeeiros.shared;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.List;

public class Signatures {
    public static byte[] signOpenAccountRequest(PrivateKey key, byte[] nonce, byte[] publicKey) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, publicKey);
    }

    public static boolean verifyOpenAccountRequestSignature(byte[] signature, PublicKey signingKey, byte[] nonce, byte[] publicKey) throws SignatureException, InvalidKeyException {
        return Crypto.verifySignature(signingKey, signature, nonce, publicKey);
    }

    public static byte[] signOpenAccountResponse(PrivateKey key, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, status.getBytes());
    }

    public static boolean verifyOpenAccountResponseSignature(byte[] signature, PublicKey signingKey, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.verifySignature(signingKey, signature, nonce, status.getBytes());
    }


    public static byte[] signNonceNegotiationRequest(PrivateKey key, byte[] challengeNonce, byte[] publicKey) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, challengeNonce, publicKey);
    }

    public static boolean verifyNonceNegotiationRequestSignature(byte[] signature, PublicKey signingKey, byte[] challengeNonce, byte[] publicKey) throws SignatureException, InvalidKeyException {
        return Crypto.verifySignature(signingKey, signature, challengeNonce, publicKey);
    }

    public static byte[] signNonceNegotiationResponse(PrivateKey key, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, status.getBytes());
    }

    public static boolean verifyNonceNegotiationResponseSignature(byte[] signature, PublicKey signingKey, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.verifySignature(signingKey, signature, nonce, status.getBytes());
    }


    public static byte[] signSendAmountRequest(PrivateKey key, byte[] sourceKey, byte[] destinationKey, String amount, byte[] sourceNonce) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, sourceKey, destinationKey, amount.getBytes(), sourceNonce);
    }

    public static boolean verifySendAmountRequestSignature(byte[] signature, PublicKey signingKey, byte[] sourceKey, byte[] destinationKey, String amount, byte[] sourceNonce) throws SignatureException, InvalidKeyException {
        return Crypto.verifySignature(signingKey, signature, sourceKey, destinationKey, amount.getBytes(), sourceNonce);
    }

    public static byte[] signSendAmountResponse(PrivateKey key, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, status.getBytes());
    }

    public static boolean verifySendAmountResponseSignature(byte[] signature, PublicKey signingKey, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.verifySignature(signingKey, signature, nonce, status.getBytes());
    }

    public static byte[] signReceiveAmountRequest(PrivateKey key, byte[] sourceKey, byte[] destinationKey, String amount, byte[] destinationNonce, boolean accept) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, sourceKey, destinationKey, amount.getBytes(), destinationNonce, new byte[] {accept ? (byte) 1 : 0});
    }

    public static boolean verifyReceiveAmountRequestSignature(byte[] signature, PublicKey signingKey, byte[] sourceKey, byte[] destinationKey, String amount, byte[] destinationNonce, boolean accept) throws SignatureException, InvalidKeyException {
        return Crypto.verifySignature(signingKey, signature, sourceKey, destinationKey, amount.getBytes(), destinationNonce, new byte[] {accept ? (byte) 1 : 0});
    }

    public static byte[] signReceiveAmountResponse(PrivateKey key, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, status.getBytes());
    }

    public static boolean verifyReceiveAmountResponseSignature(byte[] signature, PublicKey signingKey, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.verifySignature(signingKey, signature, nonce, status.getBytes());
    }


    public static byte[] signCheckAccountResponse(PrivateKey key, byte[] challengeNonce, String status, String balance, List<Bank.NonRepudiableTransaction> transactions) {
        // TODO
        return new byte[0];
    }

    public static boolean verifyCheckAccountResponseSignature(byte[] signature, PublicKey signingKey, byte[] challengeNonce, String status, String balance, List<Bank.NonRepudiableTransaction> transactions) {
        // TODO
        return true;
    }
}
