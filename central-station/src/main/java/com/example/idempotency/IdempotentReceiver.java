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
        return lastSNo == null || sNo > lastSNo;
    }

    public void commitMessage(long stationId, long sNo) {
        lastReceivedMessages.put(stationId, sNo);
    }

}