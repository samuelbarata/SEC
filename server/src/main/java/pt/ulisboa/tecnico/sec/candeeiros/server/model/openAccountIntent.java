package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.util.ArrayList;
import java.util.HashMap;

public class OpenAccountIntent {
    private int timestamp;
    private Bank.OpenAccountRequest request;
    private ArrayList<Bank.OpenAccountResponse.Status> statuses;
    private final HashMap<Bank.OpenAccountResponse.Status, Integer> occurrences;
    private Bank.OpenAccountResponse.Status majority;
    private boolean majorityChecked;

    public OpenAccountIntent(int timestamp, Bank.OpenAccountRequest request) {
        this.timestamp = timestamp;
        this.request = request;
        this.statuses = new ArrayList<>();
        this.occurrences = new HashMap<>();
        this.majority = null;
        this.majorityChecked = false;
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
        if(majorityChecked) return false;
        return totalServers %2==0 ? occurrences.get(majority) >= (Math.ceil((double)(totalServers+1)/2)) : occurrences.get(majority) >= (Math.ceil((double)(totalServers)/2));
    }

    public Bank.OpenAccountResponse.Status getMajority() {
        return majority;
    }
    public void majorityChecked() { majorityChecked = true;}
}
