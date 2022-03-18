package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.server.BankServiceImpl;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

import java.io.*;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BftBank {
    private static final Logger logger = LoggerFactory.getLogger(BankServiceImpl.class);
    private final Map<PublicKey, BankAccount> accounts;
    private final BufferedWriter ledgerWriter;

    public BftBank(String ledgerFileName) throws IOException {
        accounts = new ConcurrentHashMap<>();

        logger.info("Trying to read ledger at {}", ledgerFileName);
        parseLedger(ledgerFileName);

        ledgerWriter = new BufferedWriter(new FileWriter(ledgerFileName, true));
    }

    public void parseLedger(String ledgerFileName) throws IOException {
        new File(ledgerFileName).createNewFile(); // Will create file if it doesn't exist
        BufferedReader br = new BufferedReader(new FileReader(ledgerFileName));
        String line;
        while ((line = br.readLine()) != null) {
            // TODO
        }
    }

    public boolean accountExists(PublicKey key) {
        return accounts.containsKey(key);
    }

    public void createAccount(PublicKey key) {
        accounts.put(key, new BankAccount(key));

        try {
            ledgerWriter.append("create-");
            ledgerWriter.append(Crypto.keyAsString(key));
            ledgerWriter.append('\n');
            ledgerWriter.flush();
        } catch (IOException e) {
            logger.error("Cannot write to ledger file. Exiting");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public void addTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount sourceAccount = getAccount(source);
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        accounts.get(destination).getTransactionQueue().add(transaction);

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

    public void acceptTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount destinationAccount = getAccount(destination);
        BankAccount sourceAccount = getAccount(source);

        destinationAccount.getTransactionQueue().remove(transaction);

        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        destinationAccount.getTransactionHistory().add(transaction);
        sourceAccount.getTransactionHistory().add(transaction);

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

    public void rejectTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount destinationAccount = getAccount(destination);
        BankAccount sourceAccount = getAccount(source);

        destinationAccount.getTransactionQueue().remove(transaction);
        sourceAccount.setBalance(sourceAccount.getBalance().add(amount));

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

    public BankAccount getAccount(PublicKey key) {
        return accounts.get(key);
    }
}
