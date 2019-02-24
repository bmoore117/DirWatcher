package com.company;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        Path path = Paths.get("C:\\Users\\moore\\Documents\\delete");

        DirWatcher watcher = new DirWatcher(path, 3000);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        var test = executorService.submit(watcher);

        Thread.sleep(5000);

        System.exit(0);
    }
}
