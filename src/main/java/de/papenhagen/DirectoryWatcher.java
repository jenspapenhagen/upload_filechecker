package de.papenhagen;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class DirectoryWatcher implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(DirectoryWatcher.class.getName());

    public enum Event {
        ENTRY_CREATE,
        ENTRY_MODIFY,
        ENTRY_DELETE
    }

    private static final Map<WatchEvent.Kind<Path>, Event> EVENT_MAP
        = Map.of(ENTRY_CREATE, Event.ENTRY_CREATE,
                 ENTRY_MODIFY, Event.ENTRY_MODIFY,
                 ENTRY_DELETE, Event.ENTRY_DELETE);

    private ExecutorService mExecutor;
    private Future<?> mWatcherTask;

    private final Set<Path> mWatched;
    private final boolean mPreExistingAsCreated;
    private final Listener mListener;

    public DirectoryWatcher(final Builder builder) {
        mWatched = builder.mWatched;
        mPreExistingAsCreated = builder.mPreExistingAsCreated;
        mListener = builder.mListener;
    }

    //start the service
    public void start() {
        mExecutor = Executors.newSingleThreadExecutor();
        mWatcherTask = mExecutor.submit(this);
    }

    //stop the service
    public void stop() {
        mWatcherTask.cancel(true);
        mWatcherTask = null;
        mExecutor.shutdown();
        mExecutor = null;
    }

    @Override
    public void run() {
        final WatchService watchService;
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (final IOException ioe) {
            throw new RuntimeException("Exception while creating watch service.", ioe);
        }
        final Map<WatchKey, Path> watchKeyToDirectory = new HashMap<>();

        for (final Path dir1 : mWatched) {
            try {
                if (mPreExistingAsCreated) {
                    try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir1)) {
                        for (final Path path : stream) {
                            mListener.onEvent(Event.ENTRY_CREATE, dir1.resolve(path));
                        }
                    }
                }

                final WatchKey key = dir1.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE);
                watchKeyToDirectory.put(key, dir1);
            } catch (final IOException ioe) {
                LOGGER.severe("Not watching " + dir1 + "Exception: " + ioe);
            }
        }

        while (true) {
            if (Thread.interrupted()) {
                LOGGER.info("Directory watcher thread interrupted.");
                break;
            }

            final WatchKey key;
            try {
                key = watchService.take();
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                continue;
            }

            final Path dir = watchKeyToDirectory.get(key);
            if (dir == null) {
                LOGGER.warning("Watch key not recognized.");
                continue;
            }

            for (final WatchEvent<?> event : key.pollEvents()) {
                if (event.kind().equals(OVERFLOW)) {
                    break;
                }

                final WatchEvent<Path> pathEvent = cast(event);
                final WatchEvent.Kind<Path> kind = pathEvent.kind();

                final Path path = dir.resolve(pathEvent.context());
                if (EVENT_MAP.containsKey(kind)) {
                    mListener.onEvent(EVENT_MAP.get(kind), path);
                }
            }

            final boolean valid = key.reset();
            if (!valid) {
                watchKeyToDirectory.remove(key);
                LOGGER.warning(dir + " is inaccessible. Stopping watch.");
                if (watchKeyToDirectory.isEmpty()) {
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(final WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    /**
     * small Listener Interface for a cleaner builder setup.
     */
    public interface Listener {

        void onEvent(final Event event, final Path path);
    }

    public static class Builder {

        private final Set<Path> mWatched = new HashSet<>();
        private boolean mPreExistingAsCreated = false;
        private Listener mListener;

        public Builder addDirectories(final String dirPath) {
            return addDirectories(Paths.get(dirPath));
        }

        public Builder addDirectories(final Path dirPath) {
            mWatched.add(dirPath);
            return this;
        }

        public Builder addDirectories(final Path... dirPaths) {
            Collections.addAll(mWatched, dirPaths);
            return this;
        }

        public Builder addDirectories(final Iterable<? extends Path> dirPaths) {
            for (Path dirPath : dirPaths) {
                mWatched.add(dirPath);
            }
            return this;
        }

        public Builder setPreExistingAsCreated(final boolean value) {
            mPreExistingAsCreated = value;
            return this;
        }

        public DirectoryWatcher build(final Listener listener) {
            mListener = listener;
            return new DirectoryWatcher(this);
        }
    }
}
