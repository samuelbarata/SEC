import org.junit.jupiter.api.*;
import pt.ulisboa.tecnico.sec.candeeiros.Bank.*;
import pt.ulisboa.tecnico.sec.candeeiros.client.BankClient;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.*;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.security.*;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BasicTest {
    private static BankClient client;
    private static PublicKey publicKey1, publicKey2, publicKey3, serverPublicKey;
    private static PrivateKey privateKey1, privateKey2, privateKey3;
    private static Nonce nonce1, nonce2;

    @BeforeAll
    static void startup() {
        String target = System.getProperty("target");
        serverPublicKey = (PublicKey) Crypto.readKeyOrExit(System.getProperty("serverPublicKey"), "pub");
        client = new BankClient(target, serverPublicKey);
        publicKey1 = (PublicKey) Crypto.readKeyOrExit("./keys/1/id.pub", "pub");
        publicKey2 = (PublicKey) Crypto.readKeyOrExit("./keys/2/id.pub", "pub");
        publicKey3 = (PublicKey) Crypto.readKeyOrExit("./keys/3/id.pub", "pub");
        privateKey1 = (PrivateKey) Crypto.readKeyOrExit("./keys/1/id", "private");
        privateKey2 = (PrivateKey) Crypto.readKeyOrExit("./keys/2/id", "private");
        privateKey3 = (PrivateKey) Crypto.readKeyOrExit("./keys/3/id", "private");
    }

    @Test
    @Order(1)
    void createAccountTest() throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        OpenAccountResponse openAccountResponse;
        CheckAccountResponse checkAccountResponse;

        // Create Account
        openAccountResponse = client.openAccount(privateKey1, publicKey1);
        assertEquals(OpenAccountResponse.Status.SUCCESS, openAccountResponse.getStatus());
        openAccountResponse = client.openAccount(privateKey2, publicKey2);
        assertEquals(OpenAccountResponse.Status.SUCCESS, openAccountResponse.getStatus());

        // Check Account
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1000", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());
    }

    @Test
    @Order(2)
    void sendAmountTest() throws FailedChallengeException, SignatureException, InvalidKeyException, FailedAuthenticationException, WrongNonceException, NoSuchAlgorithmException, InvalidKeySpecException {
        CheckAccountResponse checkAccountResponse;
        SendAmountResponse sendAmountResponse;
        NonceNegotiationResponse nonceNegotiationResponse;

        // Get Nonces
        nonceNegotiationResponse = client.nonceNegotiation(privateKey1, publicKey1);
        assertEquals(NonceNegotiationResponse.Status.SUCCESS, nonceNegotiationResponse.getStatus());
        nonce1 = Nonce.newNonce(nonceNegotiationResponse.getNonce().getNonceBytes().toByteArray());
        nonceNegotiationResponse = client.nonceNegotiation(privateKey2, publicKey2);
        assertEquals(NonceNegotiationResponse.Status.SUCCESS, nonceNegotiationResponse.getStatus());
        nonce2 = Nonce.newNonce(nonceNegotiationResponse.getNonce().getNonceBytes().toByteArray());

        // Send Amount
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "100", nonce1);
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());
        nonce1 = Nonce.decode(sendAmountResponse.getNonce());

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
        assertEquals(publicKey1, Crypto.decodePublicKey(checkAccountResponse.getTransactionsList().get(0).getTransaction().getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(checkAccountResponse.getTransactionsList().get(0).getTransaction().getDestinationPublicKey()));
        assertEquals("100", checkAccountResponse.getTransactionsList().get(0).getTransaction().getAmount());
    }

    @Test
    @Order(3)
    void acceptAmountTest() throws WrongNonceException, SignatureException, InvalidKeyException, FailedAuthenticationException, FailedChallengeException {
        CheckAccountResponse checkAccountResponse;
        ReceiveAmountResponse receiveAmountResponse;

        // Accept Transaction
        receiveAmountResponse = client.receiveAmount(privateKey2, publicKey1, publicKey2, "100", true, nonce2);
        assertEquals(ReceiveAmountResponse.Status.SUCCESS, receiveAmountResponse.getStatus());
        nonce2 = Nonce.decode(receiveAmountResponse.getNonce());

        // Check Destination Account
        checkAccountResponse = client.checkAccount(publicKey2);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1100", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());
    }

    @Test
    @Order(4)
    void rejectAmountTest() throws WrongNonceException, SignatureException, InvalidKeyException, FailedAuthenticationException, FailedChallengeException {
        CheckAccountResponse checkAccountResponse;
        SendAmountResponse sendAmountResponse;
        ReceiveAmountResponse receiveAmountResponse;

        // Send Amount
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "100", nonce1);
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());
        nonce1 = Nonce.decode(sendAmountResponse.getNonce());

        // Reject Transaction
        receiveAmountResponse = client.receiveAmount(privateKey2, publicKey1, publicKey2, "100", false, nonce2);
        assertEquals(ReceiveAmountResponse.Status.SUCCESS, receiveAmountResponse.getStatus());
        nonce2 = Nonce.decode(receiveAmountResponse.getNonce());

        // Check Accounts (expecting no change)
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("900", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());

        checkAccountResponse = client.checkAccount(publicKey2);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1100", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());


    }

    @Test
    @Order(5)
    void auditTest() throws FailedChallengeException, SignatureException, InvalidKeyException, FailedAuthenticationException, NoSuchAlgorithmException, InvalidKeySpecException {
        AuditResponse auditResponse;

        // Check both accounts audits
        auditResponse = client.audit(publicKey1);
        assertEquals(AuditResponse.Status.SUCCESS, auditResponse.getStatus());
        assertEquals(1, auditResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getDestinationPublicKey()));
        assertEquals("100", auditResponse.getTransactionsList().get(0).getTransaction().getAmount());

        auditResponse = client.audit(publicKey2);
        assertEquals(AuditResponse.Status.SUCCESS, auditResponse.getStatus());
        assertEquals(1, auditResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getDestinationPublicKey()));
        assertEquals("100", auditResponse.getTransactionsList().get(0).getTransaction().getAmount());
    }

    @Test
    @Order(6)
    void incorrectUsageTest() throws WrongNonceException, FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        OpenAccountResponse openAccountResponse;
        CheckAccountResponse checkAccountResponse;
        SendAmountResponse sendAmountResponse;
        ReceiveAmountResponse receiveAmountResponse;

        // Create accounts that already exist
        openAccountResponse = client.openAccount(privateKey1, publicKey1);
        assertEquals(OpenAccountResponse.Status.ALREADY_EXISTED, openAccountResponse.getStatus());

        // Check account that doesn't exist
        checkAccountResponse = client.checkAccount(publicKey3);
        assertEquals(CheckAccountResponse.Status.INVALID_KEY, checkAccountResponse.getStatus());

        // Send amount from/to account that doesn't exit or to self
        sendAmountResponse = client.sendAmount(privateKey3, publicKey3, publicKey1, "10", nonce1);
        assertEquals(SendAmountResponse.Status.SOURCE_INVALID, sendAmountResponse.getStatus());
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey3, "10", nonce1);
        assertEquals(SendAmountResponse.Status.DESTINATION_INVALID, sendAmountResponse.getStatus());
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey1, "10", nonce1);
        assertEquals(SendAmountResponse.Status.DESTINATION_INVALID, sendAmountResponse.getStatus());

        // Create transactions with invalid amounts
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "qwe", nonce1);
        assertEquals(SendAmountResponse.Status.INVALID_NUMBER_FORMAT, sendAmountResponse.getStatus());
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "-100", nonce1);
        assertEquals(SendAmountResponse.Status.INVALID_NUMBER_FORMAT, sendAmountResponse.getStatus());
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "0", nonce1);
        assertEquals(SendAmountResponse.Status.INVALID_NUMBER_FORMAT, sendAmountResponse.getStatus());

        // Send amount bigger than balance
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "10000", nonce1);
        assertEquals(SendAmountResponse.Status.NOT_ENOUGH_BALANCE, sendAmountResponse.getStatus());

        // Accept transaction that does not exist
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "100", nonce1);
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());
        nonce2 = Nonce.decode(sendAmountResponse.getNonce());

        receiveAmountResponse = client.receiveAmount(privateKey2, publicKey1, publicKey2, "200", true, nonce2);
        assertEquals(ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION, receiveAmountResponse.getStatus());
        receiveAmountResponse = client.receiveAmount(privateKey2, publicKey3, publicKey2, "100", true, nonce2);
        assertEquals(ReceiveAmountResponse.Status.NO_SUCH_TRANSACTION, receiveAmountResponse.getStatus());
        receiveAmountResponse = client.receiveAmount(privateKey3, publicKey1, publicKey3, "100", true, nonce2);
        assertEquals(ReceiveAmountResponse.Status.INVALID_KEY, receiveAmountResponse.getStatus());

        // Use wrong nonce
        receiveAmountResponse = client.receiveAmount(privateKey2, publicKey1, publicKey2, "100", true, nonce1);
        assertEquals(ReceiveAmountResponse.Status.INVALID_NONCE, receiveAmountResponse.getStatus());
    }

    @AfterAll
    static void cleanup() {
        client.shutdown();
    }
}