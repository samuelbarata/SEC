package pt.ulisboa.tecnico.sec.candeeiros.shared;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.KeyStore.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// source: https://www.nealgroothuis.name/import-a-private-key-into-a-java-keystore/
// source: https://www.javatpoint.com/java-keystore
public class KeyManager {
    private static final Logger logger = LoggerFactory.getLogger(KeyManager.class);

    private String keyStoreFile;
    private String keyFile;

    private KeyStore keystore;
    private final String alias;
    private final char[] keyPassword;
    private final char[] keyStorePassword;

    private static char[] defaultPassword = "0".toCharArray();

    public KeyManager(String keyfile) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException, UnrecoverableEntryException{
        this(keyfile, "keystore.ks", defaultPassword, defaultPassword, "private");
    }

    public KeyManager(String keyFile, String keyStoreFile ,char[] keyPassword, char[] keyStorePassword, String alias)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, UnrecoverableEntryException {
        keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        this.keyPassword = keyPassword;
        this.keyStorePassword = keyStorePassword;
        this.alias = alias;
        this.keyStoreFile = keyStoreFile;
        this.keyFile=keyFile;

        try(FileInputStream keyStoreData = new FileInputStream(keyStoreFile)){
            //open existing keystore
            logger.info("Importing KeyStore");
            keystore.load(keyStoreData, keyStorePassword);  
        } catch (IOException e) {
            //create new keypair
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            logger.info("Creating new RSA key pair");
            kpg.initialize(8192);
            KeyPair kp = kpg.generateKeyPair();

            //write publicKey to file
            byte[] rawPubKey = kp.getPublic().getEncoded();
            FileOutputStream keyfos = new FileOutputStream(keyFile);
            keyfos.write(rawPubKey);
            keyfos.close();
            

            X509Certificate cert;
            CertificateFactory fac = CertificateFactory.getInstance("X509");
            cert = (X509Certificate) fac.generateCertificate(new ByteArrayInputStream(rawPubKey));

            ProtectionParameter pp = new PasswordProtection(keyPassword);

            Certificate[] chain = {(Certificate)cert};

            PrivateKeyEntry privKeyEntry = new PrivateKeyEntry(kp.getPrivate(),chain);
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
