package com.example;

import java.io.IOException;
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

    private boolean isRunning = true;
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

    public void start() {
        System.out.println("Watcher Started Successfully");

        try {
            while (isRunning) {

                WatchKey key = watchService.take();

                Path dir = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {

                    WatchEvent.Kind<?> kind = event.kind();

                    Path relativePath = (Path) event.context();

                    Path fullPath = dir.resolve(relativePath);

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
        } catch (ClosedWatchServiceException e) {
            System.out.println("Watch service closed.");
        } catch (Exception e) {
            e.printStackTrace();
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
    public void stop() throws InterruptedException {

        System.out.println("Stopping watcher...");
        isRunning = false;

        try {
            watchService.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Shutting down indexing pool...");
        indexingPool.shutdown();

        if (!indexingPool.awaitTermination(60, TimeUnit.SECONDS)) {
            System.out.println("Forcing Indexing Pool Shutdown...");
            indexingPool.shutdownNow();
        }

        System.out.println("Watcher stopped.");
    }
}