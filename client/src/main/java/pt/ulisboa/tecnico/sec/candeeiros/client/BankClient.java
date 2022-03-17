package pt.ulisboa.tecnico.sec.candeeiros.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.BankServiceGrpc;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Scanner;

public class BankClient {
    private String target;
    private final ManagedChannel channel;
    private final BankServiceGrpc.BankServiceBlockingStub stub;

    public BankClient(String target) {
        this.target = target;
        this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        this.stub = BankServiceGrpc.newBlockingStub(channel);
    }

    public void shutdown() {
        channel.shutdown();
    }

    public void openAccount(PublicKey publicKey) {
        Bank.OpenAccountRequest request = Bank.OpenAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        System.out.printf("Attempting to open an account with public key %s%n",
                Crypto.keyAsShortString(publicKey));

        Bank.OpenAccountResponse response = stub.openAccount(request);

        System.out.printf("Received %s%n", response.getStatus().name());
    }

    public void sendAmount(PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount) {

        System.out.printf("Attempting to create a transaction from %s to %s with amount %s%n",
                Crypto.keyAsShortString(sourcePublicKey),
                Crypto.keyAsShortString(destinationPublicKey),
                amount);

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

        Bank.SendAmountResponse response = stub.sendAmount(request);

        System.out.printf("Received %s%n", response.getStatus().name());
    }

    public void checkAccount(String publicKeyFilename) {
        PublicKey publicKey;
        try {
            publicKey = (PublicKey) Crypto.readKey(publicKeyFilename, "pub");
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return;
        }

        System.out.printf("Checking account with key %s%n", Crypto.keyAsShortString(publicKey));

        Bank.CheckAccountRequest request = Bank.CheckAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        Bank.CheckAccountResponse response = stub.checkAccount(request);

        System.out.printf("Received %s%n", response.getStatus().name());

        for (Bank.Transaction t : response.getTransactionsList()) {
            try {
                System.out.printf("%s -> %s - amount: %s%n",
                        Crypto.keyAsShortString(Crypto.decodePublicKey(t.getSourcePublicKey())),
                        Crypto.keyAsShortString(Crypto.decodePublicKey(t.getDestinationPublicKey())),
                        t.getAmount()
                        );
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                e.printStackTrace();
            }
        }
    }
}
