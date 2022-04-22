import org.junit.jupiter.api.*;
import pt.ulisboa.tecnico.sec.candeeiros.Bank.*;
import pt.ulisboa.tecnico.sec.candeeiros.client.ByzantineBankClient;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedAuthenticationException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedChallengeException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.WrongNonceException;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.KeyManager;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.security.PublicKey;
import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByzantineTest {
    private static ByzantineBankClient client;
    private static PublicKey publicKey1, publicKey2, publicKey3, serverPublicKey;
    private static PrivateKey privateKey1, privateKey2, privateKey3;
    private static ArrayList<KeyManager> km = new ArrayList<>();
    private static Nonce nonce1, nonce2;
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
        client = new ByzantineBankClient(target, serverPublicKey);
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
    void sendWrongSignatureTest()
            throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        OpenAccountResponse openAccountResponse;

        openAccountResponse = client.openAccountSendWrongSignature(privateKey1, publicKey1);
        assertEquals(OpenAccountResponse.Status.INVALID_SIGNATURE, openAccountResponse.getStatus());
    }

    @Test
    void receiveWrongSignatureTest() {
        OpenAccountResponse openAccountResponse;

        assertThrows(FailedAuthenticationException.class,
                () -> client.openAccountReceiveWrongSignature(privateKey1, publicKey1));
    }

    @Test
    void replayAttackTest()
            throws FailedAuthenticationException, SignatureException, FailedChallengeException, InvalidKeyException {

        // First request causes byzantine client to save response
        NonceNegotiationResponse response = client.nonceNegotiationReplayAttack(privateKey1, publicKey1);
        nonce1 = Nonce.decode(response.getNonce());

        // Second request gets the saved response instead of the actual response
        assertThrows(FailedChallengeException.class,
                () -> client.nonceNegotiationReplayAttack(privateKey1, publicKey1));
    }

    @Test
    void delayedReplayAttackTest()
            throws FailedAuthenticationException, WrongNonceException, SignatureException, InvalidKeyException {
        // First 2 executions should not fail
        client.sendAmountDelayedReplayAttack(privateKey1, publicKey1, publicKey2, "100", nonce1);
        nonce1 = nonce1.nextNonce();
        client.sendAmountDelayedReplayAttack(privateKey1, publicKey1, publicKey2, "100", nonce1);
        nonce1 = nonce1.nextNonce();

        // Third execution should fail nonce
        assertThrows(WrongNonceException.class,
                () -> client.sendAmountDelayedReplayAttack(privateKey1, publicKey1, publicKey2, "100", nonce1));

    }

    @AfterAll
    static void cleanup() {
        client.shutdown();
        File keystore = new File(keyStoreFile);
        keystore.delete();
    }
}