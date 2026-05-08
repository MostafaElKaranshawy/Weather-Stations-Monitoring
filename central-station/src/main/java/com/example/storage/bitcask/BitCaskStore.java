package com.example.storage.bitcask;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BitCaskStore implements AutoCloseable {

    private static final String DATA_FILE_EXTENSION = ".data";
    private final Path directory;
    private final Map<String, IndexEntry> keyDir = new ConcurrentHashMap<>();
    
    private FileChannel activeFileChannel;
    private String activeFileId;
    private long currentOffset = 0;

    public BitCaskStore(String dirPath) throws IOException {
        this.directory = Paths.get(dirPath);
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
        boot();
    }

    private static final String HINT_FILE_EXTENSION = ".hint";

    private synchronized void boot() throws IOException {
        List<Path> dataFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + DATA_FILE_EXTENSION)) {
            for (Path entry : stream) {
                dataFiles.add(entry);
            }
        }
        
        dataFiles.sort(Comparator.comparing(Path::toString));

        for (Path path : dataFiles) {
            String fileId = path.getFileName().toString();
            Path hintPath = directory.resolve(fileId.replace(DATA_FILE_EXTENSION, HINT_FILE_EXTENSION));
            
            if (Files.exists(hintPath)) {
                loadIndexFromHintFile(hintPath, fileId);
            } else {
                loadIndexFromDataFile(path, fileId);
                writeHintFile(path, fileId); // Create hint file if it doesn't exist
            }
        }

        rotateActiveFile();
    }

    private void loadIndexFromHintFile(Path hintPath, String fileId) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(hintPath.toFile(), "r")) {
            long offset = 0;
            long fileLength = raf.length();
            while (offset < fileLength) {
                long timestamp = raf.readLong();
                int keySize = raf.readInt();
                int valueSize = raf.readInt();
                long valuePosition = raf.readLong();
                
                byte[] keyBytes = new byte[keySize];
                raf.readFully(keyBytes);
                String key = new String(keyBytes);
                
                keyDir.put(key, new IndexEntry(fileId, valueSize, valuePosition, timestamp));
                offset += 24 + keySize;
            }
        }
    }

    private void loadIndexFromDataFile(Path path, String fileId) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long offset = 0;
            long fileLength = raf.length();
            while (offset < fileLength) {
                if (fileLength - offset < 16) break;
                long timestamp = raf.readLong();
                int keySize = raf.readInt();
                int valueSize = raf.readInt();
                byte[] keyBytes = new byte[keySize];
                raf.readFully(keyBytes);
                String key = new String(keyBytes);
                keyDir.put(key, new IndexEntry(fileId, valueSize, offset + 16 + keySize, timestamp));
                offset += 16 + keySize + valueSize;
            }
        }
    }

    private void writeHintFile(Path dataPath, String fileId) throws IOException {
        Path hintPath = directory.resolve(fileId.replace(DATA_FILE_EXTENSION, HINT_FILE_EXTENSION));
        // We need to filter keyDir for entries that belong to this fileId
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(hintPath.toFile()))) {
            for (Map.Entry<String, IndexEntry> entry : keyDir.entrySet()) {
                if (entry.getValue().fileId.equals(fileId)) {
                    dos.writeLong(entry.getValue().timestamp);
                    byte[] keyBytes = entry.getKey().getBytes();
                    dos.writeInt(keyBytes.length);
                    dos.writeInt(entry.getValue().valueSize);
                    dos.writeLong(entry.getValue().valuePosition);
                    dos.write(keyBytes);
                }
            }
        }
    }

    private void rotateActiveFile() throws IOException {
        if (activeFileChannel != null) {
            activeFileChannel.close();
            // Optional: generate hint file for the old active file
            writeHintFile(directory.resolve(activeFileId), activeFileId);
        }
        activeFileId = System.currentTimeMillis() + DATA_FILE_EXTENSION;
        Path activePath = directory.resolve(activeFileId);
        activeFileChannel = FileChannel.open(activePath, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        currentOffset = 0;
    }

    public synchronized void put(String key, byte[] value) throws IOException {
        byte[] keyBytes = key.getBytes();
        int keySize = keyBytes.length;
        int valueSize = value.length;
        long timestamp = System.currentTimeMillis();

        int totalSize = 16 + keySize + valueSize;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.putLong(timestamp);
        buffer.putInt(keySize);
        buffer.putInt(valueSize);
        buffer.put(keyBytes);
        buffer.put(value);
        buffer.flip();

        while (buffer.hasRemaining()) {
            activeFileChannel.write(buffer);
        }

        keyDir.put(key, new IndexEntry(activeFileId, valueSize, currentOffset + 16 + keySize, timestamp));
        currentOffset += totalSize;
    }

    public byte[] get(String key) throws IOException {
        IndexEntry entry = keyDir.get(key);
        if (entry == null) {
            return null;
        }

        Path path = directory.resolve(entry.fileId);
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(entry.valuePosition);
            byte[] value = new byte[entry.valueSize];
            raf.readFully(value);
            return value;
        }
    }

    public List<String> listKeys() {
        return new ArrayList<>(keyDir.keySet());
    }

    public synchronized void compact() throws IOException {
        String compactFileId = "compact_" + System.currentTimeMillis() + DATA_FILE_EXTENSION;
        Path compactPath = directory.resolve(compactFileId);
        
        Map<String, IndexEntry> newEntries = new HashMap<>();
        long offset = 0;

        try (FileChannel compactChannel = FileChannel.open(compactPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (Map.Entry<String, IndexEntry> entry : keyDir.entrySet()) {
                String key = entry.getKey();
                IndexEntry index = entry.getValue();
                
                // Skip entries currently in the active file to avoid race conditions or complex locking
                if (index.fileId.equals(activeFileId)) continue;

                byte[] value = get(key);
                if (value == null) continue;

                byte[] keyBytes = key.getBytes();
                int keySize = keyBytes.length;
                int valueSize = value.length;

                int totalSize = 16 + keySize + valueSize;
                ByteBuffer buffer = ByteBuffer.allocate(totalSize);
                buffer.putLong(index.timestamp);
                buffer.putInt(keySize);
                buffer.putInt(valueSize);
                buffer.put(keyBytes);
                buffer.put(value);
                buffer.flip();

                while (buffer.hasRemaining()) {
                    compactChannel.write(buffer);
                }

                newEntries.put(key, new IndexEntry(compactFileId, valueSize, offset + 16 + keySize, index.timestamp));
                offset += totalSize;
            }
        }

        // Update keyDir and clean up old files
        Set<String> filesToKeep = new HashSet<>();
        filesToKeep.add(activeFileId);
        filesToKeep.add(compactFileId);
        
        // Also keep the hint file for the compact file
        writeHintFile(compactPath, compactFileId);
        filesToKeep.add(compactFileId.replace(DATA_FILE_EXTENSION, HINT_FILE_EXTENSION));
        filesToKeep.add(activeFileId.replace(DATA_FILE_EXTENSION, HINT_FILE_EXTENSION));

        // Update memory index
        for (Map.Entry<String, IndexEntry> e : newEntries.entrySet()) {
            // Only update if the fileId in keyDir is NOT the active one (we don't want to revert an even newer update)
            keyDir.compute(e.getKey(), (k, current) -> {
                if (current != null && !current.fileId.equals(activeFileId)) {
                    return e.getValue();
                }
                return current;
            });
        }

        // Delete old files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if ((name.endsWith(DATA_FILE_EXTENSION) || name.endsWith(HINT_FILE_EXTENSION)) 
                    && !filesToKeep.contains(name)) {
                    Files.delete(p);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (activeFileChannel != null) {
            activeFileChannel.close();
        }
    }

    public static record IndexEntry(String fileId, int valueSize, long valuePosition, long timestamp) {}
}
