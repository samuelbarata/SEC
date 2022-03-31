import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;
import pt.ulisboa.tecnico.sec.candeeiros.client.BankClient;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.*;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PersistenceTest {
    private static BankClient client;
    private static PublicKey publicKey1, publicKey2, publicKey3, serverPublicKey;

    @BeforeAll
    static void startup() {
        String target = System.getProperty("target");
        serverPublicKey = (PublicKey) Crypto.readKeyOrExit(System.getProperty("serverPublicKey"), "pub");
        client = new BankClient(target, serverPublicKey);
        publicKey1 = (PublicKey) Crypto.readKeyOrExit("./keys/1/id.pub", "pub");
        publicKey2 = (PublicKey) Crypto.readKeyOrExit("./keys/2/id.pub", "pub");
        publicKey3 = (PublicKey) Crypto.readKeyOrExit("./keys/3/id.pub", "pub");
    }

    @Test
    void verifyPersistenceTest() throws NoSuchAlgorithmException, InvalidKeySpecException, FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        Bank.CheckAccountResponse checkAccountResponse;
        Bank.AuditResponse auditResponse;

        // Checking accounts balances
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(Bank.CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("500", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());

        checkAccountResponse = client.checkAccount(publicKey2);assertEquals(Bank.CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals(Bank.CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("1100", checkAccountResponse.getBalance());
        assertEquals(4, checkAccountResponse.getTransactionsCount());
        for (int i = 0; i < 4; i++) {
            assertEquals(publicKey1, Crypto.decodePublicKey(checkAccountResponse.getTransactionsList().get(i).getTransaction().getSourcePublicKey()));
            assertEquals(publicKey2, Crypto.decodePublicKey(checkAccountResponse.getTransactionsList().get(i).getTransaction().getDestinationPublicKey()));
            assertEquals("100", checkAccountResponse.getTransactionsList().get(i).getTransaction().getAmount());
            assertTrue(Signatures.verifyPendingTransactionSignature(checkAccountResponse.getTransactionsList().get(i)));
        }


        // Check both accounts audits
        auditResponse = client.audit(publicKey1);
        assertEquals(Bank.AuditResponse.Status.SUCCESS, auditResponse.getStatus());
        assertEquals(1, auditResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getDestinationPublicKey()));
        assertEquals("100", auditResponse.getTransactionsList().get(0).getTransaction().getAmount());
        assertTrue(Signatures.verifyAcceptedTransactionSignature(auditResponse.getTransactionsList().get(0)));

        auditResponse = client.audit(publicKey2);
        assertEquals(Bank.AuditResponse.Status.SUCCESS, auditResponse.getStatus());
        assertEquals(1, auditResponse.getTransactionsCount());
        assertEquals(publicKey1, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getSourcePublicKey()));
        assertEquals(publicKey2, Crypto.decodePublicKey(auditResponse.getTransactionsList().get(0).getTransaction().getDestinationPublicKey()));
        assertEquals("100", auditResponse.getTransactionsList().get(0).getTransaction().getAmount());
        assertTrue(Signatures.verifyAcceptedTransactionSignature(auditResponse.getTransactionsList().get(0)));
    }
}
