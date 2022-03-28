package pt.ulisboa.tecnico.sec.candeeiros.shared;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.List;

public class Signatures {
    public static byte[] signOpenAccountRequest(PrivateKey key, byte[] nonce, byte[] publicKey) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, publicKey);
    }

    public static boolean verifyOpenAccountRequestSignature(byte[] signature, PublicKey signingKey, byte[] nonce, byte[] publicKey) {
        try {
            return Crypto.verifySignature(signingKey, signature, nonce, publicKey);
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
    }

    public static byte[] signOpenAccountResponse(PrivateKey key, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, status.getBytes());
    }

    public static boolean verifyOpenAccountResponseSignature(byte[] signature, PublicKey signingKey, byte[] nonce, String status) {
        try {
            return Crypto.verifySignature(signingKey, signature, nonce, status.getBytes());
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
    }


    public static byte[] signNonceNegotiationRequest(PrivateKey key, byte[] challengeNonce, byte[] publicKey) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, challengeNonce, publicKey);
    }

    public static boolean verifyNonceNegotiationRequestSignature(byte[] signature, PublicKey signingKey, byte[] challengeNonce, byte[] publicKey) {
        try {
            return Crypto.verifySignature(signingKey, signature, challengeNonce, publicKey);
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
}

    public static byte[] signNonceNegotiationResponse(PrivateKey key, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, status.getBytes());
    }

    public static boolean verifyNonceNegotiationResponseSignature(byte[] signature, PublicKey signingKey, byte[] nonce, String status) {
        try {
            return Crypto.verifySignature(signingKey, signature, nonce, status.getBytes());
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
}


    public static byte[] signSendAmountRequest(PrivateKey key, byte[] sourceKey, byte[] destinationKey, String amount, byte[] sourceNonce) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, sourceKey, destinationKey, amount.getBytes(), sourceNonce);
    }

    public static boolean verifySendAmountRequestSignature(byte[] signature, PublicKey signingKey, byte[] sourceKey, byte[] destinationKey, String amount, byte[] sourceNonce) {
        try {
            return Crypto.verifySignature(signingKey, signature, sourceKey, destinationKey, amount.getBytes(), sourceNonce);
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
}

    public static byte[] signSendAmountResponse(PrivateKey key, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, status.getBytes());
    }

    public static boolean verifySendAmountResponseSignature(byte[] signature, PublicKey signingKey, byte[] nonce, String status) {
        try {
            return Crypto.verifySignature(signingKey, signature, nonce, status.getBytes());
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
}

    public static byte[] signReceiveAmountRequest(PrivateKey key, byte[] sourceKey, byte[] destinationKey, String amount, byte[] destinationNonce, boolean accept) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, sourceKey, destinationKey, amount.getBytes(), destinationNonce, new byte[] {accept ? (byte) 1 : 0});
    }

    public static boolean verifyReceiveAmountRequestSignature(byte[] signature, PublicKey signingKey, byte[] sourceKey, byte[] destinationKey, String amount, byte[] destinationNonce, boolean accept) {
        try {
            return Crypto.verifySignature(signingKey, signature, sourceKey, destinationKey, amount.getBytes(), destinationNonce, new byte[] {accept ? (byte) 1 : 0});
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
}

    public static byte[] signReceiveAmountResponse(PrivateKey key, byte[] nonce, String status) throws SignatureException, InvalidKeyException {
        return Crypto.sign(key, nonce, status.getBytes());
    }

    public static boolean verifyReceiveAmountResponseSignature(byte[] signature, PublicKey signingKey, byte[] nonce, String status) {
        try {
            return Crypto.verifySignature(signingKey, signature, nonce, status.getBytes());
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
}

    public static byte[] signCheckAccountResponse(PrivateKey key, byte[] challengeNonce, String status, String balance, List<Bank.NonRepudiableTransaction> transactions) throws SignatureException, InvalidKeyException {
        List<byte[]> data = getCheckAccountDataToSign(challengeNonce, status, balance, transactions);
        return Crypto.sign(key, data.toArray(new byte[0][]));
    }

    public static boolean verifyCheckAccountResponseSignature(byte[] signature, PublicKey signingKey, byte[] challengeNonce, String status, String balance, List<Bank.NonRepudiableTransaction> transactions) {
        List<byte[]> data = getCheckAccountDataToSign(challengeNonce, status, balance, transactions);
        try {
            return Crypto.verifySignature(signingKey, signature, data.toArray(new byte[0][]));
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
}

    private static List<byte[]> getCheckAccountDataToSign(byte[] challengeNonce, String status, String balance, List<Bank.NonRepudiableTransaction> transactions) {
        List<byte[]> data = new ArrayList<>();
        data.add(challengeNonce);
        data.add(status.getBytes());
        data.add(balance.getBytes());
        for (Bank.NonRepudiableTransaction transaction : transactions) {
            data.add(transaction.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray());
            data.add(transaction.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray());
            data.add(transaction.getTransaction().getAmount().getBytes());
            data.add(transaction.getSourceNonce().getNonceBytes().toByteArray());
            data.add(transaction.getSourceSignature().getSignatureBytes().toByteArray());
        }
        return data;
    }

    public static byte[] signAuditResponse(PrivateKey key, byte[] challengeNonce, String status, List<Bank.NonRepudiableTransaction> transactions) throws SignatureException, InvalidKeyException {
        List<byte[]> data = getAuditDataToSign(challengeNonce, status, transactions);
        return Crypto.sign(key, data.toArray(new byte[0][]));
    }

    public static boolean verifyAuditResponseSignature(byte[] signature, PublicKey signingKey, byte[] challengeNonce, String status, List<Bank.NonRepudiableTransaction> transactions) {
        List<byte[]> data = getAuditDataToSign(challengeNonce, status, transactions);
        try {
            return Crypto.verifySignature(signingKey, signature, data.toArray(new byte[0][]));
        } catch (InvalidKeyException | SignatureException e) {
            return false;
        }
    }

    private static List<byte[]> getAuditDataToSign(byte[] challengeNonce, String status, List<Bank.NonRepudiableTransaction> transactions) {
        List<byte[]> data = new ArrayList<>();
        data.add(challengeNonce);
        data.add(status.getBytes());
        for (Bank.NonRepudiableTransaction transaction : transactions) {
            data.add(transaction.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray());
            data.add(transaction.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray());
            data.add(transaction.getTransaction().getAmount().getBytes());
            data.add(transaction.getSourceNonce().getNonceBytes().toByteArray());
            data.add(transaction.getSourceSignature().getSignatureBytes().toByteArray());
            data.add(transaction.getDestinationNonce().getNonceBytes().toByteArray());
            data.add(transaction.getDestinationSignature().getSignatureBytes().toByteArray());
        }
        return data;
    }


    public static boolean verifyPendingTransactionSignature(Bank.NonRepudiableTransaction transaction) {
        try {
            return Crypto.verifySignature(Crypto.decodePublicKey(transaction.getTransaction().getSourcePublicKey()),
                    transaction.getSourceSignature().getSignatureBytes().toByteArray(),
                    transaction.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray(),
                    transaction.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray(),
                    transaction.getTransaction().getAmount().getBytes(),
                    transaction.getSourceNonce().getNonceBytes().toByteArray());
        } catch (NoSuchAlgorithmException  | InvalidKeySpecException | SignatureException | InvalidKeyException e) {
            return false;
        }
    }

    public static boolean verifyAcceptedTransactionSignature(Bank.NonRepudiableTransaction transaction) {
        try {
            return Crypto.verifySignature(Crypto.decodePublicKey(transaction.getTransaction().getDestinationPublicKey()),
                    transaction.getDestinationSignature().getSignatureBytes().toByteArray(),
                    transaction.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray(),
                    transaction.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray(),
                    transaction.getTransaction().getAmount().getBytes(),
                    transaction.getDestinationNonce().getNonceBytes().toByteArray(),
                    new byte[] {(byte) 1});
        } catch (NoSuchAlgorithmException  | InvalidKeySpecException | SignatureException | InvalidKeyException e) {
            return false;
        }
    }
}
