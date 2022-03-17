package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.UUID;

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
}
