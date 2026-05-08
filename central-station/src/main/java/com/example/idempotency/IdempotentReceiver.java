package com.example.idempotency;

import java.util.concurrent.ConcurrentHashMap;

// This file could be much simpler, bs 7anyaka menny (we call it for future cases)
// Should discuss dealing with the case if this restarts, since then the hashmap is gone
public class IdempotentReceiver {

    private static final ConcurrentHashMap<Long, Long> lastReceivedMessages = new ConcurrentHashMap<>();

    public static boolean shouldProcess(long stationId, long sNo) {
        boolean[] result = {false};

        lastReceivedMessages.compute(stationId, (key, lastSNo) -> {
            if (lastSNo == null || sNo > lastSNo) {
                result[0] = true;
                return sNo; // update stored value
            }

            return lastSNo; // keep existing value unchanged
        });

        return result[0];
    }

}
