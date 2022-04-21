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
    public Bank.OpenAccountResponse openAccount(PrivateKey privateKey, PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.OpenAccountRequest request = Bank.OpenAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setChallengeNonce(challengeNonce.encode())
                .setSignature(Bank.Signature.newBuilder()
                        .setSignatureBytes(ByteString.copyFrom(Signatures.signOpenAccountRequest(privateKey, challengeNonce.getBytes(), publicKey.getEncoded())))
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


    public Bank.NonceNegotiationResponse nonceNegotiation(PrivateKey privateKey, PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
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


    public Bank.SendAmountResponse sendAmount(PrivateKey sourcePrivateKey, PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount, Nonce nonce) throws WrongNonceException, FailedAuthenticationException, SignatureException, InvalidKeyException {
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


    public Bank.ReceiveAmountResponse receiveAmount(PrivateKey privateKey, PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount, boolean accept, Nonce nonce) throws WrongNonceException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        Bank.Transaction transaction = Bank.Transaction
                .newBuilder()
                .setSourcePublicKey(Crypto.encodePublicKey(sourcePublicKey))
                .setDestinationPublicKey(Crypto.encodePublicKey(destinationPublicKey))
                .setAmount(amount)
                .build();

        Nonce nextNonce = nonce.nextNonce();

        Bank.ReceiveAmountRequest request = Bank.ReceiveAmountRequest
                .newBuilder()
                .setTransaction(transaction)
                .setAccept(accept)
                .setNonce(nextNonce.encode())
                .setSignature(Bank.Signature.newBuilder()
                        .setSignatureBytes(ByteString.copyFrom(Signatures.signReceiveAmountRequest(privateKey,
                            sourcePublicKey.getEncoded(),
                            destinationPublicKey.getEncoded(),
                            amount,
                            nextNonce.getBytes(),
                            accept)))
                        .build())
                .build();

        Bank.ReceiveAmountResponse response = stub.receiveAmount(request);

        if (response.getStatus() == Bank.ReceiveAmountResponse.Status.INVALID_MESSAGE_FORMAT)
            return response;

        if (!Signatures.verifyReceiveAmountResponseSignature(response.getSignature().getSignatureBytes().toByteArray(), serverPublicKey,
                response.getNonce().getNonceBytes().toByteArray(),
                response.getStatus().name()))
            throw new FailedAuthenticationException();

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

        if (response.getStatus() == Bank.CheckAccountResponse.Status.INVALID_MESSAGE_FORMAT)
            return response;

        if (!Signatures.verifyCheckAccountResponseSignature(response.getSignature().getSignatureBytes().toByteArray(), serverPublicKey,
                response.getChallengeNonce().getNonceBytes().toByteArray(),
                response.getStatus().name(),
                response.getBalance(),
                response.getTransactionsList()))
            throw new FailedAuthenticationException();

        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }

    public Bank.AuditResponse audit(PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.AuditRequest request = Bank.AuditRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setChallengeNonce(challengeNonce.encode())
                .build();

        Bank.AuditResponse response = stub.audit(request);

        if (response.getStatus() == Bank.AuditResponse.Status.INVALID_MESSAGE_FORMAT)
            return response;

        if (!Signatures.verifyAuditResponseSignature(response.getSignature().getSignatureBytes().toByteArray(), serverPublicKey,
                response.getChallengeNonce().getNonceBytes().toByteArray(),
                response.getStatus().name(),
                response.getTransactionsList()))
            throw new FailedAuthenticationException();

        if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
            throw new FailedChallengeException();

        return response;
    }


    private boolean isNextNonce(Nonce sent, Nonce received) {
        return sent.nextNonce().equals(received);
    }
}
