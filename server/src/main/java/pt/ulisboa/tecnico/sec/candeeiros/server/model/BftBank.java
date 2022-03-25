package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.server.BankServiceImpl;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import java.io.*;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;

// TODO fine tune synchronization
public class BftBank {
    private static final Logger logger = LoggerFactory.getLogger(BankServiceImpl.class);
    private final Map<PublicKey, BankAccount> accounts;
    private final BufferedWriter ledgerWriter;

    public BftBank(String ledgerFileName) throws IOException {
        accounts = new HashMap<>();

        logger.info("Trying to read ledger at {}", ledgerFileName);
        parseLedger(ledgerFileName);

        ledgerWriter = new BufferedWriter(new FileWriter(ledgerFileName, true));
    }

    private synchronized void parseLedger(String ledgerFileName) throws IOException {
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

    private synchronized void parseLine(String line) {
        line = line.substring(0, line.length() - 1);
        String[] args = line.split("-");
        switch (args[0]) {
            case "create":
                if (args.length != 2) {
                    logger.error("Invalid line in ledger: {}", line);
                    System.exit(1);
                }
                try {
                    createAccountNoLog(Crypto.keyFromString(args[1]));
                } catch (InvalidKeySpecException e) {
                    logger.error("Invalid line in ledger: {}", line);
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            case "add":
                if (args.length != 4) {
                    logger.error("Invalid line in ledger: {}", line);
                    System.exit(1);
                }
                try {
                    addTransactionNoLog(Crypto.keyFromString(args[1]), Crypto.keyFromString(args[2]), new BigDecimal(args[3]));
                } catch (InvalidKeySpecException e) {
                    logger.error("Invalid line in ledger: {}", line);
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            case "accept":
                if (args.length != 4) {
                    logger.error("Invalid line in ledger: {}", line);
                    System.exit(1);
                }
                try {
                    acceptTransactionNoLog(Crypto.keyFromString(args[1]), Crypto.keyFromString(args[2]), new BigDecimal(args[3]));
                } catch (InvalidKeySpecException e) {
                    logger.error("Invalid line in ledger: {}", line);
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
            case "reject":
                if (args.length != 4) {
                    logger.error("Invalid line in ledger: {}", line);
                    System.exit(1);
                }
                try {
                    rejectTransactionNoLog(Crypto.keyFromString(args[1]), Crypto.keyFromString(args[2]), new BigDecimal(args[3]));
                } catch (InvalidKeySpecException e) {
                    logger.error("Invalid line in ledger: {}", line);
                    e.printStackTrace();
                    System.exit(1);
                }
                break;
        }
    }

    public synchronized boolean accountExists(PublicKey key) {
        return accounts.containsKey(key);
    }

    private synchronized BankAccount createAccountNoLog(PublicKey key) {
        BankAccount account = new BankAccount(key);
        accounts.put(key, account);
        return account;
    }

    public synchronized void createAccount(PublicKey key) {
        BankAccount account = createAccountNoLog(key);
        try {
            ledgerWriter.append("create-");
            ledgerWriter.append(Crypto.keyAsString(key));
            ledgerWriter.append("-");
            ledgerWriter.append(account.getNonce().toString());
            ledgerWriter.append('\n');
            ledgerWriter.flush();
        } catch (IOException e) {
            logger.error("Cannot write to ledger file. Exiting");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private synchronized void addTransactionNoLog(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount sourceAccount = getAccount(source);
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        accounts.get(destination).getTransactionQueue().add(transaction);
    }

    public synchronized void addTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        addTransactionNoLog(source, destination, amount);
        try {
            ledgerWriter.append("add-");
            ledgerWriter.append(Crypto.keyAsString(source));
            ledgerWriter.append('-');
            ledgerWriter.append(Crypto.keyAsString(destination));
            ledgerWriter.append('-');
            ledgerWriter.append(amount.toString());
            ledgerWriter.append('\n');
            ledgerWriter.flush();
        } catch (IOException e) {
            logger.error("Cannot write to ledger file. Exiting");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private synchronized void acceptTransactionNoLog(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount destinationAccount = getAccount(destination);
        BankAccount sourceAccount = getAccount(source);

        destinationAccount.getTransactionQueue().remove(transaction);

        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        destinationAccount.getTransactionHistory().add(transaction);
        sourceAccount.getTransactionHistory().add(transaction);
    }

    public synchronized void acceptTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        acceptTransactionNoLog(source, destination, amount);
        try {
            ledgerWriter.append("accept-");
            ledgerWriter.append(Crypto.keyAsString(source));
            ledgerWriter.append('-');
            ledgerWriter.append(Crypto.keyAsString(destination));
            ledgerWriter.append('-');
            ledgerWriter.append(amount.toString());
            ledgerWriter.append('\n');
            ledgerWriter.flush();
        } catch (IOException e) {
            logger.error("Cannot write to ledger file. Exiting");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private synchronized void rejectTransactionNoLog(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount destinationAccount = getAccount(destination);
        BankAccount sourceAccount = getAccount(source);

        destinationAccount.getTransactionQueue().remove(transaction);
        sourceAccount.setBalance(sourceAccount.getBalance().add(amount));
    }

    public synchronized void rejectTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        rejectTransactionNoLog(source, destination, amount);
        try {
            ledgerWriter.append("reject-");
            ledgerWriter.append(Crypto.keyAsString(source));
            ledgerWriter.append('-');
            ledgerWriter.append(Crypto.keyAsString(destination));
            ledgerWriter.append('-');
            ledgerWriter.append(amount.toString());
            ledgerWriter.append('\n');
            ledgerWriter.flush();
        } catch (IOException e) {
            logger.error("Cannot write to ledger file. Exiting");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public synchronized BankAccount getAccount(PublicKey key) {
        return accounts.get(key);
    }
}
