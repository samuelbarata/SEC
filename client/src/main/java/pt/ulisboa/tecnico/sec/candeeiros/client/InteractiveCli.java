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

public class InteractiveCli {
    private Scanner scan;
    private PublicKey publicKey;
    private PrivateKey privateKey;
    private String target;
    private final ManagedChannel channel;
    private final BankServiceGrpc.BankServiceBlockingStub stub;

    public InteractiveCli(String target) {
        this.scan = new Scanner(System.in);
        this.target = target;
        this.channel = ManagedChannelBuilder.forTarget(target).usePlaintext().build();
        this.stub = BankServiceGrpc.newBlockingStub(channel);
    }

    private void openAccount() {
        Bank.OpenAccountRequest request = Bank.OpenAccountRequest.newBuilder()
                .setPublicKey(Crypto.encodePublicKey(publicKey))
                .build();

        System.out.printf("Attempting to open an account with public key %s%n",
                Crypto.keyAsShortString(publicKey));

        Bank.OpenAccountResponse response = stub.openAccount(request);

        System.out.printf("Received %s%n", response.getStatus().name());
    }

    private void sendAmount(String otherPublicKeyFilename, String amount) {
        PublicKey other;
        try {
            other = (PublicKey) Crypto.readKey(otherPublicKeyFilename, "pub");
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return;
        }
        System.out.printf("Attempting to create a transaction from %s to %s with amount %s%n",
                Crypto.keyAsShortString(publicKey),
                Crypto.keyAsShortString(other),
                amount);

        Bank.Transaction transaction = Bank.Transaction
                .newBuilder()
                .setSourcePublicKey(Crypto.encodePublicKey(publicKey))
                .setDestinationPublicKey(Crypto.encodePublicKey(other))
                .setAmount(amount)
                .build();

        Bank.SendAmountRequest request = Bank.SendAmountRequest
                .newBuilder()
                .setTransaction(transaction)
                .build();

        Bank.SendAmountResponse response = stub.sendAmount(request);

        System.out.printf("Received %s%n", response.getStatus().name());
    }

    private void checkAccount(String publicKeyFilename) {
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

    public void run() {
        System.out.println("Enter private key file location: ");
        String privateKeyFile = scan.nextLine();
        System.out.println("Enter public key file location: ");
        String publicKeyFile = scan.nextLine();

        publicKey = (PublicKey) Crypto.readKeyOrExit(publicKeyFile, "pub");
        privateKey = (PrivateKey) Crypto.readKeyOrExit(privateKeyFile, "private");

        loop();
    }

    public void loop() {
        String line;
        while (true) {
            System.out.print("Enter a command: ");
            line = scan.nextLine();
            String[] args = line.split(" ");
            switch (args[0]) {
                case "exit":
                    channel.shutdownNow();
                    return;
                case "open_account":
                    openAccount();
                    break;
                case "send_amount":
                    sendAmount(args[1], args[2]);
                    break;
                case "check_account":
                    checkAccount(args[1]);
                    break;
                default:
                    System.out.println("Unknown Command");
            }
        }
    }
}
