import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Build {
    public static void main(String[] args) throws IOException {
        Path projectDir = getProjectDir();
        Path steamPath = Paths.get("D:\\Program Files\\Slay the Spire");
        Path modsPath = Paths.get("D:\\Program Files\\Slay the Spire\\mods");

        Path desktopJar = steamPath.resolve("desktop-1.0.jar");
        Path modTheSpireJar = steamPath.resolve("ModTheSpire.jar");
        Path baseModJar = modsPath.resolve("BaseMod.jar");

        Path targetDir = projectDir.resolve("target");
        Path classesDir = targetDir.resolve("classes");
        Path jarFile = targetDir.resolve("CommunicationMod.jar");

        // Clean
        if (Files.exists(targetDir)) {
            deleteDirectory(targetDir);
        }
        Files.createDirectories(classesDir);

        // Source files
        List<String> sourceFiles;
        try (Stream<Path> walk = Files.walk(projectDir.resolve("src/main/java"))) {
            sourceFiles = walk.filter(p -> p.toString().endsWith(".java"))
                              .map(Path::toString)
                              .collect(Collectors.toList());
        }

        // Classpath
        String classpath = desktopJar + ";" + modTheSpireJar + ";" + baseModJar;

        // Compile
        System.out.println("Compiling...");
        ToolProvider javac = ToolProvider.findFirst("javac").orElseThrow(() -> new RuntimeException("javac not found"));
        List<String> javacArgs = new ArrayList<>();
        javacArgs.add("-d");
        javacArgs.add(classesDir.toString());
        javacArgs.add("-cp");
        javacArgs.add(classpath);
        // Add --release 8 to ensure compatibility with Java 8 (which Slay the Spire uses)
        javacArgs.add("--release");
        javacArgs.add("8");
        javacArgs.addAll(sourceFiles);

        int result = javac.run(System.out, System.err, javacArgs.toArray(new String[0]));
        if (result != 0) {
            System.err.println("Compilation failed!");
            System.exit(1);
        }

        // Copy resources
        System.out.println("Copying resources...");
        Path resourcesDir = projectDir.resolve("src/main/resources");
        if (Files.exists(resourcesDir)) {
            copyDirectory(resourcesDir, classesDir);
        }

        // Package JAR
        System.out.println("Packaging JAR...");
        ToolProvider jar = ToolProvider.findFirst("jar").orElseThrow(() -> new RuntimeException("jar not found"));
        List<String> jarArgs = new ArrayList<>();
        jarArgs.add("cf");
        jarArgs.add(jarFile.toString());
        jarArgs.add("-C");
        jarArgs.add(classesDir.toString());
        jarArgs.add(".");

        result = jar.run(System.out, System.err, jarArgs.toArray(new String[0]));
        if (result != 0) {
            System.err.println("Packaging failed!");
            System.exit(1);
        }

        System.out.println("Build successful! JAR created at: " + jarFile);
    }

    private static Path getProjectDir() {
        try {
            return Paths.get(Build.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
        } catch (Exception e) {
            return Paths.get("").toAbsolutePath().normalize();
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (!Files.exists(targetDir)) {
                    Files.createDirectory(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
