package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.Objects;

public class Transaction {
    private final PublicKey source, destination;
    private final BigDecimal amount;

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
