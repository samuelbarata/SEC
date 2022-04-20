package pt.ulisboa.tecnico.sec.candeeiros.shared;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.KeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

// source: https://www.nealgroothuis.name/import-a-private-key-into-a-java-keystore/
// source: https://www.javatpoint.com/java-keystore
public class KeyManager {
    private String keyStoreFile;
    private String keyFile;

    private KeyStore keystore;
    private final String alias;
    private final char[] keyPassword;
    private final char[] keyStorePassword;

    private static char[] emptyCharArray = {};

    public KeyManager(String keyfile) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException{
        this(keyfile, "keystore.ks", emptyCharArray, emptyCharArray, "private");
    }

    public KeyManager(String keyFile, String keyStoreFile ,char[] keyPassword, char[] keyStorePassword, String alias)
            throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException {
        keystore = KeyStore.getInstance("RSA");
        this.keyPassword = keyPassword;
        this.keyStorePassword = keyStorePassword;
        this.alias = alias;
        this.keyStoreFile = keyStoreFile;
        this.keyFile=keyFile;

        try(FileInputStream keyStoreData = new FileInputStream(keyStoreFile)){
            //open existing keystore
            keystore.load(keyStoreData, keyStorePassword);  
        } catch (IOException e) {
            //import RSA key to keystore
            /*FileInputStream certificateStream = new FileInputStream(certificateFile);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

            java.security.cert.Certificate[] chain = {};
            chain = certificateFactory.generateCertificates(certificateStream).toArray(chain);
            certificateStream.close();*/

            PrivateKey privateKey = (PrivateKey) Crypto.readKeyOrExit(keyFile, "private");
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPrivateKeySpec privSpec = kf.getKeySpec(privateKey, RSAPrivateKeySpec.class);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(privSpec);
            KeyPair kp = new KeyPair(publicKey, privateKey);

            CertificateFactory fac = CertificateFactory.getInstance("X509");
            Certificate selfSignedCertificate = fac.generateCertificate(publicKey.toString());
            Certificate[] certificateChain = new Certificate[]{selfSignedCertificate};

            keystore.setEntry(alias, new KeyStore.PrivateKeyEntry(privateKey, chain),
                    new KeyStore.PasswordProtection(keyPassword));

            saveKeystore();
        }
    }

    private void saveKeystore() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        try (FileOutputStream keyStoreOutputStream = new FileOutputStream(keyStoreFile)) {  
            keystore.store(keyStoreOutputStream, keyStorePassword);  
        }  
    }

    public PrivateKey getKey() throws UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException {
        return (PrivateKey) keystore.getKey(alias, keyPassword);
    }

}
