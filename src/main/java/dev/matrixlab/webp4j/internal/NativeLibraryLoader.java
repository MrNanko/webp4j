package dev.matrixlab.webp4j.internal;

import dev.matrixlab.webp4j.exception.NativeLibraryNotFoundException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class NativeLibraryLoader {

    private NativeLibraryLoader() {
        throw new AssertionError("Cannot instantiate utility class.");
    }

    public static void loadLibrary() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        String libraryFileName = getLibraryFileName(os, arch);

        // Path to the library in the jar
        String resourcePath = String.format("/native/%s", libraryFileName);

        // Get the library from the jar
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new NativeLibraryNotFoundException(
                        String.format("Native library not found: %s%n" +
                                        "OS: %s, Architecture: %s%n" +
                                        "Expected path: %s%n" +
                                        "Supported platforms: Linux (x64/aarch64/arm), macOS (x64/arm64), Windows (x64)",
                                libraryFileName, os, arch, resourcePath)
                );
            }

            File tempLibraryFile = Files.createTempFile("webp4j-", "-" + libraryFileName).toFile();

            // Register a cleanup hook
            final File fileToDelete = tempLibraryFile;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (fileToDelete.exists()) {
                        Files.delete(fileToDelete.toPath());
                    }
                } catch (Exception e) {
                    // Ignore cleanup failures during JVM shutdown
                }
            }));

            try (FileOutputStream out = new FileOutputStream(tempLibraryFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            // Load the library
            System.load(tempLibraryFile.getAbsolutePath());

        } catch (IOException e) {
            throw new NativeLibraryNotFoundException("Could not load native WebP library", e);
        }
    }

    /**
     * Build the native library filename based on OS and CPU architecture.
     *
     * @param os   operating system name (lowercase)
     * @param arch CPU architecture name (lowercase)
     * @return library filename, e.g. "libwebp4j-mac-arm64.dylib"
     */
    private static String getLibraryFileName(String os, String arch) {
        String platform;
        String architecture;
        String prefix;
        String extension;

        if (os.contains("win")) {
            platform = "windows";
            architecture = normalizeArchitecture(arch, false);
            prefix = "";  // Windows does not use the 'lib' prefix
            extension = "dll";
        } else if (os.contains("nux") || os.contains("linux")) {
            platform = "linux";
            architecture = normalizeArchitecture(arch, false);
            prefix = "lib";  // Linux uses the 'lib' prefix
            extension = "so";
        } else if (os.contains("mac")) {
            platform = "mac";
            architecture = normalizeArchitecture(arch, true);
            prefix = "lib";  // macOS uses the 'lib' prefix
            extension = "dylib";
        } else {
            throw new UnsupportedOperationException(String.format("Unsupported os: %s, arch: %s", os, arch));
        }

        // Naming rule:
        //   <prefix>webp4j-<platform>-<arch>.<extension>
        // Examples:
        //   Windows x64   -> webp4j-windows-x64.dll
        //   Linux x64     -> libwebp4j-linux-x64.so
        //   Linux aarch64 -> libwebp4j-linux-aarch64.so
        //   Linux arm     -> libwebp4j-linux-arm.so
        //   macOS x64     -> libwebp4j-mac-x64.dylib
        //   macOS arm64   -> libwebp4j-mac-arm64.dylib

        return String.format("%swebp4j-%s-%s.%s", prefix, platform, architecture, extension);
    }

    /**
     * Normalize architecture name to a unified format used by this project.
     *
     * @param arch  original architecture name (lowercase)
     * @param isMac whether the platform is macOS
     * @return normalized architecture name
     */
    private static String normalizeArchitecture(String arch, boolean isMac) {
        // Check for 64-bit x86 architecture (x86-64/amd64/x64)
        boolean is64BitX86Architecture = arch.equals("amd64") ||
                arch.equals("x86_64") ||
                arch.equals("x64");

        if (is64BitX86Architecture) {
            return "x64";
        }

        // ARM 64-bit handling
        if (arch.contains("aarch64") || arch.equals("arm64")) {
            return isMac ? "arm64" : "aarch64";  // macOS uses arm64; Linux uses aarch64
        }

        // ARM 32-bit handling (e.g., Raspberry Pi)
        if (arch.contains("arm")) {
            return "arm";
        }

        // Keep other architectures as-is
        return arch;
    }
}
