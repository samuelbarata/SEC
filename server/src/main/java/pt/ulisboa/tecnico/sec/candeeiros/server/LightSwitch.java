package pt.ulisboa.tecnico.sec.candeeiros.server;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

public class LightSwitch {
    ConcurrentHashMap<PublicKey, LocalDateTime> chandelier = new ConcurrentHashMap<>();

    public LightSwitch() {
        this.chandelier = new ConcurrentHashMap<>();
    }

    public boolean isLightOn(PublicKey lamp) {
        if(!chandelier.containsKey(lamp)) {
            chandelier.put(lamp, LocalDateTime.now());
            return true;
        }
        if(chandelier.get(lamp).isAfter(LocalDateTime.now().minusNanos(10000000))) {
            chandelier.put(lamp, LocalDateTime.now());
            return true;
        } else {
            chandelier.put(lamp, LocalDateTime.now());
            return false;
        }
    }

}
