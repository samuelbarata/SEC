package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BftBank {
    private final Map<PublicKey, BankAccount> accounts;

    public BftBank() {
        accounts = new ConcurrentHashMap<>();
    }

    public boolean accountExists(PublicKey key) {
        return accounts.containsKey(key);
    }

    public void createAccount(PublicKey key) {
        accounts.put(key, new BankAccount(key));
    }

    public void addTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount sourceAccount = getAccount(source);
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        accounts.get(destination).getTransactionQueue().add(transaction);
    }

    public void acceptTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount destinationAccount = getAccount(destination);
        BankAccount sourceAccount = getAccount(source);

        destinationAccount.getTransactionQueue().remove(transaction);

        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        destinationAccount.getTransactionHistory().add(transaction);
        sourceAccount.getTransactionHistory().add(transaction);
    }

    public void rejectTransaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        Transaction transaction = new Transaction(source, destination, amount);
        BankAccount destinationAccount = getAccount(destination);
        BankAccount sourceAccount = getAccount(source);

        destinationAccount.getTransactionQueue().remove(transaction);
        sourceAccount.setBalance(sourceAccount.getBalance().add(amount));
    }

    public BankAccount getAccount(PublicKey key) {
        return accounts.get(key);
    }
}
