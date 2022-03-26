package pt.ulisboa.tecnico.sec.candeeiros.client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedAuthenticationException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedChallengeException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.WrongNonceException;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.List;

public class BankClient {
    private final ManagedChannel channel;
    private final BankServiceGrpc.BankServiceBlockingStub stub;
    private final PublicKey serverPublicKey;

    public BankClient(String target, PublicKey serverPublicKey) {
        this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        this.stub = BankServiceGrpc.newBlockingStub(channel);
        this.serverPublicKey = serverPublicKey;
    }

    public void shutdown() {
        channel.shutdown();
    }


    // ***** Authenticated procedures *****
    public Bank.OpenAccountResponse openAccount(PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.OpenAccountRequest request = Bank.OpenAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setChallengeNonce(challengeNonce.encode())
                .build();

        Bank.OpenAccountResponse response = stub.openAccount(request);

        try {
            if(!Crypto.verifySignature(serverPublicKey, response.getSignature().getSignatureBytes().toByteArray(),
                response.getChallengeNonce().getNonceBytes().toByteArray(),
                response.getStatus().name().getBytes()
                ))
                throw new FailedAuthenticationException();
        } catch (InvalidKeyException | SignatureException e) {
            // Should never happen
            e.printStackTrace();
        }
        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }


    public Bank.NonceNegotiationResponse nonceNegotiation(PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.NonceNegotiationRequest request = Bank.NonceNegotiationRequest.newBuilder()
                .setChallengeNonce(challengeNonce.encode())
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        Bank.NonceNegotiationResponse response = stub.nonceNegotiation(request);

        try {
            if(!Crypto.verifySignature(serverPublicKey, response.getSignature().getSignatureBytes().toByteArray(),
                response.getChallengeNonce().getNonceBytes().toByteArray(),
                response.getStatus().name().getBytes(),
                response.getNonce().getNonceBytes().toByteArray()
                ))
                throw new FailedAuthenticationException();
        } catch (InvalidKeyException | SignatureException e) {
            // Should never happen
            e.printStackTrace();
        }
        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }


    public Bank.SendAmountResponse sendAmount(PrivateKey sourcePrivateKey, PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount, Nonce nonce) throws WrongNonceException, FailedAuthenticationException {
        Bank.Transaction transaction = Bank.Transaction
                .newBuilder()
                .setSourcePublicKey(Crypto.encodePublicKey(sourcePublicKey))
                .setDestinationPublicKey(Crypto.encodePublicKey(destinationPublicKey))
                .setAmount(amount)
                .build();


        Bank.SendAmountRequest.Builder request = Bank.SendAmountRequest
                .newBuilder()
                .setTransaction(transaction)
                .setNonce(nonce.nextNonce().encode());

        try {
            request.setSignature(Bank.Signature.newBuilder().setSignatureBytes(ByteString.copyFrom(Crypto.sign(sourcePrivateKey, 
                request.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray(),
                request.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray(),
                request.getTransaction().getAmount().getBytes(),
                request.getNonce().getNonceBytes().toByteArray()
            ))));
        } catch (InvalidKeyException | SignatureException e) {
            // Should never happen
            e.printStackTrace();
        }

        Bank.SendAmountResponse response = stub.sendAmount(request.build());
    
        try {
            if(!Crypto.verifySignature(serverPublicKey, response.getSignature().getSignatureBytes().toByteArray(),
                response.getNonce().getNonceBytes().toByteArray(),
                response.getStatus().name().getBytes()
                ))
                throw new FailedAuthenticationException();
        } catch (InvalidKeyException | SignatureException e) {
            // Should never happen
            e.printStackTrace();
        }

        if (!isNextNonce(nonce, Nonce.decode(response.getNonce())))
            throw new WrongNonceException();

        return response;
    }


    public Bank.ReceiveAmountResponse receiveAmount(PrivateKey privateKey, PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount, boolean accept, Nonce nonce) throws WrongNonceException, FailedAuthenticationException {
        Bank.Transaction transaction = Bank.Transaction
                .newBuilder()
                .setSourcePublicKey(Crypto.encodePublicKey(sourcePublicKey))
                .setDestinationPublicKey(Crypto.encodePublicKey(destinationPublicKey))
                .setAmount(amount)
                .build();

        Bank.ReceiveAmountRequest.Builder request = Bank.ReceiveAmountRequest
                .newBuilder()
                .setTransaction(transaction)
                .setAccept(accept)
                .setNonce(nonce.nextNonce().encode());

        try {
            request.setSignature(Bank.Signature.newBuilder().setSignatureBytes(ByteString.copyFrom(Crypto.sign(privateKey, 
                request.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray(),
                request.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray(),
                request.getTransaction().getAmount().getBytes(),
                request.getNonce().getNonceBytes().toByteArray()
            ))));
        } catch (InvalidKeyException | SignatureException e) {
            // Should never happen
            e.printStackTrace();
        }

        Bank.ReceiveAmountResponse response = stub.receiveAmount(request.build());

        try {
            if(!Crypto.verifySignature(serverPublicKey, response.getSignature().getSignatureBytes().toByteArray(),
                response.getNonce().getNonceBytes().toByteArray(),
                response.getStatus().name().getBytes()
                ))
                throw new FailedAuthenticationException();
        } catch (InvalidKeyException | SignatureException e) {
            // Should never happen
            e.printStackTrace();
        }

        if (!isNextNonce(nonce, Nonce.decode(response.getNonce())))
            throw new WrongNonceException();

        return response;
    }

    // ***** Unauthenticated procedures *****
    public Bank.CheckAccountResponse checkAccount(PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.CheckAccountRequest request = Bank.CheckAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setChallengeNonce(challengeNonce.encode())
                .build();

        Bank.CheckAccountResponse response = stub.checkAccount(request);

        try {
            List<Byte> toSign = new ArrayList<>();

            for (Bank.NonRepudiableTransaction t : response.getTransactionsList()) {
                insertPrimitiveToByteList(toSign, t.getTransaction().getSourcePublicKey().getKeyBytes().toByteArray());
                insertPrimitiveToByteList(toSign, t.getTransaction().getDestinationPublicKey().getKeyBytes().toByteArray());
                insertPrimitiveToByteList(toSign, t.getTransaction().getAmount().getBytes());
                insertPrimitiveToByteList(toSign, t.getSourceNonce().getNonceBytes().toByteArray());
                insertPrimitiveToByteList(toSign, t.getSourceSignature().getSignatureBytes().toByteArray());
                // TODO verify signature on transaction?
            }

            if(!Crypto.verifySignature(serverPublicKey, response.getSignature().getSignatureBytes().toByteArray(),
                    response.getChallengeNonce().getNonceBytes().toByteArray(),
                    response.getStatus().name().getBytes(),
                    byteListToPrimitiveByteArray(toSign)
            ))
                throw new FailedAuthenticationException();
        } catch (InvalidKeyException | SignatureException e) {
            // Should never happen
            e.printStackTrace();
        }

        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }

    public Bank.AuditResponse audit(PublicKey publicKey) throws FailedChallengeException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.AuditRequest request = Bank.AuditRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setChallengeNonce(challengeNonce.encode())
                .build();

        Bank.AuditResponse response = stub.audit(request);

        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }


    private boolean isNextNonce(Nonce sent, Nonce received) {
        return sent.nextNonce().equals(received);
    }

    // Java does not support inserting a byte[] into a List<Byte> with addAll due to boxing
    public static void insertPrimitiveToByteList(List<Byte> list, byte[] array) {
        for (byte b : array)
            list.add(b);
    }

    public static byte[] byteListToPrimitiveByteArray(List<Byte> list) {
        byte[] bytes = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            bytes[i] = list.get(i);
        }
        return bytes;
    }
}
