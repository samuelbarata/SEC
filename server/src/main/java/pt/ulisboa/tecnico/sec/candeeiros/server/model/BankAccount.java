package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BankAccount {
    private final PublicKey publicKey;
    private BigDecimal balance;
    // Intentionally using the less abstract type to indicate the map maintains order
    private final LinkedHashMap<Transaction, Nonce> transactionHistory;
    private final LinkedHashMap<Transaction, Nonce> transactionQueue;
    private Nonce nonce;

    public LinkedHashMap<Transaction, Nonce> getTransactionQueue() {
        return transactionQueue;
    }

    public LinkedHashMap<Transaction, Nonce> getTransactionHistory() {
        return transactionHistory;
    }

    public BankAccount(PublicKey publicKey, Nonce nonce) {
        if (publicKey == null) {
            throw new NullPointerException();
        }
        this.publicKey = publicKey;
        this.balance = new BigDecimal(1000);
        this.transactionHistory = new LinkedHashMap();
        this.transactionQueue = new LinkedHashMap();
        this.nonce = nonce;
    }

    public BankAccount(PublicKey publicKey) {
        this(publicKey, Nonce.newNonce());
    }

    public Nonce getNonce() {
        return nonce;
    }

    public void setNonce(Nonce nonce) {
        this.nonce = nonce;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BankAccount account = (BankAccount) o;
        return publicKey.equals(account.publicKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey);
    }
}
