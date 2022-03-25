package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.io.*;
import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class BftBank {
    private static final Logger logger = LoggerFactory.getLogger(BftBank.class);
    private final Map<PublicKey, BankAccount> accounts;
    private final LedgerManager ledgerManager;

    public BftBank(String ledgerFileName) throws IOException {
        accounts = new HashMap<>();

        logger.info("Trying to read ledger at {}", ledgerFileName);
        ledgerManager = new LedgerManager(this, ledgerFileName);
        ledgerManager.parseLedger();
    }

    public synchronized boolean accountExists(PublicKey key) {
        return accounts.containsKey(key);
    }

    protected synchronized BankAccount createAccountWithNonce(PublicKey key, Nonce nonce) {
        BankAccount account = new BankAccount(key, nonce);
        accounts.put(key, account);
        return account;
    }

    protected synchronized BankAccount createAccountNoLog(PublicKey key) {
        BankAccount account = new BankAccount(key);
        accounts.put(key, account);
        return account;
    }

    public synchronized void createAccount(PublicKey key) {
        BankAccount account = createAccountNoLog(key);
        try {
            ledgerManager.createAccount(key, account.getNonce());
        } catch (IOException e) {
            logger.error("Cannot write to ledger file. Exiting");
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected synchronized void addTransactionNoLog(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount sourceAccount = getAccount(source);
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        transaction.setSourceNonce(nonce);
        accounts.get(destination).getTransactionQueue().add(transaction);
        sourceAccount.setNonce(nonce);
    }

    public synchronized void addTransaction(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce) {
        addTransactionNoLog(source, destination, amount, nonce);
        try {
            ledgerManager.addTransaction(source, destination, amount, nonce);
        } catch (IOException e) {
            logger.error("Cannot write to ledger file. Exiting");
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected synchronized void acceptTransactionNoLog(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount destinationAccount = getAccount(destination);
        BankAccount sourceAccount = getAccount(source);

        // Needed to maintain source nonce
        int i = destinationAccount.getTransactionQueue().indexOf(transaction);
        transaction = destinationAccount.getTransactionQueue().remove(i);

        transaction.setDestinationNonce(nonce);

        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        destinationAccount.getTransactionHistory().add(transaction);
        sourceAccount.getTransactionHistory().add(transaction);

        destinationAccount.setNonce(nonce);
    }

    public synchronized void acceptTransaction(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce) {
        acceptTransactionNoLog(source, destination, amount, nonce);
        try {
            ledgerManager.acceptTransaction(source, destination, amount, nonce);
        } catch (IOException e) {
            logger.error("Cannot write to ledger file. Exiting");
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected synchronized void rejectTransactionNoLog(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount destinationAccount = getAccount(destination);
        BankAccount sourceAccount = getAccount(source);

        destinationAccount.getTransactionQueue().remove(transaction);
        sourceAccount.setBalance(sourceAccount.getBalance().add(amount));

        destinationAccount.setNonce(nonce);
    }

    public synchronized void rejectTransaction(PublicKey source, PublicKey destination, BigDecimal amount, Nonce nonce) {
        rejectTransactionNoLog(source, destination, amount, nonce);
        try {
            ledgerManager.rejectTransaction(source, destination, amount, nonce);
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
