package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.shared.Nonce;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.Objects;

public class Transaction {
    private final PublicKey source, destination;
    private final BigDecimal amount;

    // intentionally ignored by equals and hash
    private Nonce sourceNonce, destinationNonce;

    public Transaction(PublicKey source, PublicKey destination, BigDecimal amount) {
        this.source = source;
        this.destination = destination;
        this.amount = amount;
    }

    public PublicKey getSource() {
        return source;
    }

    public PublicKey getDestination() {
        return destination;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Nonce getSourceNonce() {
        return sourceNonce;
    }

    public void setSourceNonce(Nonce sourceNonce) {
        this.sourceNonce = sourceNonce;
    }

    public Nonce getDestinationNonce() {
        return destinationNonce;
    }

    public void setDestinationNonce(Nonce destinationNonce) {
        this.destinationNonce = destinationNonce;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Transaction that = (Transaction) o;
        return source.equals(that.source) && destination.equals(that.destination) && amount.equals(that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, destination, amount);
    }
}
