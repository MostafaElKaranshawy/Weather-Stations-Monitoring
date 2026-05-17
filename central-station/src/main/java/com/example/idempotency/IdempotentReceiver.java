package com.example.idempotency;

import java.util.HashMap;
import java.util.Map;

public class IdempotentReceiver {
    private final Map<Long, Long> lastReceivedMessages;

    public IdempotentReceiver() {
        lastReceivedMessages = new HashMap<>();
    }

    public boolean shouldProcess(long stationId, long sNo) {
        Long lastSNo = lastReceivedMessages.get(stationId);
        if (lastSNo == null || sNo > lastSNo) {
            lastReceivedMessages.put(stationId, sNo);
            return true;
        }

        return false;
    }

}