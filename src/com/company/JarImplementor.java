package com.company;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;
import org.jetbrains.annotations.NotNull;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 *
 */

public class JarImplementor extends Implementor implements JarImpler {
    private Path tempDir;

    public static void main(String[] args) throws IllegalAccessException, IOException {
        if (args.length != 2 && args.length != 3) {
            System.err.println("Expected 2 or 3 arguments, given " + args.length);
            System.exit(1);
        }

        if (Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println("Arguments cannot be null");
            System.exit(1);
        }

        boolean implementJar = false;
        if (args.length == 3) {
            if ("-jar".equals(args[0].trim())) {
                implementJar = true;
                System.out.println("Packing result into JAR");
            } else {
                System.err.println("Unknown command: " + args[0]);
                System.exit(1);
            }
        }

        JarImplementor implementor = new JarImplementor();
        try {
            if (implementJar) {
                implementor.implementJar(Class.forName(args[1]), Paths.get(args[2]).toAbsolutePath());
            } else {
                implementor.implement(Class.forName(args[0]), Paths.get(args[1]).toAbsolutePath());
            }
        } catch (ClassNotFoundException e) {
            System.err.println("Implementation error caused: couldn't found class " + e.getMessage());
            System.exit(1);
        } catch (ImplerException e) {
            System.err.println("Implementation error caused: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Implementation succeed");
        System.exit(0);
    }

    private static Path createFile(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!path.toFile().exists()) {
            Files.createFile(path);
        }
        return path;
    }

    private static Path createTempDirectory(Path root) throws IOException {
        String randomDirectoryName = "__temp_" + getRandomAlNumString() + "__";
        return Files.createTempDirectory(root, randomDirectoryName);
    }

    private static String getRandomAlNumString() {
        final String supportedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        char[] chars = new char[10];
        Random random = new Random();
        for (int i = 0; i < chars.length; ++i) {
            chars[i] = supportedChars.charAt(random.nextInt(supportedChars.length()));
        }
        return new String(chars);
    }

    private void compileJavaClass(@NotNull Class<?> token) throws ImplerException {

        String javaSourcePath = tempDir.resolve(token.getPackageName().replace('.', File.separatorChar))
                .resolve(token.getSimpleName() + "Impl" + ".java").toString();

        String cp;
        try {
            CodeSource source = token.getProtectionDomain().getCodeSource();
            if (source == null) {
                cp = ".";
            } else {
                cp = Path.of(source.getLocation().toURI()).toString();
            }
        } catch (final URISyntaxException e) {
            throw new ImplerException("Cannot resolve classpath" + e.getMessage());
        }

        JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        var args = new String[]{"-cp", System.getProperty("java.class.path") + File.pathSeparator + javaSourcePath
                + File.pathSeparator + cp, javaSourcePath
        };


        if (javaCompiler == null || javaCompiler.run(null, null, null, args) != 0) {
            throw new ImplerException("Error during compiling generated java classes");
        }
    }

    private void createJarFile(@NotNull Path rootofJar, Class<?> token) throws IOException, ImplerException {
        add(rootofJar, token);
        //jarOutputStream.close();

    }

    private void add(Path target, Class<?> token) throws IOException, ImplerException {

        Manifest manifest = new Manifest();
        String version = "1.0.0";
        String author = InetAddress.getLocalHost().getHostName();
        Attributes global = manifest.getMainAttributes();
        global.put(Attributes.Name.MANIFEST_VERSION, version);
        global.put(new Attributes.Name("Created-By"), author);
        try (JarOutputStream outputStream = new JarOutputStream(Files.newOutputStream(createFile(target)), manifest)) {

            String zipEntryPath = token.getPackageName().replace('.', '/') +
                    "/" + token.getSimpleName() + "Impl" + ".class";

            Path binaryPath = Paths.get(tempDir.toString(),
                    token.getPackageName().replace('.', File.separatorChar), token.getSimpleName() + "Impl" + ".class");

            outputStream.putNextEntry(new ZipEntry(zipEntryPath));
            Files.copy(binaryPath, outputStream);

        } catch (IOException e) {
            throw new ImplerException("Jar creation error", e);
        } finally {
            deleteTempDir();
        }
    }

    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException, IllegalAccessException, IOException {
        checkPossibility(token);
        tempDir = createTempDirectory(jarFile.toAbsolutePath().getParent());
        implement(token, tempDir);
        compileJavaClass(token);
        createJarFile(jarFile, token);
    }

    private void deleteTempDir() {
        try {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path file, IOException e) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Temp directory deleting error");
        }
    }
}
