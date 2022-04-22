import org.junit.jupiter.api.*;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.client.BankClient;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedAuthenticationException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedChallengeException;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.KeyManager;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrashTest {
    private static BankClient client;
    private static PublicKey publicKey1, publicKey2, publicKey3, serverPublicKey;
    private static ArrayList<KeyManager> km = new ArrayList<>();
    private static String keyStoreFile = "testsKeyStore.ts";

    private void lightSwitchProtection() throws InterruptedException{
        Thread.sleep(1000);
    }

    @AfterEach
    void sleepper() throws InterruptedException {
        lightSwitchProtection();
    }

    @BeforeAll
    static void startup() {
        String target = System.getProperty("target");
        serverPublicKey = (PublicKey) Crypto.readKeyOrExit(System.getProperty("serverPublicKey"), "pub");
        client = new BankClient(target, serverPublicKey);
        char[] keyStorePassword = "0".toCharArray();
        char[] keyPassword = "0".toCharArray();

        for (int i = 01; i < 9; i++) {
            try {
                km.add(new KeyManager("./keys/" + Integer.toString(i) + "/private_key.der", keyStoreFile, keyPassword,
                        keyStorePassword,
                        "testClient" + Integer.toString(i), "./keys/" + Integer.toString(i) + "/certificate.crt"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        publicKey1 = km.get(0).getPublicKey();
        publicKey2 = km.get(1).getPublicKey();
        publicKey3 = km.get(2).getPublicKey();
    }

    @Test
    void verifyCrashStateTest() throws NoSuchAlgorithmException, InvalidKeySpecException, FailedChallengeException,
            FailedAuthenticationException, SignatureException, InvalidKeyException {
        Bank.CheckAccountResponse checkAccountResponse;
        Bank.AuditResponse auditResponse;

        // Checking accounts balances
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(Bank.CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("600", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());

        checkAccountResponse = client.checkAccount(publicKey2);
        assertEquals(Bank.CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals(Bank.CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1100", checkAccountResponse.getBalance());
        assertEquals(3, checkAccountResponse.getTransactionsCount());
        for (int i = 0; i < 3; i++) {
            assertEquals(publicKey1, Crypto.decodePublicKey(
                    checkAccountResponse.getTransactionsList().get(i).getTransaction().getSourcePublicKey()));
            assertEquals(publicKey2, Crypto.decodePublicKey(
                    checkAccountResponse.getTransactionsList().get(i).getTransaction().getDestinationPublicKey()));
            assertEquals("100", checkAccountResponse.getTransactionsList().get(i).getTransaction().getAmount());
            assertTrue(Signatures.verifyPendingTransactionSignature(checkAccountResponse.getTransactionsList().get(i)));
        }

        // Check both accounts audits
        auditResponse = client.audit(publicKey1);
        assertEquals(Bank.AuditResponse.Status.SUCCESS, auditResponse.getStatus());
        assertEquals(1, auditResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto
                .decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(
                auditResponse.getTransactionsList().get(0).getTransaction().getDestinationPublicKey()));
        assertEquals("100", auditResponse.getTransactionsList().get(0).getTransaction().getAmount());
        assertTrue(Signatures.verifyAcceptedTransactionSignature(auditResponse.getTransactionsList().get(0)));

        auditResponse = client.audit(publicKey2);
        assertEquals(Bank.AuditResponse.Status.SUCCESS, auditResponse.getStatus());
        assertEquals(1, auditResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto
                .decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(
                auditResponse.getTransactionsList().get(0).getTransaction().getDestinationPublicKey()));
        assertEquals("100", auditResponse.getTransactionsList().get(0).getTransaction().getAmount());
        assertTrue(Signatures.verifyAcceptedTransactionSignature(auditResponse.getTransactionsList().get(0)));
    }

    @AfterAll
    static void cleanup() {
        client.shutdown();
        File keystore = new File(keyStoreFile);
        keystore.delete();
    }
}
