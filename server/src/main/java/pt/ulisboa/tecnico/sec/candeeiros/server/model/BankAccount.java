package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class BankAccount {
    private final PublicKey publicKey;
    private BigDecimal balance;
    private final List<Transaction> transactionHistory;
    private final List<Transaction> transactionQueue;
    private Nonce nonce;

    public List<Transaction> getTransactionQueue() {
        return transactionQueue;
    }

    public List<Transaction> getTransactionHistory() {
        return transactionHistory;
    }

    public BankAccount(PublicKey publicKey) {
        if (publicKey == null) {
            throw new NullPointerException();
        }
        this.publicKey = publicKey;
        this.balance = new BigDecimal(1000);
        this.transactionHistory = Collections.synchronizedList(new ArrayList<>());
        this.transactionQueue = Collections.synchronizedList(new ArrayList<>());
        SecureRandom sr = new SecureRandom();
        this.nonce = Nonce.newNonce();
    }

    public Nonce getNonce() {
        return nonce;
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
