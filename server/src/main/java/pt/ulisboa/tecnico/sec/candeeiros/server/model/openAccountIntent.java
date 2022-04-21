package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.util.ArrayList;

public class openAccountIntent {
    private int timestamp;
    private Bank.OpenAccountRequest request;
    private ArrayList<Bank.OpenAccountResponse.Status> statuses;

    public openAccountIntent(int timestamp, Bank.OpenAccountRequest request) {
        this.timestamp = timestamp;
        this.request = request;
        this.statuses = new ArrayList<>();
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

    public ArrayList<Bank.OpenAccountResponse.Status> getStatuses() {
        return statuses;
    }

    public void setStatuses(ArrayList<Bank.OpenAccountResponse.Status> statuses) {
        this.statuses = statuses;
    }

    public void addStatus(Bank.OpenAccountResponse.Status newStatus) {
        statuses.add(newStatus);
    }

    public boolean majorityAchieved() {

        return false;
    }
}
