package pt.ulisboa.tecnico.sec.candeeiros.client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import java.security.PublicKey;
import java.security.SecureRandom;

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

    public Bank.OpenAccountResponse openAccount(PublicKey publicKey) {
        Bank.OpenAccountRequest request = Bank.OpenAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        return stub.openAccount(request);
    }

    public Bank.SendAmountResponse sendAmount(PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount) {
        Bank.Transaction transaction = Bank.Transaction
                .newBuilder()
                .setSourcePublicKey(Crypto.encodePublicKey(sourcePublicKey))
                .setDestinationPublicKey(Crypto.encodePublicKey(destinationPublicKey))
                .setAmount(amount)
                .build();

        Bank.SendAmountRequest request = Bank.SendAmountRequest
                .newBuilder()
                .setTransaction(transaction)
                .build();

        return stub.sendAmount(request);
    }

    public Bank.ReceiveAmountResponse receiveAmount(PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount, boolean accept) {
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
                .build();

        return stub.receiveAmount(request);
    }

    public Bank.CheckAccountResponse checkAccount(PublicKey publicKey) {
        Bank.CheckAccountRequest request = Bank.CheckAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        return stub.checkAccount(request);
    }

    public Bank.AuditResponse audit(PublicKey publicKey) {
        Bank.AuditRequest request = Bank.AuditRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        return stub.audit(request);
    }

    public Bank.NonceNegotiationResponse nonceNegotiation(PublicKey publicKey) {
        SecureRandom sr = new SecureRandom();
        byte[] challenge = new byte[64];
        sr.nextBytes(challenge);

        Bank.NonceNegotiationRequest request = Bank.NonceNegotiationRequest.newBuilder()
                .setChallenge(ByteString.copyFrom(challenge))
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        Bank.NonceNegotiationResponse response = stub.nonceNegotiation(request);

        byte[] res = Crypto.challenge(challenge);
        for (int i = 0; i < res.length; i++) {
            System.out.println(res[i]);
            System.out.println(response.getChallenge().toByteArray()[i]);
        }

        if (!Crypto.challenge(challenge).equals(response.getChallenge().toByteArray())) {
            // TODO throw exception
            System.out.println("Challenge failed");
        }

        return response;
    }
}
