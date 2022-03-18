import org.junit.jupiter.api.Test;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.Bank.*;
import pt.ulisboa.tecnico.sec.candeeiros.client.BankClient;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

class ClientTest {
    private BankClient client;

    public ClientTest() {
        String target = System.getProperty("target");
        client = new BankClient(target);
    }

    private void openAccount(PublicKey publicKey) {
        System.out.printf("Attempting to open an account with public key %s%n",
                Crypto.keyAsShortString(publicKey));
        OpenAccountResponse response = client.openAccount(publicKey);
        System.out.printf("Received %s%n", response.getStatus().name());
    }

    private void sendAmount(PublicKey sourcePublicKey, PublicKey destinationPublicKey, String amount) {
        System.out.printf("Attempting to create a transaction from %s to %s with amount %s%n",
                Crypto.keyAsShortString(sourcePublicKey),
                Crypto.keyAsShortString(destinationPublicKey),
                amount);
        SendAmountResponse response = client.sendAmount(sourcePublicKey, destinationPublicKey, amount);
        System.out.printf("Received %s%n", response.getStatus().name());
    }

    private void checkAccount(PublicKey publicKey) {
        System.out.printf("Checking account with public key %s%n",
                Crypto.keyAsShortString(publicKey));
        CheckAccountResponse response = client.checkAccount(publicKey);
        System.out.printf("Received %s%n", response.getStatus().name());
        System.out.printf("Balance: %s%n", response.getBalance());
        System.out.println("Transactions:");
        for (Bank.Transaction t : response.getTransactionsList()) {
            try {
                System.out.printf("- %s -> %s - amount: %s%n",
                        Crypto.keyAsShortString(Crypto.decodePublicKey(t.getSourcePublicKey())),
                        Crypto.keyAsShortString(Crypto.decodePublicKey(t.getDestinationPublicKey())),
                        t.getAmount()
                );
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    void justAnExample() {
        PublicKey publicKey1 = (PublicKey) Crypto.readKeyOrExit("./keys/1/id.pub", "pub");
        PublicKey publicKey2 = (PublicKey) Crypto.readKeyOrExit("./keys/2/id.pub", "pub");

        // anonymous blocks to shadow response
        openAccount(publicKey1);
        openAccount(publicKey2);
        checkAccount(publicKey1);
        checkAccount(publicKey2);
        sendAmount(publicKey1, publicKey2, "123.45");
        checkAccount(publicKey1);
        checkAccount(publicKey2);
    }
}