import org.junit.jupiter.api.*;
import pt.ulisboa.tecnico.sec.candeeiros.Bank.*;
import pt.ulisboa.tecnico.sec.candeeiros.client.BankClient;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.*;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.KeyManager;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DOSTest {
    private static BankClient client;
    private static PublicKey publicKey1, publicKey2, publicKey3, serverPublicKey;
    private static PrivateKey privateKey1, privateKey2, privateKey3;
    private static ArrayList<KeyManager> km = new ArrayList<>();
    private static Nonce nonce1, nonce2;
    private static String keyStoreFile = "testsKeyStore.ts";

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
        privateKey1 = km.get(0).getKey();
        privateKey2 = km.get(1).getKey();
        privateKey3 = km.get(2).getKey();
    }

    @Test
    @Order(1)
    void dosTest()
            throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException, InterruptedException, WrongNonceException {
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

        // Send Ammount
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "1", nonce1);
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());
        nonce1 = Nonce.decode(sendAmountResponse.getNonce());
        checkAccountResponse = client.checkAccount(publicKey1);
        assertEquals(CheckAccountResponse.Status.SUCCESS, checkAccountResponse.getStatus());
        assertEquals("599", checkAccountResponse.getBalance());
        assertEquals(0, checkAccountResponse.getTransactionsCount());
        
        //DOS Attack
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "1", nonce1);
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());
        nonce1 = Nonce.decode(sendAmountResponse.getNonce());
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "1", nonce1);
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());
        nonce1 = Nonce.decode(sendAmountResponse.getNonce());
        sendAmountResponse = client.sendAmount(privateKey1, publicKey1, publicKey2, "1", nonce1);
        assertEquals(SendAmountResponse.Status.SUCCESS, sendAmountResponse.getStatus());
        nonce1 = Nonce.decode(sendAmountResponse.getNonce());
    }

    @AfterAll
    static void cleanup() {
        client.shutdown();
        File keystore = new File(keyStoreFile);
        keystore.delete();
    }
}