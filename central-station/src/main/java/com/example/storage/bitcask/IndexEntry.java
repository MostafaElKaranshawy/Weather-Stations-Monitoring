package com.example.storage.bitcask;

public record IndexEntry(String fileId, int valueSize, long valuePosition, long timestamp) {}
