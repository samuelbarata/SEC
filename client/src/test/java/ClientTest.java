import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.Bank.*;
import pt.ulisboa.tecnico.sec.candeeiros.client.BankClient;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientTest {
    private BankClient client;
    PublicKey publicKey1, publicKey2;

    public ClientTest() {
        String target = System.getProperty("target");
        client = new BankClient(target);
        publicKey1 = (PublicKey) Crypto.readKeyOrExit("./keys/1/id.pub", "pub");
        publicKey2 = (PublicKey) Crypto.readKeyOrExit("./keys/2/id.pub", "pub");
    }

    @Test
    @Order(1)
    void correctUsage() throws NoSuchAlgorithmException, InvalidKeySpecException {
        OpenAccountResponse openAccountResponse;
        CheckAccountResponse checkAccountResponse;
        SendAmountResponse sendAmountResponse;

        // Create Account
        openAccountResponse = client.openAccount(publicKey1);
        assertEquals(OpenAccountResponse.Status.SUCCESS, openAccountResponse.getStatus());
        openAccountResponse = client.openAccount(publicKey2);
        assertEquals(OpenAccountResponse.Status.SUCCESS, openAccountResponse.getStatus());

        // Check Account
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1000", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsList().size());

        // Send Amount
        sendAmountResponse = client.sendAmount(publicKey1, publicKey2, "100");
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());

        // Check Source Account
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("900", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsList().size());

        // Check Destination Account
        checkAccountResponse = client.checkAccount(publicKey2);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1000", checkAccountResponse.getBalance());
        assertEquals(1, checkAccountResponse.getTransactionsList().size());
        assertEquals(publicKey1, Crypto.decodePublicKey(checkAccountResponse.getTransactionsList().get(0).getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(checkAccountResponse.getTransactionsList().get(0).getDestinationPublicKey()));
        assertEquals("100", checkAccountResponse.getTransactionsList().get(0).getAmount());
    }
}