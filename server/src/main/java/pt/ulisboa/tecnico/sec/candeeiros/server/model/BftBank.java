package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BftBank {
    private Map<PublicKey, BankAccount> accounts;

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
        accounts.get(source).getBalance().subtract(amount);
        accounts.get(destination).getTransactionQueue().add(transaction);
    }

    public BankAccount getAccount(PublicKey key) {
        return accounts.get(key);
    }
}
