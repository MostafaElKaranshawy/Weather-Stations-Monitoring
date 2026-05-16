package com.example.storage.bitcask;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

public class BitCaskCompactor {

    public static void compact(Path directory, Map<String, IndexEntry> keyDir, String activeFileId) throws IOException {
        String compactFileId = "compact_" + System.currentTimeMillis() + BitCaskFileIO.DATA_FILE_EXTENSION;
        Path compactPath = directory.resolve(compactFileId);
        
        Map<String, IndexEntry> newEntries = new HashMap<>();
        long offset = 0;

        try (FileChannel compactChannel = FileChannel.open(compactPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (Map.Entry<String, IndexEntry> entry : keyDir.entrySet()) {
                String key = entry.getKey();
                IndexEntry index = entry.getValue();
                
                // Skip entries currently in the active file to avoid race conditions or complex locking
                if (index.fileId().equals(activeFileId)) continue;

                byte[] value = BitCaskFileIO.readValue(directory, index);
                if (value == null) continue;

                byte[] keyBytes = key.getBytes();
                int keySize = keyBytes.length;
                int valueSize = value.length;

                BitCaskFileIO.writeRecordToChannel(compactChannel, index.timestamp(), keyBytes, value);

                newEntries.put(key, new IndexEntry(compactFileId, valueSize, offset + 16 + keySize, index.timestamp()));
                offset += 16 + keySize + valueSize;
            }
        }

        // Update memory index
        for (Map.Entry<String, IndexEntry> e : newEntries.entrySet()) {
            // Only update if the fileId in keyDir is NOT the active one (we don't want to revert an even newer update)
            keyDir.compute(e.getKey(), (k, current) -> {
                if (current != null && !current.fileId().equals(activeFileId)) {
                    return e.getValue();
                }
                return current;
            });
        }

        Set<String> filesToKeep = new HashSet<>();
        filesToKeep.add(activeFileId);
        filesToKeep.add(compactFileId);
        
        // Use updated keyDir to write a correct hint file for the compactFileId
        BitCaskFileIO.writeHintFile(directory, compactFileId, keyDir);
        filesToKeep.add(compactFileId.replace(BitCaskFileIO.DATA_FILE_EXTENSION, BitCaskFileIO.HINT_FILE_EXTENSION));
        filesToKeep.add(activeFileId.replace(BitCaskFileIO.DATA_FILE_EXTENSION, BitCaskFileIO.HINT_FILE_EXTENSION));

        // Delete old files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if ((name.endsWith(BitCaskFileIO.DATA_FILE_EXTENSION) || name.endsWith(BitCaskFileIO.HINT_FILE_EXTENSION)) 
                    && !filesToKeep.contains(name)) {
                    Files.delete(p);
                }
            }
        }
    }
}
