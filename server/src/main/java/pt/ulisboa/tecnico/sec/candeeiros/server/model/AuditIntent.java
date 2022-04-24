package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.util.HashMap;

public class AuditIntent {
    int majority;
    HashMap<Integer, Integer> occurrences;
    HashMap<Integer, Bank.AuditResponse> responses;
    boolean majorityChecked;

    public AuditIntent() {
        responses = new HashMap<>();
        occurrences = new HashMap<>();
        majority = -1;
        this.majorityChecked = false;
    }

    public boolean addResponse(int timestamp, Bank.AuditResponse response, int totalServers) {
        if(responses.get(timestamp) == null) {
            responses.put(timestamp, response);
            occurrences.put(timestamp, 1);
        }
        else if(responses.get(timestamp) == response) {
            occurrences.put(timestamp, occurrences.get(timestamp) + 1);
        } else {
            return false;
        }

        if(majority < 0) {
            majority = timestamp;
        } else if (occurrences.get(majority) < occurrences.get(timestamp)) {
            majority = timestamp;
        }
        if(this.majorityChecked) return false;
        return totalServers %2==0 ? occurrences.get(majority) >= (Math.ceil((double)(totalServers+1)/2)) : occurrences.get(majority) >= (Math.ceil((double)(totalServers)/2));
    }

    public Bank.AuditResponse getMajority() {
        this.majorityChecked = true;
        return responses.get(majority);
    }
}
