package pt.ulisboa.tecnico.sec.candeeiros.server;

import java.security.PublicKey;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.ulisboa.tecnico.sec.candeeiros.shared.Crypto;

public class LightSwitch {
    private static final Logger logger = LoggerFactory.getLogger(LightSwitch.class);
    ConcurrentHashMap<PublicKey, LocalDateTime> chandelier = new ConcurrentHashMap<>();
    private static final long lightSpeed = 400000000; // 400ms

    public LightSwitch() {
        this.chandelier = new ConcurrentHashMap<>();
    }

    public boolean isLightOn(PublicKey lamp) {
        LocalDateTime now = LocalDateTime.now();
        if (!chandelier.containsKey(lamp)) {
            chandelier.put(lamp, now);
            return true;
        }
        if (chandelier.get(lamp).plusNanos(lightSpeed).isBefore(now)) {
            chandelier.put(lamp, now);
            return true;
        } else {
            chandelier.put(lamp, now);
            logger.info("Blocked request from: {} at {}", Crypto.keyAsShortString(lamp), now.toString());
            return false;
        }
    }

}
