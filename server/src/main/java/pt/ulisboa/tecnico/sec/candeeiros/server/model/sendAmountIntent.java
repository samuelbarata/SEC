package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.util.ArrayList;
import java.util.HashMap;

public class sendAmountIntent {
    private int timestamp;
    private Bank.SendAmountRequest request;
    private ArrayList<Bank.SendAmountResponse.Status> statuses;
    private final HashMap<Bank.SendAmountResponse.Status, Integer> occurrences;
    private Bank.SendAmountResponse.Status majority;

    public sendAmountIntent(int timestamp, Bank.SendAmountRequest request) {
        this.timestamp = timestamp;
        this.request = request;
        this.statuses = new ArrayList<>();
        this.occurrences = new HashMap<>();
        this.majority = null;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public Bank.SendAmountRequest getRequest() {
        return request;
    }

    public void setRequest(Bank.SendAmountRequest request) {
        this.request = request;
    }

    public void addStatus(Bank.SendAmountResponse.Status newStatus) {
        statuses.add(newStatus);

        // first time adding a status, to update majority
        if(majority == null) {
            occurrences.put(newStatus, 1);
            majority = newStatus;
            return;
        }
        // if there was already a majority and a status alike was added
        else if(occurrences.containsKey(newStatus)) {
            occurrences.put(newStatus, occurrences.get(newStatus) + 1);
            // if there was already a majority and a status alike wasn't added
        } else {
            occurrences.put(newStatus, 1);
        }

        //check majority
        if(occurrences.get(newStatus) > occurrences.get(majority)) {
            majority = newStatus;
        }

    }

    public boolean hasMajority(int totalServers) {
        return occurrences.get(majority) >= (Math.ceil(totalServers/2));
    }

    public Bank.SendAmountResponse.Status getMajority() {
        return majority;
    }
}
