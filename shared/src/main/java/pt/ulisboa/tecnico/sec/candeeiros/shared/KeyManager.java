package pt.ulisboa.tecnico.sec.candeeiros.shared;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.KeyStore.*;
import java.security.cert.*;
import java.security.cert.Certificate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyManager {
    private static final Logger logger = LoggerFactory.getLogger(KeyManager.class);

    private String keyStoreFile;

    private KeyStore keystore;
    private final String alias;
    private final char[] keyPassword;
    private final char[] keyStorePassword;

    public KeyManager(String keyFile, String keyStoreFile, char[] keyPassword, char[] keyStorePassword, String alias,
            String certFile) throws FileNotFoundException, IOException {
        try {
            keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e1) {
            logger.error("KeyStoreException {}:", e1.getMessage());
            e1.printStackTrace();
            System.exit(1);
        }
        this.keyPassword = keyPassword;
        this.keyStorePassword = keyStorePassword;
        this.alias = alias;
        this.keyStoreFile = keyStoreFile;
        boolean loadNewKey = false;

        try (FileInputStream keyStoreData = new FileInputStream(keyStoreFile)) {
            // open existing keystore
            logger.info("Importing Existing KeyStore");
            try {
                keystore.load(keyStoreData, keyStorePassword);
                loadNewKey = !keystore.containsAlias(alias);
            } catch (NoSuchAlgorithmException | CertificateException | KeyStoreException e1) {
                logger.error("KeyStoreException {}:", e1.getMessage());
                e1.printStackTrace();
                System.exit(1);
            }
        } catch (IOException e) {
            // importing new keypair
            logger.info("Creating new KeyStore");
            try {
                keystore.load(null, keyStorePassword);
                loadNewKey = true;
            } catch (NoSuchAlgorithmException | CertificateException | IOException e1) {
                logger.error("KeyStoreException {}:", e1.getMessage());
                e1.printStackTrace();
                System.exit(1);
            }
        }
        if (loadNewKey) {
            logger.info("importing new RSA key pair into KeyStore");
            PrivateKey pk = (PrivateKey) Crypto.privateKeyFromFileOrExit(keyFile);

            X509Certificate cert = Crypto.readCertOrExit(certFile);

            ProtectionParameter pp = new PasswordProtection(keyPassword);

            Certificate[] chain = { (Certificate) cert };

            PrivateKeyEntry privKeyEntry = new PrivateKeyEntry(pk, chain);
            try {
                keystore.setEntry(alias, privKeyEntry, pp);
            } catch (KeyStoreException e1) {
                logger.error("KeyStoreException {}:", e1.getMessage());
                e1.printStackTrace();
                System.exit(1);
            }
            saveKeystore();
        }
    }

    private void saveKeystore() throws FileNotFoundException, IOException {
        logger.info("Saving KeyStore");
        try (FileOutputStream keyStoreOutputStream = new FileOutputStream(keyStoreFile)) {
            try {
                keystore.store(keyStoreOutputStream, keyStorePassword);
            } catch (NoSuchAlgorithmException nsae) {
                logger.error("Unreachable Block. No such algorithm {}:", nsae.getMessage());
                nsae.printStackTrace();
                System.exit(1);
            } catch (CertificateException cee) {
                logger.error("Certificate Exception {}:", cee.getMessage());
                cee.printStackTrace();
                System.exit(1);
            }
        } catch (KeyStoreException kse) {
            logger.error("KeyStoreException {}:", kse.getMessage());
            kse.printStackTrace();
            System.exit(1);
        }
    }

    public PrivateKey getKey() {
        try {
            return (PrivateKey) keystore.getKey(alias, keyPassword);
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            logger.error("Failed to get key {}:", e.getMessage());
            e.printStackTrace();
            System.exit(1);
            // For the compiler
            return null;
        }
    }
}
