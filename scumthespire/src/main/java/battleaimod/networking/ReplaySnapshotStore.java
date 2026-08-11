package battleaimod.networking;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

/** Stores immutable replay snapshots in a unique directory for each emitted path. */
public final class ReplaySnapshotStore {
    static final String ROOT_DIRECTORY = "savestates";
    static final String PLAN_DIRECTORY_PREFIX = "plan-";

    private ReplaySnapshotStore() {
    }

    public static Path createPlanDirectory(String clientCwd) throws IOException {
        Path root = snapshotRoot(clientCwd);
        Files.createDirectories(root);

        Path planDirectory = root.resolve(PLAN_DIRECTORY_PREFIX + UUID.randomUUID()).normalize();
        if (!root.equals(planDirectory.getParent())) {
            throw new IOException("Invalid replay snapshot directory: " + planDirectory);
        }
        return Files.createDirectory(planDirectory);
    }

    public static void clearPreviousPlans(String clientCwd) throws IOException {
        Path root = snapshotRoot(clientCwd);
        if (!Files.isDirectory(root)) {
            return;
        }

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root)) {
            for (Path entry : entries) {
                Path fileName = entry.getFileName();
                if (Files.isDirectory(entry) && fileName != null &&
                        fileName.toString().startsWith(PLAN_DIRECTORY_PREFIX)) {
                    deletePlanDirectory(root, entry);
                }
            }
        }
    }

    private static Path snapshotRoot(String clientCwd) {
        return Paths.get(clientCwd).resolve(ROOT_DIRECTORY).toAbsolutePath().normalize();
    }

    private static void deletePlanDirectory(Path root, Path planDirectory) throws IOException {
        Path normalizedPlanDirectory = planDirectory.toAbsolutePath().normalize();
        if (!root.equals(normalizedPlanDirectory.getParent()) ||
                !normalizedPlanDirectory.getFileName().toString().startsWith(PLAN_DIRECTORY_PREFIX)) {
            throw new IOException("Refusing to delete non-plan replay snapshot directory: " + planDirectory);
        }

        Files.walkFileTree(normalizedPlanDirectory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
