package io.github.keveon.rendoc.conversion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

final class TemporaryFiles {

    private TemporaryFiles() {}

    static void deleteRecursively(Path root) throws IOException {
        if (root == null) {
            return;
        }

        var visitor = new CleanupVisitor();
        try {
            Files.walkFileTree(root, visitor);
        } catch (NoSuchFileException exception) {
            return;
        }
        visitor.throwIfFailed();
    }

    static void scheduleDeletionOnExit(Path root) {
        if (root == null) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.comparingInt(Path::getNameCount))
                    .forEach(path -> path.toFile().deleteOnExit());
        } catch (IOException | UncheckedIOException exception) {
            root.toFile().deleteOnExit();
        }
    }

    private static final class CleanupVisitor extends SimpleFileVisitor<Path> {

        private IOException failure;

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            delete(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exception) {
            if (exception instanceof NoSuchFileException) {
                return FileVisitResult.CONTINUE;
            }
            record(exception);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) {
            if (exception != null) {
                record(exception);
            }
            delete(directory);
            return FileVisitResult.CONTINUE;
        }

        private void delete(Path path) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException exception) {
                record(exception);
            }
        }

        private void record(IOException exception) {
            if (failure == null) {
                failure = new IOException("Could not fully clean a conversion workspace");
            }
            failure.addSuppressed(exception);
        }

        private void throwIfFailed() throws IOException {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
