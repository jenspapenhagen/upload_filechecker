package de.papenhagen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import static java.util.Objects.isNull;

public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {

        final String uploadFiles = System.getProperty("uploadfiles");

        //build up the path
        final Path currentWorkingDir = Paths.get("").toAbsolutePath();
        final Path watchPath = Paths.get(currentWorkingDir.toString(), isNull(uploadFiles) ? "uploadfiles" : uploadFiles);

        if(!Files.exists(watchPath)) {
            LOGGER.severe("Path not existing");
            return;
        }

        final Uploader uploader = new Uploader();

        final DirectoryWatcher watcher = new DirectoryWatcher.Builder()
                .addDirectories(watchPath)
                .setPreExistingAsCreated(true)
                .build((event, path) -> {
                    switch (event) {
                        case ENTRY_CREATE:
                            LOGGER.info(path + " created.");
                            uploader.uploadToTika(path);
                            break;
                        case ENTRY_MODIFY:
                            LOGGER.info(path + " modified.");
                            break;
                        case ENTRY_DELETE:
                            LOGGER.info(path + " deleted.");
                            break;
                        default:
                            LOGGER.severe("default event");
                            break;
                    }
                });

        try {
            // Actual directory watching starts here
            watcher.start();

        } catch (Exception e) {
            LOGGER.severe("Exception on watching run");
            // Stop watching
            watcher.stop();
        }

    }

}