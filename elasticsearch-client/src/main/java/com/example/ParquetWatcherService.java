package com.example;

import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;

public class ParquetWatcherService {

    private final Path baseDir;

    private final WeatherIndexer weatherIndexer;

    private final WatchService watchService;

    private final ExecutorService indexingPool =
            Executors.newFixedThreadPool(4);

    private final Set<Path> inFlightFiles =
            ConcurrentHashMap.newKeySet();

    public ParquetWatcherService(
            Path baseDir,
            WeatherIndexer weatherIndexer
    ) throws Exception {

        this.baseDir = baseDir;
        this.weatherIndexer = weatherIndexer;

        this.watchService =
                FileSystems.getDefault().newWatchService();

        registerAll(baseDir);

        initialScan();
    }

    private void registerAll(Path start) throws Exception {

        Files.walk(start)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        dir.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE
                        );
                        System.out.println("Watching: " + dir);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    private void initialScan() throws Exception {
        try {
            Files.walk(baseDir)
                    .filter(Files::isRegularFile)
                    .filter(this::isParquetFile)
                    .forEach(this::submitIndexingTask);
        } catch (Exception e) {
            throw new Exception(e);
        }
    }

    public void start() throws Exception {

        while (true) {

            WatchKey key = watchService.take();

            Path dir = (Path) key.watchable();

            for (WatchEvent<?> event : key.pollEvents()) {

                WatchEvent.Kind<?> kind = event.kind();

                Path relativePath = (Path) event.context();

                Path fullPath = dir.resolve(relativePath);

                System.out.println(kind + ": " + fullPath);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE
                        && Files.isDirectory(fullPath)) {

                    registerAll(fullPath);
                }

                if (kind == StandardWatchEventKinds.ENTRY_CREATE
                        && Files.isRegularFile(fullPath)
                        && isParquetFile(fullPath)) {

                    submitIndexingTask(fullPath);
                }
            }

            key.reset();
        }
    }

    private boolean isParquetFile(Path path) {

        String name = path.getFileName().toString();

        return name.endsWith(".parquet")
                && !name.startsWith(".")
                && !name.startsWith("_");
    }

    private void submitIndexingTask(Path parquetPath) {

        Path canonical;
        try {
            canonical = parquetPath.toRealPath();
        } catch (Exception e) {
            canonical = parquetPath.toAbsolutePath().normalize();
        }

        if (!inFlightFiles.add(canonical)) {
            System.out.println("Skipping already-queued: " + canonical);
            return;
        }

        final Path finalPath = canonical;

        indexingPool.submit(() -> {
            try {
                Thread.sleep(2000);
                weatherIndexer.indexParquetFile(finalPath.toFile());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                inFlightFiles.remove(finalPath);
            }
        });
    }
}