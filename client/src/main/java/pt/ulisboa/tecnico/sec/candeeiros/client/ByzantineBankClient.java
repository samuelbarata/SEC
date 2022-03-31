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
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;

public class ByzantineBankClient {
    private final ManagedChannel channel;
    private final BankServiceGrpc.BankServiceBlockingStub stub;
    private final PublicKey serverPublicKey;

    // Faults
    private Bank.NonceNegotiationResponse replayedResponse = null;
    private int sendAmountCount = 0;
    private Bank.SendAmountResponse sendAmountResponse = null;

    public ByzantineBankClient(String target, PublicKey serverPublicKey) {
        this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        this.stub = BankServiceGrpc.newBlockingStub(channel);
        this.serverPublicKey = serverPublicKey;
    }

    public void shutdown() {
        channel.shutdown();
    }

    public Bank.OpenAccountResponse openAccountSendWrongSignature(PrivateKey privateKey, PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        Nonce challengeNonce = Nonce.newNonce();

        byte[] signature = Signatures.signOpenAccountRequest(privateKey, challengeNonce.getBytes(), publicKey.getEncoded());

        // FAULT
        signature[0] = 0;
        signature[1] = 0;
        signature[2] = 0;

        Bank.OpenAccountRequest request = Bank.OpenAccountRequest.newBuilder()
        .setPublicKey(Crypto.encodePublicKey(publicKey))
        .setChallengeNonce(challengeNonce.encode())
        .setSignature(Bank.Signature.newBuilder()
                .setSignatureBytes(ByteString.copyFrom(
                        signature
                ))
                .build())
        .build();

        Bank.OpenAccountResponse response = stub.openAccount(request);

        if (response.getStatus() == Bank.OpenAccountResponse.Status.INVALID_MESSAGE_FORMAT)
            return response;

        if (!Signatures.verifyOpenAccountResponseSignature(response.getSignature().getSignatureBytes().toByteArray(), serverPublicKey,
                response.getChallengeNonce().getNonceBytes().toByteArray(),
                response.getStatus().name()))
            throw new FailedAuthenticationException();
        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }

    public Bank.OpenAccountResponse openAccountReceiveWrongSignature(PrivateKey privateKey, PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.OpenAccountRequest request = Bank.OpenAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setChallengeNonce(challengeNonce.encode())
                .setSignature(Bank.Signature.newBuilder()
                        .setSignatureBytes(ByteString.copyFrom(
                                Signatures.signOpenAccountRequest(privateKey, challengeNonce.getBytes(), publicKey.getEncoded())
                        ))
                        .build())
                .build();

        Bank.OpenAccountResponse response = stub.openAccount(request);

        // FAULT
        byte[] signature = response.getSignature().getSignatureBytes().toByteArray();
        signature[0] = 0;
        signature[1] = 0;
        signature[2] = 0;

        Bank.OpenAccountResponse.Builder builder = Bank.OpenAccountResponse.newBuilder()
                .setStatus(response.getStatus())
                .setChallengeNonce(response.getChallengeNonce())
                .setSignature(Bank.Signature.newBuilder()
                    .setSignatureBytes(ByteString.copyFrom(signature)).build());

        response = builder.build();

        if (response.getStatus() == Bank.OpenAccountResponse.Status.INVALID_MESSAGE_FORMAT)
            return response;

        if (!Signatures.verifyOpenAccountResponseSignature(response.getSignature().getSignatureBytes().toByteArray(), serverPublicKey,
                response.getChallengeNonce().getNonceBytes().toByteArray(),
                response.getStatus().name()))
            throw new FailedAuthenticationException();
        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }


    public Bank.NonceNegotiationResponse nonceNegotiationReplayAttack(PrivateKey privateKey, PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.NonceNegotiationRequest request = Bank.NonceNegotiationRequest.newBuilder()
                .setChallengeNonce(challengeNonce.encode())
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setSignature(Bank.Signature.newBuilder()
                        .setSignatureBytes(ByteString.copyFrom(Signatures.signNonceNegotiationRequest(privateKey,
                                challengeNonce.getBytes(),
                                publicKey.getEncoded())))
                        .build())
                .build();

        Bank.NonceNegotiationResponse response = stub.nonceNegotiation(request);

        // FAULT
        if (replayedResponse == null) {
            replayedResponse = response;
        } else {
            response = replayedResponse;
        }

        if (response.getStatus() == Bank.NonceNegotiationResponse.Status.INVALID_MESSAGE_FORMAT)
            return response;

        if (!Signatures.verifyNonceNegotiationResponseSignature(response.getSignature().getSignatureBytes().toByteArray(), serverPublicKey,
                response.getChallengeNonce().getNonceBytes().toByteArray(),
                response.getStatus().name()))
            throw new FailedAuthenticationException();

        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }


    public Bank.SendAmountResponse sendAmountDelayedReplayAttack(PrivateKey sourcePrivateKey, PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount, Nonce nonce) throws WrongNonceException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        Bank.Transaction transaction = Bank.Transaction
                .newBuilder()
                .setSourcePublicKey(Crypto.encodePublicKey(sourcePublicKey))
                .setDestinationPublicKey(Crypto.encodePublicKey(destinationPublicKey))
                .setAmount(amount)
                .build();

        Nonce nextNonce = nonce.nextNonce();

        Bank.SendAmountRequest request = Bank.SendAmountRequest
                .newBuilder()
                .setTransaction(transaction)
                .setNonce(nextNonce.encode())
                .setSignature(Bank.Signature.newBuilder()
                    .setSignatureBytes(ByteString.copyFrom(Signatures.signSendAmountRequest(sourcePrivateKey,
                            sourcePublicKey.getEncoded(),
                            destinationPublicKey.getEncoded(),
                            amount,
                            nextNonce.getBytes()
                            )))
                    .build())
                .build();


        Bank.SendAmountResponse response = stub.sendAmount(request);

        // FAULT
        sendAmountCount++;
        if (sendAmountCount == 1) {
            sendAmountResponse = response;
        } else if (sendAmountCount == 3) {
            response = sendAmountResponse;
        }

        if (response.getStatus() == Bank.SendAmountResponse.Status.INVALID_MESSAGE_FORMAT)
            return response;

        if (!Signatures.verifySendAmountResponseSignature(response.getSignature().getSignatureBytes().toByteArray(), serverPublicKey,
                response.getNonce().getNonceBytes().toByteArray(),
                response.getStatus().name()))
            throw new FailedAuthenticationException();
    
        if (!isNextNonce(nonce, Nonce.decode(response.getNonce())))
            throw new WrongNonceException();

        return response;
    }

    private boolean isNextNonce(Nonce sent, Nonce received) {
        return sent.nextNonce().equals(received);
    }
}
