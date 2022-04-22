package pt.ulisboa.tecnico.sec.candeeiros.server.model;

import pt.ulisboa.tecnico.sec.candeeiros.Bank;

import java.util.HashMap;

public class CheckAccountIntent {
    int majority;
    HashMap<Integer, Integer> occurrences;
    HashMap<Integer, Bank.CheckAccountResponse> responses;

    public CheckAccountIntent() {
        responses = new HashMap<>();
        occurrences = new HashMap<>();
        majority = -1;
    }

    public boolean addResponse(int timestamp, Bank.CheckAccountResponse response, int totalServers) {
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

        return occurrences.get(majority) >= (Math.ceil((double)totalServers / 2));
    }

    public Bank.CheckAccountResponse getMajority() {
        return responses.get(majority);
    }
}
