package battleaimod.networking;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplaySnapshotStoreTest {
    @Test
    public void createsDistinctPlanDirectoriesAndOnlyClearsPreviousPlans() throws Exception {
        Path workspace = Files.createTempDirectory("replay-snapshot-store-");
        try {
            Path firstPlan = ReplaySnapshotStore.createPlanDirectory(workspace.toString());
            Path secondPlan = ReplaySnapshotStore.createPlanDirectory(workspace.toString());
            Path legacySnapshot = workspace.resolve("savestates").resolve("0.txt");
            Files.createFile(legacySnapshot);

            assertFalse(firstPlan.equals(secondPlan));
            assertTrue(Files.isDirectory(firstPlan));
            assertTrue(Files.isDirectory(secondPlan));

            ReplaySnapshotStore.clearPreviousPlans(workspace.toString());

            assertFalse(Files.exists(firstPlan));
            assertFalse(Files.exists(secondPlan));
            assertTrue(Files.exists(legacySnapshot));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private static void deleteRecursively(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted((first, second) -> second.getNameCount() - first.getNameCount())
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                     } catch (Exception e) {
                         throw new RuntimeException(e);
                     }
                 });
        }
    }
}
