import org.junit.jupiter.api.*;
import pt.ulisboa.tecnico.sec.candeeiros.Bank.*;
import pt.ulisboa.tecnico.sec.candeeiros.client.BankClient;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BasicTest {
    private static BankClient client;
    private static PublicKey publicKey1, publicKey2, publicKey3;

    @BeforeAll
    static void startup() {
        String target = System.getProperty("target");
        client = new BankClient(target);
        publicKey1 = (PublicKey) Crypto.readKeyOrExit("./keys/1/id.pub", "pub");
        publicKey2 = (PublicKey) Crypto.readKeyOrExit("./keys/2/id.pub", "pub");
        publicKey3 = (PublicKey) Crypto.readKeyOrExit("./keys/3/id.pub", "pub");
    }

    @Test
    @Order(1)
    void correctUsageTest() throws NoSuchAlgorithmException, InvalidKeySpecException {
        OpenAccountResponse openAccountResponse;
        CheckAccountResponse checkAccountResponse;
        SendAmountResponse sendAmountResponse;
        ReceiveAmountResponse receiveAmountResponse;
        AuditResponse auditResponse;
        NonceNegotiationResponse nonceNegotiationResponse;
        Nonce nonce1, nonce2;

        // Create Account
        openAccountResponse = client.openAccount(publicKey1);
        assertEquals(OpenAccountResponse.Status.SUCCESS, openAccountResponse.getStatus());
        openAccountResponse = client.openAccount(publicKey2);
        assertEquals(OpenAccountResponse.Status.SUCCESS, openAccountResponse.getStatus());

        // Get Nonces
        nonceNegotiationResponse = client.nonceNegotiation(publicKey1);
        assertEquals(NonceNegotiationResponse.Status.SUCCESS, nonceNegotiationResponse.getStatus());
        nonce1 = Nonce.newNonce(nonceNegotiationResponse.getNonce().getNonceBytes().toByteArray());
        nonceNegotiationResponse = client.nonceNegotiation(publicKey2);
        assertEquals(NonceNegotiationResponse.Status.SUCCESS, nonceNegotiationResponse.getStatus());
        nonce2 = Nonce.newNonce(nonceNegotiationResponse.getNonce().getNonceBytes().toByteArray());

        // Check Account
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1000", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());

        // Send Amount
        sendAmountResponse = client.sendAmount(publicKey1, publicKey2, "100");
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());

        // Check Source Account
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("900", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());

        // Check Destination Account
        checkAccountResponse = client.checkAccount(publicKey2);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1000", checkAccountResponse.getBalance());
        assertEquals(1, checkAccountResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto.decodePublicKey(checkAccountResponse.getTransactionsList().get(0).getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(checkAccountResponse.getTransactionsList().get(0).getDestinationPublicKey()));
        assertEquals("100", checkAccountResponse.getTransactionsList().get(0).getAmount());

        // Accept Transaction
        receiveAmountResponse = client.receiveAmount(publicKey1, publicKey2, "100", true);
        assertEquals(ReceiveAmountResponse.Status.SUCCESS, receiveAmountResponse.getStatus());

        // Check Destination Account
        checkAccountResponse = client.checkAccount(publicKey2);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1100", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());

        // Send Amount
        sendAmountResponse = client.sendAmount(publicKey1, publicKey2, "100");
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());

        // Reject Transaction
        receiveAmountResponse = client.receiveAmount(publicKey1, publicKey2, "100", false);
        assertEquals(ReceiveAmountResponse.Status.SUCCESS, receiveAmountResponse.getStatus());

        // Check Accounts (expecting no change)
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("900", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());

        checkAccountResponse = client.checkAccount(publicKey2);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1100", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());

        // Check both accounts audits
        auditResponse = client.audit(publicKey1);
        assertEquals(AuditResponse.Status.SUCCESS, auditResponse.getStatus());
        assertEquals(1, auditResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getDestinationPublicKey()));
        assertEquals("100", auditResponse.getTransactionsList().get(0).getAmount());

        auditResponse = client.audit(publicKey2);
        assertEquals(AuditResponse.Status.SUCCESS, auditResponse.getStatus());
        assertEquals(1, auditResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getDestinationPublicKey()));
        assertEquals("100", auditResponse.getTransactionsList().get(0).getAmount());
    }

    @Test
    @Order(2)
    void incorrectUsageTest() {
        OpenAccountResponse openAccountResponse;
        CheckAccountResponse checkAccountResponse;
        SendAmountResponse sendAmountResponse;
        ReceiveAmountResponse receiveAmountResponse;

        // Create accounts that already exist
        openAccountResponse = client.openAccount(publicKey1);
        assertEquals(OpenAccountResponse.Status.ALREADY_EXISTED, openAccountResponse.getStatus());

        // Check account that doesn't exist
        checkAccountResponse = client.checkAccount(publicKey3);
        assertEquals(CheckAccountResponse.Status.INVALID_KEY, checkAccountResponse.getStatus());

        // Send amount from/to account that doesn't exit or to self
        sendAmountResponse = client.sendAmount(publicKey3, publicKey1, "10");
        assertEquals(SendAmountResponse.Status.SOURCE_INVALID, sendAmountResponse.getStatus());
        sendAmountResponse = client.sendAmount(publicKey1, publicKey3, "10");
        assertEquals(SendAmountResponse.Status.DESTINATION_INVALID, sendAmountResponse.getStatus());
        sendAmountResponse = client.sendAmount(publicKey1, publicKey1, "10");
        assertEquals(SendAmountResponse.Status.DESTINATION_INVALID, sendAmountResponse.getStatus());

        // Create transactions with invalid amounts
        sendAmountResponse = client.sendAmount(publicKey1, publicKey2, "qwe");
        assertEquals(SendAmountResponse.Status.INVALID_NUMBER_FORMAT, sendAmountResponse.getStatus());
        sendAmountResponse = client.sendAmount(publicKey1, publicKey2, "-100");
        assertEquals(SendAmountResponse.Status.INVALID_NUMBER_FORMAT, sendAmountResponse.getStatus());
        sendAmountResponse = client.sendAmount(publicKey1, publicKey2, "0");
        assertEquals(SendAmountResponse.Status.INVALID_NUMBER_FORMAT, sendAmountResponse.getStatus());

        // Send amount bigger than balance
        sendAmountResponse = client.sendAmount(publicKey1, publicKey2, "10000");
        assertEquals(SendAmountResponse.Status.NOT_ENOUGH_BALANCE, sendAmountResponse.getStatus());

        // Accept transaction that does not exist
        sendAmountResponse = client.sendAmount(publicKey1, publicKey2, "100");
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());

        receiveAmountResponse = client.receiveAmount(publicKey1, publicKey2, "200", true);
        assertEquals(ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION, receiveAmountResponse.getStatus());
        receiveAmountResponse = client.receiveAmount(publicKey3, publicKey2, "100", true);
        assertEquals(ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION, receiveAmountResponse.getStatus());
        receiveAmountResponse = client.receiveAmount(publicKey1, publicKey3, "100", true);
        assertEquals(ReceiveAmountResponse.Status.INVALID_KEY, receiveAmountResponse.getStatus());
    }

    @AfterAll
    static void cleanup() {
        client.shutdown();
    }
}