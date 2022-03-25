package pt.ulisboa.tecnico.sec.candeeiros.client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedChallengeException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.WrongNonceException;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;

public class BankClient {
    private final ManagedChannel channel;
    private final BankServiceGrpc.BankServiceBlockingStub stub;

    public BankClient(String target) {
        this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        this.stub = BankServiceGrpc.newBlockingStub(channel);
    }

    public void shutdown() {
        channel.shutdown();
    }


    // ***** Authenticated procedures *****
    public Bank.OpenAccountResponse openAccount(PublicKey publicKey) throws FailedChallengeException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.OpenAccountRequest request = Bank.OpenAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setChallengeNonce(challengeNonce.encode())
                .build();

        Bank.OpenAccountResponse response = stub.openAccount(request);

        if (response.getStatus() == Bank.OpenAccountResponse.Status.SUCCESS)
            if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
                throw new FailedChallengeException();

        return response;
    }


    public Bank.NonceNegotiationResponse nonceNegotiation(PublicKey publicKey) throws FailedChallengeException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.NonceNegotiationRequest request = Bank.NonceNegotiationRequest.newBuilder()
                .setChallengeNonce(challengeNonce.encode())
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        Bank.NonceNegotiationResponse response = stub.nonceNegotiation(request);

        if (response.getStatus() == Bank.NonceNegotiationResponse.Status.SUCCESS)
            if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
                throw new FailedChallengeException();

        return response;
    }


    public Bank.SendAmountResponse sendAmount(PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount, Nonce nonce) throws WrongNonceException {
        Bank.Transaction transaction = Bank.Transaction
                .newBuilder()
                .setSourcePublicKey(Crypto.encodePublicKey(sourcePublicKey))
                .setDestinationPublicKey(Crypto.encodePublicKey(destinationPublicKey))
                .setAmount(amount)
                .build();


        Bank.SendAmountRequest request = Bank.SendAmountRequest
                .newBuilder()
                .setTransaction(transaction)
                .setNonce(nonce.nextNonce().encode())
                .build();

        Bank.SendAmountResponse response = stub.sendAmount(request);

        if (response.getStatus() == Bank.SendAmountResponse.Status.SUCCESS)
            if (!isNextNonce(nonce, Nonce.decode(response.getNonce())))
                throw new WrongNonceException();

        return response;
    }


    public Bank.ReceiveAmountResponse receiveAmount(PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount, boolean accept, Nonce nonce) throws WrongNonceException {
        Bank.Transaction transaction = Bank.Transaction
                .newBuilder()
                .setSourcePublicKey(Crypto.encodePublicKey(sourcePublicKey))
                .setDestinationPublicKey(Crypto.encodePublicKey(destinationPublicKey))
                .setAmount(amount)
                .build();

        Bank.ReceiveAmountRequest request = Bank.ReceiveAmountRequest
                .newBuilder()
                .setTransaction(transaction)
                .setAccept(accept)
                .setNonce(nonce.nextNonce().encode())
                .build();

        Bank.ReceiveAmountResponse response = stub.receiveAmount(request);

        if (response.getStatus() == Bank.ReceiveAmountResponse.Status.SUCCESS)
            if (!isNextNonce(nonce, Nonce.decode(response.getNonce())))
                throw new WrongNonceException();

        return response;
    }

    // ***** Unauthenticated procedures *****
    public Bank.CheckAccountResponse checkAccount(PublicKey publicKey) throws FailedChallengeException {
        Nonce challengeNonce = Nonce.newNonce();

        Bank.CheckAccountRequest request = Bank.CheckAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .setChallengeNonce(challengeNonce.encode())
                .build();

        Bank.CheckAccountResponse response = stub.checkAccount(request);

        if (response.getStatus() == Bank.CheckAccountResponse.Status.SUCCESS)
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

        if (response.getStatus() == Bank.AuditResponse.Status.SUCCESS)
            if (!challengeNonce.equals(Nonce.decode(response.getChallengeNonce())))
                throw new FailedChallengeException();

        return response;
    }


    private boolean isNextNonce(Nonce sent, Nonce received) {
        return sent.nextNonce().equals(received);
    }
}
