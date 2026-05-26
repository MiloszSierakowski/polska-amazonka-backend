package pl.polskaamazonka.backend.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class UploadPaths {

    public static final String UPLOADS_DIR_NAME = "uploads";
    public static final String CATEGORIES_DIR_NAME = "categories";
    public static final String CATEGORIES_PUBLIC_PREFIX = "/uploads/categories/";
    public static final String LEGACY_CATEGORIES_PUBLIC_PREFIX = "/categories/";

    private UploadPaths() {
    }

    public static Path resolveUploadsRoot() {
        Path workingDirectory = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path siblingUploads = workingDirectory.resolve("..").resolve(UPLOADS_DIR_NAME).normalize();
        Path localUploads = workingDirectory.resolve(UPLOADS_DIR_NAME);
        if (Files.isDirectory(siblingUploads)) {
            return siblingUploads;
        }
        if (Files.isDirectory(localUploads)) {
            return localUploads;
        }
        if ("backend".equalsIgnoreCase(String.valueOf(workingDirectory.getFileName()))) {
            return siblingUploads;
        }
        return localUploads;
    }

    public static Path resolveCategoriesDirectory() {
        Path directory = resolveUploadsRoot().resolve(CATEGORIES_DIR_NAME);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return directory;
    }

    public static String normalizeCategoryImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return imageUrl;
        }
        if (imageUrl.startsWith(CATEGORIES_PUBLIC_PREFIX)) {
            return imageUrl;
        }
        if (imageUrl.startsWith(LEGACY_CATEGORIES_PUBLIC_PREFIX)) {
            return CATEGORIES_PUBLIC_PREFIX + imageUrl.substring(LEGACY_CATEGORIES_PUBLIC_PREFIX.length());
        }
        return imageUrl;
    }
}
