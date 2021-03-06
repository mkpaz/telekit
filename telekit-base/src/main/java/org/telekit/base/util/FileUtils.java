package org.telekit.base.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.substringBeforeLast;
import static org.telekit.base.Env.TEMP_DIR;

public final class FileUtils {

    public static @NotNull File urlToFile(URL url) {
        try {
            return new File(Objects.requireNonNull(url).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NotNull String sanitizeFileName(String filename) {
        if (filename == null || filename.isBlank()) return "";
        return filename.replaceAll("[\\\\/:*?\"'<>|]", "_");
    }

    /**
     * Returns file name without extension.
     *
     * @param path absolute path to the target file
     */
    public static @NotNull String getFileName(Path path) {
        if (path == null) return "";
        return substringBeforeLast(path.getFileName().toString(), ".");
    }

    /**
     * Returns file name extension.
     *
     * @param path absolute path to the target file
     */
    public static @NotNull String getFileExtension(Path path) {
        if (path == null) return "";
        return substringAfterLast(path.getFileName().toString(), ".");
    }

    public static @NotNull Path ensureNotNull(String path, Path defaultValue) {
        return path != null ? Paths.get(path) : Objects.requireNonNull(defaultValue);
    }

    public static @NotNull List<Path> findFilesByPrefix(Path path, String prefix) {
        if (!dirExists(path)) return Collections.emptyList();
        return Arrays.stream(path.toFile().listFiles((dir, name) -> name.startsWith(prefix)))
                .filter(File::isFile)
                .map(File::toPath)
                .collect(Collectors.toList());
    }

    public static @NotNull Path createTempFilePath() {
        return createTempPath(null, ".tmp");
    }

    public static @NotNull Path createTempDirPath() {
        return createTempPath("tmp-", null);
    }

    /*
     * This method does not create nor empty temp file nor directory,
     * but only returns its path.
     */
    public static @NotNull Path createTempPath(String prefix, String suffix) {
        String filename = UUID.randomUUID().toString().replace("-", "");
        prefix = StringUtils.ensureNotNull(prefix, "");
        suffix = StringUtils.ensureNotNull(suffix, "");
        return TEMP_DIR.resolve(prefix + filename + suffix);
    }

    public static @NotNull Path createTempFile() {
        return createTempFile(null, null);
    }

    public static @NotNull Path createTempFile(String prefix, String suffix) {
        try {
            return Files.createTempFile(prefix, suffix);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @NotNull Path createTempDir() {
        return createTempDir(null);
    }

    public static @NotNull Path createTempDir(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createDir(Path path) {
        try {
            if (!exists(path)) Files.createDirectory(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createDirs(Path path) {
        try {
            if (!exists(path)) Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copyFile(Path source, Path dest, StandardCopyOption option) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(dest);

        try {
            Files.copy(source, dest, option);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void copyDir(Path source, Path dest, boolean overwrite) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(dest);
        try {
            Files.walk(source).forEach(entry -> {
                Path target = dest.resolve(source.relativize(entry));
                if (!Files.exists(target) | overwrite) {
                    copyFile(entry, target, REPLACE_EXISTING);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteFile(Path path) {
        if (!fileExists(path)) return;
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteDir(Path path) {
        if (!dirExists(path)) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isDirEmpty(Path dir) {
        if (!dirExists(dir)) return true;
        try (DirectoryStream<Path> folderStream = Files.newDirectoryStream(dir)) {
            return !folderStream.iterator().hasNext();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean exists(Path path) {
        return path != null && Files.exists(path);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean dirExists(Path path) {
        return exists(path) && Files.isDirectory(path);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean fileExists(Path path) {
        return exists(path) && Files.isRegularFile(path);
    }

    /**
     * Creates a copy of a file in the temp directory.
     *
     * @return a path to temp file or null if copying has failed.
     */
    public static @Nullable Path backupFile(Path source) {
        if (!exists(source)) return null;

        Path tmp = createTempFilePath();
        try {
            copyFile(source, tmp, REPLACE_EXISTING);
            return tmp;
        } catch (Exception e) {
            return null;
        }
    }
}
