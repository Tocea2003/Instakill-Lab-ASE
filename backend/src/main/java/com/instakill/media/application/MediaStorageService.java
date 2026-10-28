package com.instakill.media.application;

import com.instakill.common.error.BadRequestException;
import com.instakill.common.id.IdGenerator;
import com.instakill.media.processing.ImageProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;

@Service
public class MediaStorageService {

    static final Set<String> PROCESSED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png");
    static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "webm");

    private final Path basePath;
    private final IdGenerator idGenerator;
    private final ImageProcessor imageProcessor;

    public MediaStorageService(@Value("${media.base-path:media}") String basePath, IdGenerator idGenerator,
                               ImageProcessor imageProcessor) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
        this.idGenerator = idGenerator;
        this.imageProcessor = imageProcessor;
    }

    /** Saves the file under a generated name and returns the public URL path (e.g. /media/&lt;uuid&gt;.mp4). */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File must not be empty");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported file type; allowed: " + String.join(", ", ALLOWED_EXTENSIONS));
        }
        String filename = idGenerator.generate() + "." + extension;
        try {
            Files.createDirectories(basePath);
            if (PROCESSED_IMAGE_EXTENSIONS.contains(extension)) {
                byte[] processed = processImage(file.getBytes(), extension);
                Files.write(basePath.resolve(filename), processed);
            } else {
                Files.copy(file.getInputStream(), basePath.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store media file", e);
        }
        return "/media/" + filename;
    }

    private byte[] processImage(byte[] bytes, String extension) {
        try {
            return imageProcessor.process(bytes, extension.equals("jpeg") ? "jpg" : extension);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("File is not a valid image");
        }
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot < 0 ? "" : originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
