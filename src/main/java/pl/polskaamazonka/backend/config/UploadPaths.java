package pl.polskaamazonka.backend.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class UploadPaths {

    public static final String UPLOADS_DIR_NAME = "uploads";
    public static final String CATEGORIES_DIR_NAME = "categories";
    public static final String VIDEOS_DIR_NAME = "videos";
    public static final String CATEGORIES_PUBLIC_PREFIX = "/uploads/categories/";
    public static final String VIDEOS_PUBLIC_PREFIX = "/uploads/videos/";
    public static final String LEGACY_CATEGORIES_PUBLIC_PREFIX = "/categories/";
    public static final String DEFAULT_VIDEO_THUMB_FILE_NAME = "default.png";

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

    public static Path resolveVideosDirectory() {
        Path directory = resolveUploadsRoot().resolve(VIDEOS_DIR_NAME);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return directory;
    }

    public static String defaultVideoThumbnailPublicUrl() {
        return VIDEOS_PUBLIC_PREFIX + DEFAULT_VIDEO_THUMB_FILE_NAME;
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

    public static boolean isStoredVideoThumbnailUrl(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith(VIDEOS_PUBLIC_PREFIX);
    }
}
