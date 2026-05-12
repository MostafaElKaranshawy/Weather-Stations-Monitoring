package com.example.storage.bitcask;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Map;

public class BitCaskFileIO {
    public static final String DATA_FILE_EXTENSION = ".data";
    public static final String HINT_FILE_EXTENSION = ".hint";

    public static void writeHintFile(Path directory, String fileId, Map<String, IndexEntry> keyDir) throws IOException {
        Path hintPath = directory.resolve(fileId.replace(DATA_FILE_EXTENSION, HINT_FILE_EXTENSION));
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(hintPath.toFile()))) {
            for (Map.Entry<String, IndexEntry> entry : keyDir.entrySet()) {
                if (entry.getValue().fileId().equals(fileId)) {
                    dos.writeLong(entry.getValue().timestamp());
                    byte[] keyBytes = entry.getKey().getBytes();
                    dos.writeInt(keyBytes.length);
                    dos.writeInt(entry.getValue().valueSize());
                    dos.writeLong(entry.getValue().valuePosition());
                    dos.write(keyBytes);
                }
            }
        }
    }

    public static byte[] readValue(Path directory, IndexEntry entry) throws IOException {
        Path path = directory.resolve(entry.fileId());
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            raf.seek(entry.valuePosition());
            byte[] value = new byte[entry.valueSize()];
            raf.readFully(value);
            return value;
        }
    }

    public static void writeRecordToChannel(FileChannel channel, long timestamp, byte[] keyBytes, byte[] value) throws IOException {
        int keySize = keyBytes.length;
        int valueSize = value.length;
        int totalSize = 16 + keySize + valueSize;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.putLong(timestamp);
        buffer.putInt(keySize);
        buffer.putInt(valueSize);
        buffer.put(keyBytes);
        buffer.put(value);
        buffer.flip();

        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
