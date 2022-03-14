package pt.ulisboa.tecnico.sec.candeeiros.server.model;

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

    public boolean tryCreateAccount(PublicKey key) {
        if (!accountExists(key)) {
            accounts.put(key, new BankAccount(key));
            return true;
        }
        return false;
    }
}
