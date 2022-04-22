package pt.ulisboa.tecnico.sec.candeeiros.client;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedAuthenticationException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedChallengeException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.WrongNonceException;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.KeyManager;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;
import pt.ulisboa.tecnico.sec.candeeiros.shared.exceptions.UnexistingKeyException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.Scanner;

public class InteractiveClient {
    public static void main(String args[]) throws CertificateException, IOException {
        final String host;
        final int port;
        final String target;
        final String keyStorePath;
        final String keyStorePassword;
        final String keyPassword;
        final String serverKeyPath;
        final String keyName;
        String privateKeyPath = null;
        String certificatePath = null;

        if (args.length == 9) {
            privateKeyPath = args[7];
            certificatePath = args[8];
        } else if (args.length != 7) {
            System.out.println("Not enough arguments!");
            return;
        }

        host = args[0];
        port = Integer.parseInt(args[1]);
        target = host + ":" + port;
        keyStorePath = args[2];
        keyStorePassword = args[3];
        keyName = args[4];
        keyPassword = args[5];
        serverKeyPath = args[6];

        KeyManager keyManager;

        try {
            System.out.println("Found key in keystore. Loading it...");
            keyManager = new KeyManager(keyName, keyStorePath, keyPassword.toCharArray(), keyStorePassword.toCharArray());
        } catch (UnexistingKeyException | IOException e) {
            if (privateKeyPath != null && certificatePath != null) {
                System.out.println("Key does not exist in keystore! Please provide private key and cert to add.");
                return;
            }
            System.out.println("Key not found in keystore. Adding it...");
            keyManager = new KeyManager(privateKeyPath, keyStorePath, keyPassword.toCharArray(),
                    keyStorePassword.toCharArray(), keyName, certificatePath);
        }

        InteractiveClient client = new InteractiveClient(target, keyManager, (PublicKey) Crypto.readKeyOrExit(serverKeyPath, "pub"));
        client.run();
    }

    private final BankClient client;
    private final PublicKey publicKey;
    private final KeyManager keyManager;
    private final Scanner scan;
    private Nonce nonce = null;

    public InteractiveClient(String target, KeyManager keyManager, PublicKey serverPublicKey) {
        this.keyManager = keyManager;
        this.publicKey = keyManager.getPublicKey();
        client = new BankClient(target, serverPublicKey);
        this.scan = new Scanner(System.in);
    }

    public void run() {
        while (true) {
            System.out.println("1. Open Account");
            System.out.println("2. Send Amount");
            System.out.println("3. Receive Amount");
            System.out.println("4. Check Account");
            System.out.println("5. Audit Account");
            System.out.println("6. Exit");
            System.out.print("> ");
            System.out.flush();

            int choice = scan.nextInt();
            // Consume \n
            scan.nextLine();
            switch (choice) {
                case 1:
                    openAccount();
                    break;
                case 2:
                    sendAmount();
                    break;
                case 3:
                    receiveAmount();
                    break;
                case 4:
                    checkAccount();
                    break;
                case 5:
                    audit();
                    break;
                case 6:
                    client.shutdown();
                    return;
                default:
                    System.out.println("Invalid choice");
            }
        }
    }

    private void negotiateNonce() throws FailedChallengeException, SignatureException, InvalidKeyException, FailedAuthenticationException {
        if (nonce != null)
            return;
        Bank.NonceNegotiationResponse response = client.nonceNegotiation(keyManager.getKey(), publicKey);
        nonce = Nonce.decode(response.getNonce());
    }

    private void openAccount() {
        try {
            Bank.OpenAccountResponse response = client.openAccount(keyManager.getKey(), publicKey);
            System.out.println("Response: " + response.getStatus().name());
        } catch (FailedChallengeException | SignatureException | InvalidKeyException | FailedAuthenticationException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void sendAmount() {
        try {
            System.out.println("Enter file with receiver public key");
            System.out.print("> ");
            System.out.flush();
            PublicKey receiverPublicKey = (PublicKey) Crypto.readKeyOrExit(scan.nextLine(), "pub");
            System.out.println("Enter the amount to send");
            System.out.print("> ");
            System.out.flush();
            int amount = scan.nextInt();
            // Consume \n
            scan.nextLine();

            negotiateNonce();
            Bank.SendAmountResponse response = client.sendAmount(keyManager.getKey(), publicKey, receiverPublicKey, Integer.toString(amount), nonce);

            nonce = Nonce.decode(response.getNonce());

            System.out.println("Response: " + response.getStatus().name());
        } catch (FailedChallengeException | SignatureException | InvalidKeyException | FailedAuthenticationException | WrongNonceException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void receiveAmount() {
        try {
            System.out.println("Enter file with sender public key");
            System.out.print("> ");
            System.out.flush();
            PublicKey senderPublicKey = (PublicKey) Crypto.readKeyOrExit(scan.nextLine(), "pub");
            System.out.println("Enter the amount to receive");
            System.out.print("> ");
            System.out.flush();
            int amount = scan.nextInt();
            // Consume \n
            scan.nextLine();

            negotiateNonce();
            Bank.ReceiveAmountResponse response = client.receiveAmount(keyManager.getKey(), senderPublicKey, publicKey, Integer.toString(amount), true, nonce);

            nonce = Nonce.decode(response.getNonce());

            System.out.println("Response: " + response.getStatus().name());
        } catch (WrongNonceException | FailedChallengeException | SignatureException  | InvalidKeyException | FailedAuthenticationException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void checkAccount() {
        try {
            System.out.println("Enter file with public key to check (empty for self)");
            System.out.print("> ");
            System.out.flush();
            String file = scan.nextLine();
            PublicKey keyToCheck;
            if (file.isEmpty()) {
                keyToCheck = publicKey;
            } else {
                keyToCheck = (PublicKey) Crypto.readKeyOrExit(file, "pub");
            }
            Bank.CheckAccountResponse response = client.checkAccount(keyToCheck);
            System.out.println("Response: " + response.getStatus().name());

            if (response.getStatus() == Bank.CheckAccountResponse.Status.SUCCESS) {
                System.out.println("Balance: " + response.getBalance());
                for (Bank.NonRepudiableTransaction transaction : response.getTransactionsList()) {
                    System.out.println("Transaction: " + transaction.getTransaction().getAmount() + " from " +
                            Crypto.keyAsShortString(Crypto.decodePublicKey(transaction.getTransaction().getSourcePublicKey())) + " to " +
                            Crypto.keyAsShortString(Crypto.decodePublicKey(transaction.getTransaction().getDestinationPublicKey())));
                }
            }
        } catch (FailedChallengeException | FailedAuthenticationException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Server sent misformatted publickey");
            e.printStackTrace();
        }
    }

    private void audit() {
        try {
            System.out.println("Enter file with public key to audit (empty for self)");
            System.out.print("> ");
            System.out.flush();
            String file = scan.nextLine();
            PublicKey keyToAudit;
            if (file.isEmpty()) {
                keyToAudit = publicKey;
            } else {
                keyToAudit = (PublicKey) Crypto.readKeyOrExit(file, "pub");
            }
            Bank.AuditResponse response = client.audit(keyToAudit);
            System.out.println("Response: " + response.getStatus().name());

            if (response.getStatus() == Bank.AuditResponse.Status.SUCCESS) {
                for (Bank.NonRepudiableTransaction transaction : response.getTransactionsList()) {
                    System.out.println("Transaction: " + transaction.getTransaction().getAmount() + " from " +
                            Crypto.keyAsShortString(Crypto.decodePublicKey(transaction.getTransaction().getSourcePublicKey())) + " to " +
                            Crypto.keyAsShortString(Crypto.decodePublicKey(transaction.getTransaction().getDestinationPublicKey())));
                }
            }
        } catch (FailedChallengeException | FailedAuthenticationException e) {
            System.out.println("Error: " + e.getMessage());
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            System.out.println("Server sent misformatted publickey");
            e.printStackTrace();
        }
    }
}
