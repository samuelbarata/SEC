package pt.ulisboa.tecnico.sec.candeeiros.shared;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

public class Crypto {
	private static final Logger logger = LoggerFactory.getLogger(Crypto.class);

	public static PublicKey keyFromString(String key) throws InvalidKeySpecException {
		try {
			return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(
					Base64.getDecoder().decode(key)
			));
		}  catch (NoSuchAlgorithmException e) {
			logger.error("Unreachable Block. No such algorithm RSA");
			e.printStackTrace();
			return null;
		}
	}

	public static PrivateKey privateKeyFromFileOrExit(String filename){
		byte[] keyBytes;
		try {
			keyBytes = Files.readAllBytes(Paths.get(filename));
			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
			return KeyFactory.getInstance("RSA").generatePrivate(spec);
		} catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
			logger.error("Failed to read private key from file {}", filename);
			e.printStackTrace();
			return null;
		}
	}

	public static String keyAsString(Key key) {
		return new String(Base64.getEncoder().encode(key.getEncoded()));
	}

	public static String keyAsShortString(Key key) {
		return new String(Base64.getEncoder().encode(key.getEncoded())).substring(45, 55);
	}

	public static Bank.PublicKey encodePublicKey(PublicKey publicKey) {
		return Bank.PublicKey.newBuilder().setKeyBytes(ByteString.copyFrom(publicKey.getEncoded())).build();
	}

	public static PublicKey decodePublicKey(Bank.PublicKey publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
		return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(publicKey.getKeyBytes().toByteArray()));
	}

	private static byte[] flatten(byte[][] first) {
        byte[] result = null;
        for (byte[] curr : first) {
            result = concat(result, curr);
        }
        return result;
    }
	private static byte[] concat(byte[] first, byte[] second) {
        if (first == null) {
            if (second == null) {
                return null;
            }
            return second;
        }
        if (second == null) {
            return first;
        }
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }


	public static byte[] sign(PrivateKey privateKey, byte[]... plaintext) throws SignatureException, InvalidKeyException {

		Signature privateSignature = null;
		try {
			privateSignature = Signature.getInstance("SHA256withRSA");
		} catch (NoSuchAlgorithmException e) {
			// Should never happen
			logger.error("Unreachable block: No such algorithm SHA256withRSA");
			e.printStackTrace();
		}
		privateSignature.initSign(privateKey);
		privateSignature.update(flatten(plaintext));
		return privateSignature.sign();
	}

	public static boolean verifySignature(PublicKey publicKey, byte[] signature ,  byte[]... plaintext) throws InvalidKeyException, SignatureException{
		Signature privateSignature = null;
		try {
			privateSignature = Signature.getInstance("SHA256withRSA");
		} catch (NoSuchAlgorithmException e) {
			// Should never happen
			logger.error("Unreachable block: No such algorithm SHA256withRSA");
			e.printStackTrace();
		}
		privateSignature.initVerify(publicKey);
		privateSignature.update(flatten(plaintext));
		return privateSignature.verify(signature);
	}
	

	public static Key readKeyOrExit(String keyPath, String type) {
		try {
			return readKey(keyPath, type);
		} catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | NullPointerException e) {
			logger.error("Could not read {} key in file {}:", type, keyPath);
			e.printStackTrace();
			System.exit(1);
			// For the compiler
			return null;
		}
	}

	public static Key readKey(String keyPath, String type) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		byte[] encoded;
		try (FileInputStream fis = new FileInputStream(keyPath)) {
			encoded = new byte[fis.available()];
			fis.read(encoded);
		}
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");
		if (type.equals("pub")) {
			X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
			return keyFactory.generatePublic(keySpec);
		}

		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
		return keyFactory.generatePrivate(keySpec);
	}

	public static X509Certificate readCertOrExit(String certPath) {
		try {
			CertificateFactory fac = CertificateFactory.getInstance("X509");
            FileInputStream is = new FileInputStream(certPath);
            return (X509Certificate) fac.generateCertificate(is);
		} catch (IOException | NullPointerException | CertificateException e) {
			logger.error("Invalid certificate in file {}:", certPath);
			e.printStackTrace();
			System.exit(1);
			// For the compiler
			return null;
		}
	}
}