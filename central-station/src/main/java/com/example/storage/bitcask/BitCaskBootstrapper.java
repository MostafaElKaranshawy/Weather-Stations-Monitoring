package com.example.storage.bitcask;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class BitCaskBootstrapper {

    public static void boot(Path directory, Map<String, IndexEntry> keyDir) throws IOException {
        List<Path> dataFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + BitCaskFileIO.DATA_FILE_EXTENSION)) {
            for (Path entry : stream) {
                dataFiles.add(entry);
            }
        }
        
        dataFiles.sort(Comparator.comparing(Path::toString));

        for (Path path : dataFiles) {
            String fileId = path.getFileName().toString();
            Path hintPath = directory.resolve(fileId.replace(BitCaskFileIO.DATA_FILE_EXTENSION, BitCaskFileIO.HINT_FILE_EXTENSION));
            
            if (Files.exists(hintPath)) {
                loadIndexFromHintFile(hintPath, fileId, keyDir);
            } else {
                loadIndexFromDataFile(path, fileId, keyDir);
                BitCaskFileIO.writeHintFile(directory, fileId, keyDir);
            }
        }
    }

    private static void loadIndexFromHintFile(Path hintPath, String fileId, Map<String, IndexEntry> keyDir) throws IOException {
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

    private static void loadIndexFromDataFile(Path path, String fileId, Map<String, IndexEntry> keyDir) throws IOException {
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
                // Skip the value since we only need the index
                raf.skipBytes(valueSize);
                
                String key = new String(keyBytes);
                keyDir.put(key, new IndexEntry(fileId, valueSize, offset + 16 + keySize, timestamp));
                offset += 16 + keySize + valueSize;
            }
        }
    }
}
