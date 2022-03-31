import org.junit.jupiter.api.*;
import pt.ulisboa.tecnico.sec.candeeiros.Bank.*;
import pt.ulisboa.tecnico.sec.candeeiros.client.ByzantineBankClient;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedAuthenticationException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.FailedChallengeException;
import pt.ulisboa.tecnico.sec.candeeiros.client.exceptions.WrongNonceException;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Signatures;

import java.security.PublicKey;
import java.security.*;
import java.security.spec.InvalidKeySpecException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ByzantineTest {
    private static ByzantineBankClient client;
    private static PublicKey publicKey1, publicKey2, publicKey3, serverPublicKey;
    private static PrivateKey privateKey1, privateKey2, privateKey3;
    private static Nonce nonce1, nonce2;

    @BeforeAll
    static void startup() {
        String target = System.getProperty("target");
        serverPublicKey = (PublicKey) Crypto.readKeyOrExit(System.getProperty("serverPublicKey"), "pub");
        client = new ByzantineBankClient(target, serverPublicKey);
        publicKey1 = (PublicKey) Crypto.readKeyOrExit("./keys/1/id.pub", "pub");
        publicKey2 = (PublicKey) Crypto.readKeyOrExit("./keys/2/id.pub", "pub");
        publicKey3 = (PublicKey) Crypto.readKeyOrExit("./keys/3/id.pub", "pub");
        privateKey1 = (PrivateKey) Crypto.readKeyOrExit("./keys/1/id", "private");
        privateKey2 = (PrivateKey) Crypto.readKeyOrExit("./keys/2/id", "private");
        privateKey3 = (PrivateKey) Crypto.readKeyOrExit("./keys/3/id", "private");
    }

    @Test
    void sendWrongSignatureTest() throws FailedChallengeException, FailedAuthenticationException, SignatureException, InvalidKeyException {
        OpenAccountResponse openAccountResponse;

        openAccountResponse = client.openAccountSendWrongSignature(privateKey1, publicKey1);
        assertEquals(OpenAccountResponse.Status.INVALID_SIGNATURE, openAccountResponse.getStatus());
    }

    @Test
    void receiveWrongSignatureTest() {
        OpenAccountResponse openAccountResponse;

        assertThrows(FailedAuthenticationException.class, () -> client.openAccountReceiveWrongSignature(privateKey1, publicKey1));
    }

    @Test
    void replayAttackTest() throws FailedAuthenticationException, SignatureException, FailedChallengeException, InvalidKeyException {

        // First request causes byzantine client to save response
        NonceNegotiationResponse response = client.nonceNegotiationReplayAttack(privateKey1, publicKey1);
        nonce1 = Nonce.decode(response.getNonce());

        // Second request gets the saved response instead of the actual response
        assertThrows(FailedChallengeException.class, () -> client.nonceNegotiationReplayAttack(privateKey1, publicKey1));
    }

    @Test
    void delayedReplayAttackTest() throws FailedAuthenticationException, WrongNonceException, SignatureException, InvalidKeyException {
        // First 2 executions should not fail
        client.sendAmountDelayedReplayAttack(privateKey1, publicKey1, publicKey2, "100", nonce1);
        nonce1 = nonce1.nextNonce();
        client.sendAmountDelayedReplayAttack(privateKey1, publicKey1, publicKey2, "100", nonce1);
        nonce1 = nonce1.nextNonce();

        // Third execution should fail nonce
        assertThrows(WrongNonceException.class, () -> client.sendAmountDelayedReplayAttack(privateKey1, publicKey1, publicKey2, "100", nonce1));

    }


    @AfterAll
    static void cleanup() {
        client.shutdown();
    }
}