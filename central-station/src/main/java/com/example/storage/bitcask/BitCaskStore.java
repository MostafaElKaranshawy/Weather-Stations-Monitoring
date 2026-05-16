package com.example.storage.bitcask;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BitCaskStore implements AutoCloseable {

    private final Path directory;
    private final Map<String, IndexEntry> keyDir = new ConcurrentHashMap<>();
    
    private FileChannel activeFileChannel;
    private String activeFileId;
    private long currentOffset = 0;
    private static final long MAX_FILE_SIZE = 1024 * 1024; // 1 MB limit for segment rotation

    public BitCaskStore(String dirPath) throws IOException {
        this.directory = Paths.get(dirPath);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        boot();
    }

    private synchronized void boot() throws IOException {
        BitCaskBootstrapper.boot(directory, keyDir);
        rotateActiveFile();
    }

    private void rotateActiveFile() throws IOException {
        if (activeFileChannel != null) {
            activeFileChannel.close();
            // generate hint file for the old active file
            BitCaskFileIO.writeHintFile(directory, activeFileId, keyDir);
        }
        activeFileId = System.currentTimeMillis() + BitCaskFileIO.DATA_FILE_EXTENSION;
        Path activePath = directory.resolve(activeFileId);
        activeFileChannel = FileChannel.open(activePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        currentOffset = 0;
    }

    public synchronized void put(String key, byte[] value) throws IOException {
        byte[] keyBytes = key.getBytes();
        int keySize = keyBytes.length;
        int valueSize = value.length;
        int recordSize = 16 + keySize + valueSize;

        if (currentOffset + recordSize > MAX_FILE_SIZE) {
            rotateActiveFile();
        }

        long timestamp = System.currentTimeMillis();
        BitCaskFileIO.writeRecordToChannel(activeFileChannel, timestamp, keyBytes, value);

        keyDir.put(key, new IndexEntry(activeFileId, valueSize, currentOffset + 16 + keySize, timestamp));
        currentOffset += recordSize;
    }

    public byte[] get(String key) throws IOException {
        IndexEntry entry = keyDir.get(key);
        if (entry == null) {
            return null;
        }
        return BitCaskFileIO.readValue(directory, entry);
    }

    public List<String> listKeys() {
        return new ArrayList<>(keyDir.keySet());
    }

    public synchronized void compact() throws IOException {
        BitCaskCompactor.compact(directory, keyDir, activeFileId);
    }

    @Override
    public void close() throws IOException {
        if (activeFileChannel != null) {
            activeFileChannel.close();
        }
    }
}
