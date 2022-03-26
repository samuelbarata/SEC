package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.io.*;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

public class LedgerManager {
    private static final Logger logger = LoggerFactory.getLogger(LedgerManager.class);
    private final BftBank bank;
    private final String ledgerFileName;
    private final BufferedWriter ledgerWriter;

    public LedgerManager(BftBank bank, String ledgerFileName) throws IOException {
        this.bank = bank;
        this.ledgerFileName = ledgerFileName;
        this.ledgerWriter = new BufferedWriter(new FileWriter(ledgerFileName, true));
    }

    public void parseLedger() throws IOException {
        new File(ledgerFileName).createNewFile(); // Will create file if it doesn't exist
        StringBuilder line = new StringBuilder();
        int count = 0;
        try (FileReader fr = new FileReader(ledgerFileName)) {
            BufferedReader br = new BufferedReader(fr);
            int c;
            // read instead of readline to not parse last line if it is not finished by a \n
            while ((c = br.read()) != -1) {
                line.append((char) c);
                if (c == '\n') {
                    count++;
                    parseLine(line.toString());
                    // Avoid generating new objects with new StringBuilder for each line
                    line.setLength(0);
                }
            }
        }
        if (line.length() != 0) {
            logger.warn("Last line does not have line feed. Assuming it is corrupted. Fixing");
            try (RandomAccessFile f = new RandomAccessFile(ledgerFileName, "rw")) {
                f.setLength(f.length() - line.length());
            }
        }
        logger.info("Read {} lines from ledger file", count);
    }

    private void parseLine(String line) {
        line = line.substring(0, line.length() - 1);
        String[] args = line.split("-");
        switch (args[0]) {
            case "create":
                if (args.length != 3) {
                    logger.error("Invalid line in ledger: {}", line);
                    System.exit(1);
                }
                try {
                    bank.createAccountWithNonce(Crypto.keyFromString(args[1]), Nonce.fromString(args[2]));
                } catch (InvalidKeySpecException e) {
                    logger.error("Invalid line in ledger: {}", line);
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            case "add":
                if (args.length != 6) {
                    logger.error("Invalid line in ledger: {}", line);
                    System.exit(1);
                }
                try {
                    bank.addTransactionNoLog(Crypto.keyFromString(args[1]), Crypto.keyFromString(args[2]), new BigDecimal(args[3]), Nonce.fromString(args[4]), signatureFromString(args[5]));
                } catch (InvalidKeySpecException e) {
                    logger.error("Invalid line in ledger: {}", line);
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            case "accept":
                if (args.length != 6) {
                    logger.error("Invalid line in ledger: {}", line);
                    System.exit(1);
                }
                try {
                    bank.acceptTransactionNoLog(Crypto.keyFromString(args[1]), Crypto.keyFromString(args[2]), new BigDecimal(args[3]), Nonce.fromString(args[4]), signatureFromString(args[5]));
                } catch (InvalidKeySpecException e) {
                    logger.error("Invalid line in ledger: {}", line);
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            case "reject":
                if (args.length != 5) {
                    logger.error("Invalid line in ledger: {}", line);
                    System.exit(1);
                }
                try {
                    bank.rejectTransactionNoLog(Crypto.keyFromString(args[1]), Crypto.keyFromString(args[2]), new BigDecimal(args[3]), Nonce.fromString(args[4]));
                } catch (InvalidKeySpecException e) {
                    logger.error("Invalid line in ledger: {}", line);
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
        }
    }

    public void createAccount(PublicKey key, Nonce nonce) throws IOException {
        ledgerWriter.append("create-");
        ledgerWriter.append(Crypto.keyAsString(key));
        ledgerWriter.append("-");
        ledgerWriter.append(nonce.toString());
        ledgerWriter.append('\n');
        ledgerWriter.flush();
    }

    public void addTransaction(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce, byte[] signature) throws IOException {
        ledgerWriter.append("add-");
        addTransactionContent(source, destination, amount, nonce, signature);
    }

    public void acceptTransaction(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce, byte[] signature) throws IOException {
        ledgerWriter.append("accept-");
        addTransactionContent(source, destination, amount, nonce, signature);
    }

    public void rejectTransaction(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce, byte[] signature) throws IOException {
        ledgerWriter.append("reject-");
        addTransactionContent(source, destination, amount, nonce, signature);
    }

    private void addTransactionContent(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce, byte[] signature) throws IOException {
        ledgerWriter.append(Crypto.keyAsString(source));
        ledgerWriter.append('-');
        ledgerWriter.append(Crypto.keyAsString(destination));
        ledgerWriter.append('-');
        ledgerWriter.append(amount.toString());
        ledgerWriter.append('-');
        ledgerWriter.append(nonce.toString());
        ledgerWriter.append('-');
        ledgerWriter.append(signatureToString(signature));
        ledgerWriter.append('\n');
        ledgerWriter.flush();
    }


    private byte[] signatureFromString(String signature) {
        return Base64.getDecoder().decode(signature);
    }

    private String signatureToString(byte[] signature) {
        return new String(Base64.getEncoder().encode(signature));
    }
}
