package com.company;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

import static java.nio.file.StandardWatchEventKinds.*;

public class DirWatcher implements Runnable {

    private WatchService watchService;
    private final Set<String> watchedDirectories;
    private HashMap<String, Timer> debounceMap;
    private HashMap<WatchKey, Path> keys;
    private final int sleepTime;

    public DirWatcher(Path dir, int sleepTime) throws IOException {
        this.sleepTime = sleepTime;
        this.watchedDirectories = new HashSet<>();
        this.debounceMap = new HashMap<>();
        keys = new HashMap<>();
        watchService = FileSystems.getDefault().newWatchService();
        registerAll(dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
            {
                System.out.println("Registering directory " + dir.toString());
                watchedDirectories.add(dir.toString());
                WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE, OVERFLOW);
                keys.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        boolean shouldContinue = true;
        while (shouldContinue) {
            try {
                WatchKey key = watchService.take();

                Path path = keys.get(key);
                if (path == null) {
                    System.err.println("WatchKey not recognized!!");
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == OVERFLOW) {
                        continue;
                    }

                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    Path absolute;
                    if (ev.context().isAbsolute()) {
                        absolute = ev.context();
                    } else {
                        absolute = path.resolve(filename);
                    }
                    File file = absolute.toFile();
                    if (file.isDirectory()) {
                        //System.out.println(kind + " with count " + ev.count() + " for directory " + absolute.toString());
                        // later timer tasks modify this, don't want collisions
                        synchronized (watchedDirectories) {
                            if (!watchedDirectories.contains(absolute.toString())) {
                                registerAll(absolute);
                            }
                        }
                    } else {
                        //System.out.println(kind + " with count " + ev.count() + " for file " + absolute.toString());
                        Timer timer = debounceMap.get(absolute.toString());
                        if (timer != null) {
                            timer.cancel();
                        }

                        timer = new Timer();
                        timer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                if (kind == ENTRY_CREATE) {
                                    System.out.println("File " + absolute.toString() + " was created");
                                } else if (kind == ENTRY_MODIFY) {
                                    if (absolute.toFile().exists()) {
                                        System.out.println("File " + absolute.toString() + " was modified");
                                    } else {
                                        System.out.println("File " + absolute.toString() + " was deleted");
                                    }
                                } else {
                                    if (watchedDirectories.contains(absolute.toString())) {
                                        System.out.println("Watched directory " + absolute.toString() + " was deleted");
                                        synchronized (watchedDirectories) {
                                            watchedDirectories.remove(absolute.toString());
                                        }
                                    } else {
                                        System.out.println("File " + absolute.toString() + " was deleted");
                                    }
                                }
                            }
                        }, sleepTime);
                        debounceMap.put(absolute.toString(), timer);
                    }
                }

                key.reset();
            } catch (InterruptedException e) {
                System.out.println("Watch service interrupted, exit requested");
                for (Timer t : debounceMap.values()) {
                    t.cancel();
                }
                shouldContinue = false;
            } catch (IOException e) {
                System.out.println("Error registering directory");
                e.printStackTrace();
            }
        }
    }
}
