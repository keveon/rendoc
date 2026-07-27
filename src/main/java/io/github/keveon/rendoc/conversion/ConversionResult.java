package io.github.keveon.rendoc.conversion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConversionResult implements AutoCloseable {

    @FunctionalInterface
    interface Cleanup {
        void delete(Path directory) throws IOException;
    }

    private final Path path;
    private final Path ownedDirectory;
    private final Cleanup cleanup;
    private boolean closed;

    private ConversionResult(Path path, Path ownedDirectory, Cleanup cleanup) {
        this.path = path;
        this.ownedDirectory = ownedDirectory;
        this.cleanup = cleanup;
    }

    public static ConversionResult owned(Path path, Path ownedDirectory) {
        return owned(path, ownedDirectory, TemporaryFiles::deleteRecursively);
    }

    static ConversionResult owned(Path path, Path ownedDirectory, Cleanup cleanup) {
        return new ConversionResult(path, ownedDirectory, cleanup);
    }

    public Path path() {
        return path;
    }

    public void transferTo(OutputStream output) throws IOException {
        try (var input = Files.newInputStream(path)) {
            input.transferTo(output);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        try {
            cleanup.delete(ownedDirectory);
            closed = true;
        } catch (IOException exception) {
            TemporaryFiles.scheduleDeletionOnExit(ownedDirectory);
            throw exception;
        }
    }
}
