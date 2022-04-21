package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.util.ArrayList;
import java.util.HashMap;

public class openAccountIntent {
    private int timestamp;
    private Bank.OpenAccountRequest request;
    private ArrayList<Bank.OpenAccountResponse.Status> statuses;
    private HashMap<Bank.OpenAccountResponse.Status, Integer> occurences;
    private Bank.OpenAccountResponse.Status majority;

    public openAccountIntent(int timestamp, Bank.OpenAccountRequest request) {
        this.timestamp = timestamp;
        this.request = request;
        this.statuses = new ArrayList<>();
        this.occurences = new HashMap<>();
        this.majority = null;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public Bank.OpenAccountRequest getRequest() {
        return request;
    }

    public void setRequest(Bank.OpenAccountRequest request) {
        this.request = request;
    }

    public void addStatus(Bank.OpenAccountResponse.Status newStatus) {
        statuses.add(newStatus);

        // first time adding a status, to update majority
        if(majority == null) {
            occurences.put(newStatus, 1);
            majority = newStatus;
            return;
        }
        // if there was already a majority and a status alike was added
        else if(occurences.containsKey(newStatus)) {
            occurences.put(newStatus, occurences.get(newStatus) + 1);
        // if there was already a majority and a status alike wasn't added
        } else {
            occurences.put(newStatus, 1);
        }

        //check majority
        if(occurences.get(newStatus) > occurences.get(majority)) {
            majority = newStatus;
        }

    }

    public boolean hasMajority(int totalServers) {
        return occurences.get(majority) >= (Math.ceil(totalServers/2));
    }

    public Bank.OpenAccountResponse.Status getMajority() {
        return majority;
    }
}
