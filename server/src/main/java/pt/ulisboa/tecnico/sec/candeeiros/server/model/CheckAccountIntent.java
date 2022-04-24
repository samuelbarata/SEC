package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.util.HashMap;

public class CheckAccountIntent {
    int majority;
    HashMap<Integer, Integer> occurrences;
    HashMap<Integer, Bank.CheckAccountResponse> responses;
    boolean majorityChecked;

    public CheckAccountIntent() {
        responses = new HashMap<>();
        occurrences = new HashMap<>();
        majority = -1;
        this.majorityChecked = false;
    }

    public boolean addResponse(int timestamp, Bank.CheckAccountResponse response, int totalServers) {
        System.out.println("CHECK TS" + responses.get(timestamp));
        System.out.println("CHECK Response" + response);
        if(responses.get(timestamp) == null) {
            responses.put(timestamp, response);
            occurrences.put(timestamp, 1);
        }
        else if(responses.get(timestamp).equals(response)) {
            occurrences.put(timestamp, occurrences.get(timestamp) + 1);
        } else {
            return false;
        }

        if(majority < 0) {
            majority = timestamp;
        } else if (occurrences.get(majority) < occurrences.get(timestamp)) {
            majority = timestamp;
        }

        System.out.println("CHECK" + occurrences);
        if(this.majorityChecked) return false;
        return totalServers %2==0 ? occurrences.get(majority) >= (Math.ceil((double)(totalServers+1)/2)) : occurrences.get(majority) >= (Math.ceil((double)(totalServers)/2));
    }

    public Bank.CheckAccountResponse getMajority() {
        this.majorityChecked = true;
        return responses.get(majority);
    }
}
