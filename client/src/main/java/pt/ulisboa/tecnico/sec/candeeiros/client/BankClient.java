package pt.ulisboa.tecnico.sec.candeeiros.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import java.security.PublicKey;

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
}
