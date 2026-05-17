package com.example;

import javax.annotation.processing.Processor;
import java.io.IOException;
import java.nio.file.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ParquetWatcherService {

    private final Path baseDir;
    private final WeatherIndexer weatherIndexer;
    private final WatchService watchService;

    // Set the threads for only 90% of the available cores to avoid CPU resources saturation
    private final ExecutorService indexingPool =
            Executors.newFixedThreadPool((int) (Runtime.getRuntime().availableProcessors()*0.9));

    // Save read files to avoid reread them.
    private final Set<Path> inFlightFiles =
            ConcurrentHashMap.newKeySet();

    // Atomic variable for maintaining shutdown between different threads
    private final AtomicBoolean shutdownStarted =
            new AtomicBoolean(false);

    // System Running Var.
    private volatile boolean isRunning = true;

    public ParquetWatcherService(
            Path baseDir,
            WeatherIndexer weatherIndexer
    ) throws Exception {
        try {
            this.baseDir = baseDir;
            this.weatherIndexer = weatherIndexer;
            this.watchService = FileSystems.getDefault().newWatchService();
            registerAll(baseDir);
            initialScan();
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    // Given certain path, register all its directories to be monitored.
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

    // The very initial scan
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

    // Watcher Process start
    public void start() {
        System.out.println("Watcher Started Successfully");
        try {
            while (isRunning) {
                WatchKey key = watchService.take();
                Path dir = (Path) key.watchable();

                // Loop over triggered events and check if there are new created files/directories.
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    Path relativePath = (Path) event.context();
                    Path fullPath = dir.resolve(relativePath);

                    // If new directory is created, register it to the watcher.
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE
                            && Files.isDirectory(fullPath)) {
                        registerAll(fullPath);
                    }

                    // If new parquet file created, index it
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE
                            && Files.isRegularFile(fullPath)
                            && isParquetFile(fullPath)) {
                        submitIndexingTask(fullPath);
                    }
                }

                // Remove processed events.
                key.reset();
            }
        } catch (ClosedWatchServiceException e) {
            System.out.println("Watch service closed.");
        } catch (Exception e) {
            isRunning = false;
            throw new RuntimeException(e);
        }
    }

    private boolean isParquetFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".parquet")
                && !name.startsWith(".")
                && !name.startsWith("_");
    }

    // Assign the indexing task to the thread pool, and handle the exceptions in the task level to avoid crashing the watcher.
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
                if (shutdownStarted.compareAndSet(false, true)) {
                    System.err.println(
                            "Fatal indexing error: " + finalPath + " - " + e.getMessage()
                    );
                    isRunning = false;
                    try {
                        watchService.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
                throw new RuntimeException(e);
            } finally {
                inFlightFiles.remove(finalPath);
            }
        });
    }

    // Stop function to shutdown the watcher and the thread pool gracefully, and wait for the indexing tasks to finish before exiting.
    public void stop() throws InterruptedException {
        if (!shutdownStarted.compareAndSet(false, true)) {
            indexingPool.awaitTermination(60, TimeUnit.SECONDS);
            return;
        }

        isRunning = false;
        System.out.println("[Shutdown] Initiating shutdown sequence...");

        weatherIndexer.shutdown();

        System.out.println("[Shutdown] Stopping watcher...");
        try {
            watchService.close();
            System.out.println("[Shutdown] Watch service closed successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("[Shutdown] Shutting down indexing pool...");
        indexingPool.shutdown();

        if (!indexingPool.awaitTermination(60, TimeUnit.SECONDS)) {
            indexingPool.shutdownNow();
        }

        System.out.println("[Shutdown] Indexing Pool stopped successfully.");
    }
}