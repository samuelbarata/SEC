package pt.ulisboa.tecnico.sec.candeeiros.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedAuthenticationException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedChallengeException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.WrongNonceException;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.Scanner;

public class InteractiveClient {
    private final Logger logger = LoggerFactory.getLogger(InteractiveClient.class);
    private final PublicKey publicKey;
    private final PrivateKey privateKey;
    private final String target;
    private final BankClient client;
    private Nonce nonce = null;

    public InteractiveClient(String target, String privateKeyFile, String publicKeyFile, String serverPublicKeyFile) {
        this.target = target;
        this.publicKey = (PublicKey) Crypto.readKeyOrExit(publicKeyFile, "pub");
        this.privateKey = (PrivateKey) Crypto.readKeyOrExit(privateKeyFile, "private");
        client = new BankClient(target, (PublicKey) Crypto.readKeyOrExit(serverPublicKeyFile, "pub"));
    }

    private void negotiateNonce() throws FailedChallengeException, SignatureException, InvalidKeyException, FailedAuthenticationException {
        if (nonce != null)
            return;
        Bank.NonceNegotiationResponse response = client.nonceNegotiation(privateKey, publicKey);
        nonce = Nonce.decode(response.getNonce());
    }

    private Bank.OpenAccountResponse openAccount() throws FailedChallengeException, SignatureException, InvalidKeyException, FailedAuthenticationException {
        Bank.OpenAccountResponse response = client.openAccount(privateKey, publicKey);
        negotiateNonce();
        return response;
    }

    private Bank.SendAmountResponse sendAmount(PublicKey destination, String amount) throws WrongNonceException, FailedChallengeException, SignatureException, InvalidKeyException, FailedAuthenticationException {
        negotiateNonce();
        Bank.SendAmountResponse response = client.sendAmount(privateKey, publicKey, destination, amount, nonce);
        nonce = Nonce.decode(response.getNonce());
        return response;
    }

    private Bank.CheckAccountResponse checkAccount(PublicKey publicKey) throws FailedChallengeException, FailedAuthenticationException {
        Bank.CheckAccountResponse response = client.checkAccount(publicKey);
        return response;
    }

    public void run() {
        String line;
        Nonce nonce = null;

        while (true) {
            Scanner scan = new Scanner(System.in);

            System.out.print("Enter a command: ");
            line = scan.nextLine();
            String[] commandArgs = line.split(" ");
            switch (commandArgs[0]) {
                case "exit":
                    client.shutdown();
                    return;
                case "open_account":
                    try {
                        if (commandArgs.length != 1) {
                            System.out.println("Wrong number of arguments");
                        } else {
                            openAccount();
                        }
                    } catch (FailedChallengeException e) {
                        e.printStackTrace();
                    } catch (SignatureException e) {
                        e.printStackTrace();
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (FailedAuthenticationException e) {
                        e.printStackTrace();
                    }
                    break;
                case "send_amount":
                    if (commandArgs.length != 3) {
                        System.out.println("Not enough arguments");
                    } else {
                        try {
                            PublicKey destination = (PublicKey) Crypto.readKey(commandArgs[1], "pub");
                            sendAmount(destination, commandArgs[2]);
                        } catch (WrongNonceException | FailedChallengeException e) {
                            e.printStackTrace();
                        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
                            e.printStackTrace();
                        } catch (SignatureException e) {
                            e.printStackTrace();
                        } catch (InvalidKeyException e) {
                            e.printStackTrace();
                        } catch (FailedAuthenticationException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case "check_account":
                    if (commandArgs.length != 2) {
                        System.out.println("Not enough arguments");
                    } else {
                        try {
                            PublicKey account = (PublicKey) Crypto.readKey(commandArgs[1], "pub");
                            checkAccount(account);
                        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | FailedChallengeException | FailedAuthenticationException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                default:
                    System.out.println("Unknown Command");
            }
        }
    }

    public static void main(String[] args) {
        // check arguments
        if (args.length < 4) {
            System.err.println("Argument(s) missing!");
            return;
        }

        final String host = args[0];
        final int port = Integer.parseInt(args[1]);
        final String target = host + ":" + port;

        Scanner scan = new Scanner(System.in);

        System.out.println("Enter private key file location: ");
        String privateKeyFile = scan.nextLine();
        System.out.println("Enter public key file location: ");
        String publicKeyFile = scan.nextLine();
        System.out.println("Enter server public key file location: ");
        String serverPublicKeyFile = scan.nextLine();

        InteractiveClient client = new InteractiveClient(target, privateKeyFile, publicKeyFile, serverPublicKeyFile);
        client.run();
    }
}

