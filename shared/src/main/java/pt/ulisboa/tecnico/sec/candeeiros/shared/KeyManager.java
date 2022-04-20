package pt.ulisboa.tecnico.sec.candeeiros.shared;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.*;
import java.security.KeyStore.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// source: https://www.nealgroothuis.name/import-a-private-key-into-a-java-keystore/
// source: https://www.javatpoint.com/java-keystore
public class KeyManager {
    private static final Logger logger = LoggerFactory.getLogger(KeyManager.class);

    private String keyStoreFile;
    private String keyFile;
    private String certFile;

    private KeyStore keystore;
    private final String alias;
    private final char[] keyPassword;
    private final char[] keyStorePassword;

    private static char[] defaultPassword = "0".toCharArray();

    public KeyManager(String keyFile, String keyStoreFile ,char[] keyPassword, char[] keyStorePassword, String alias, String certFile)
            throws Exception {
        keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        this.keyPassword = keyPassword;
        this.keyStorePassword = keyStorePassword;
        this.alias = alias;
        this.keyStoreFile = keyStoreFile;
        this.keyFile = keyFile;
        this.certFile = certFile;

        try(FileInputStream keyStoreData = new FileInputStream(keyStoreFile)){
            //open existing keystore
            logger.info("Importing KeyStore");
            keystore.load(keyStoreData, keyStorePassword);  
        } catch (IOException e) {
            //importing new keypair
            keystore.load(null, keyStorePassword);
            logger.info("importing new RSA key pair");

            PrivateKey pk = (PrivateKey) Crypto.privateKeyFromFile(keyFile);
            
            CertificateFactory fac = CertificateFactory.getInstance("X509");
            FileInputStream is = new FileInputStream(certFile);
            X509Certificate cert = (X509Certificate) fac.generateCertificate(is);

            ProtectionParameter pp = new PasswordProtection(keyPassword);

            Certificate[] chain = {(Certificate)cert};

            PrivateKeyEntry privKeyEntry = new PrivateKeyEntry(pk,chain);
            keystore.setEntry(alias, privKeyEntry, pp);
            
            saveKeystore();
        }
    }

    private void saveKeystore() throws FileNotFoundException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException{
        logger.info("Saving KeyStore");
        try (FileOutputStream keyStoreOutputStream = new FileOutputStream(keyStoreFile)) {  
            keystore.store(keyStoreOutputStream, keyStorePassword);  
        }  
    }

    public PrivateKey getKey() throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        return (PrivateKey) keystore.getKey(alias, keyPassword);
    }

}
