package ru.bell.autoleasing.utilities.files;

import ru.bell.autoleasing.exceptions.DataAccessException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Stream;

public class FileManager {
    private static final ConcurrentHashMap<String, ReadWriteLock> fileLocks = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ExecutorService> fileExecutors = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, AtomicBoolean> operationInProgress = new ConcurrentHashMap<>();

    private static ReadWriteLock getFileLock(String filePath) {
        return fileLocks.computeIfAbsent(filePath, k -> new ReentrantReadWriteLock());
    }

    private static ExecutorService getFileExecutor(String filePath) {
        return fileExecutors.computeIfAbsent(filePath,
                k -> Executors.newSingleThreadExecutor(
                r -> new Thread(r, "FileOperation-" + Path.of(k).getFileName())));
    }

    private static AtomicBoolean isFileOperationInProgress(String filePath) {
        return operationInProgress.computeIfAbsent(filePath, k -> new AtomicBoolean(false));
    }

    public static boolean isFileReady(String filePath) {
        ExecutorService executor = fileExecutors.get(filePath);
        if (executor == null) {
            return true;
        }
        return !isFileOperationInProgress(filePath).get() && !executor.isShutdown();
    }

    public static void ensureFileExists(String filePath) {
        ReadWriteLock lock = getFileLock(filePath);
        lock.writeLock().lock();
        try {
            Path path = Path.of(filePath);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.notExists(path)) {
                try {
                    Files.createFile(path);
                } catch (FileAlreadyExistsException ignored) {}
            }
        } catch (IOException e) {
            throw new DataAccessException("Не удалось создать файл/директорию для данных: " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static <T> void updateFileInBackground(String filePath, String entityHeader, List<T> entities, ToFileString<T> toFileString) {
        ExecutorService executor = getFileExecutor(filePath);
        AtomicBoolean tracker = isFileOperationInProgress(filePath);

        executor.execute(() -> {
            tracker.set(true);
            updateFile(filePath, entityHeader, entities, toFileString);
            tracker.set(false);
        });
    }

    public static <T> void updateFile(String filePath, String entityHeader, List<T> entities, ToFileString<T> toFileString) {
        ReadWriteLock lock = getFileLock(filePath);
        lock.writeLock().lock();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
                writer.write(entityHeader);
                writer.newLine();

                for (T entity : entities) {
                    writer.write(toFileString.apply(entity));
                    writer.newLine();
                }
                writer.flush();
        } catch (IOException e) {
            System.err.println("Ошибка записи в файл " + filePath + " :" + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static <K, V> Future<?> updateMapFromFileInBackground(String filePath,
                                                                 Map<K, V> map,
                                                                 FromFileString<V> fromFileString,
                                                                 Function<? super V, ? extends K> keyExtractor) {
        ExecutorService executor = getFileExecutor(filePath);
        AtomicBoolean tracker = isFileOperationInProgress(filePath);

        return executor.submit(() -> {
            tracker.set(true);
            updateMapFromFile(filePath, map, fromFileString, keyExtractor);
            tracker.set(false);
        });
    }

    public static <K, V> void updateMapFromFile(
            String filePath,
            Map<K, V> map,
            FromFileString<V> fromFileString,
            Function<? super V, ? extends K> keyExtractor) {
        ReadWriteLock lock = getFileLock(filePath);
        lock.readLock().lock();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(filePath))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String lineTrimmed = line.trim();

                if (lineTrimmed.isEmpty() || lineTrimmed.startsWith("#")) continue;

                try {
                    V value = fromFileString.apply(lineTrimmed);
                    K key = keyExtractor.apply(value);
                    map.put(key, value);
                } catch (RuntimeException e) {
                    System.err.println("Ошибка чтения строки " + lineNumber + " в файле " + filePath + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка чтения файла " + filePath + " : " + e.getMessage());
        } finally {
            lock.readLock().unlock();
        }
    }

    public static Long findMaxID(String filePath) {
        ReadWriteLock lock = getFileLock(filePath);
        lock.readLock().lock();
        try (Stream<String> lines = Files.lines(Path.of(filePath))) {
            return lines
                    .skip(1)
                    .map(line ->  line.replaceAll("\\s", "").split("\\|")[0])
                    .mapToLong(Long::parseLong)
                    .max()
                    .orElse(0L);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Ошибка при чтении ID из файла %s: %s", filePath, e.getMessage()), e);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static void close() {
        for (Map.Entry<String, ExecutorService> entry : fileExecutors.entrySet()) {
            try {
                entry.getValue().close();
            } catch (Exception e) {
                System.err.println("Ошибка закрытия сервиса выполнения " + entry.getKey() + ": " + e.getMessage());
            }
        }

        fileExecutors.clear();
        operationInProgress.clear();
        fileLocks.clear();
    }

    public static void printAllFileStatus() {
        System.out.println("=== Статусы операций с файлами ===");
        for (String filePath : fileExecutors.keySet()) {
            System.out.println(getFileOperationStatus(filePath));
        }
        System.out.println("============================");
    }

    public static String getFileOperationStatus(String filePath) {
        ExecutorService executor = fileExecutors.get(filePath);
        boolean inProgress = isFileOperationInProgress(filePath).get();

        if (executor == null) {
            return "Для файла " + filePath + " не назначен сервис выполнения";
        }

        return String.format("Файл: %s | Занят: %s | Shutdown: %s | Terminated: %s",
                filePath, inProgress, executor.isShutdown(), executor.isTerminated());
    }
}
