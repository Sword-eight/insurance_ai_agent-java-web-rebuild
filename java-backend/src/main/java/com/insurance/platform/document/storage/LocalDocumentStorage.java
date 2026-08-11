package com.insurance.platform.document.storage;

import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalDocumentStorage implements DocumentStorage {
    private static final byte[] PDF_SIGNATURE = new byte[] {'%', 'P', 'D', 'F', '-'};
    private static final String PDF_MIME = "application/pdf";

    private final Path root;
    private final long maxSize;

    public LocalDocumentStorage(DocumentStorageProperties properties) {
        this.root = properties.root().toAbsolutePath().normalize();
        this.maxSize = properties.maxSize().toBytes();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("document storage is unavailable", exception);
        }
    }

    @Override
    public StoredDocument store(UUID documentId, MultipartFile file) {
        String filename = validateMetadata(file);
        String storageKey = documentId + ".pdf";
        Path target = resolve(storageKey);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(root, documentId + "-", ".tmp");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = copyValidated(file, temporary, digest);
            moveAtomically(temporary, target);
            temporary = null;
            return new StoredDocument(
                    filename, storageKey, PDF_MIME, size,
                    HexFormat.of().formatHex(digest.digest()));
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    @Override
    public Resource load(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String validateMetadata(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        if (file.getSize() > maxSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.length() > 255
                || filename.contains("/") || filename.contains("\\")
                || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        if (!PDF_MIME.equalsIgnoreCase(file.getContentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        return filename.trim();
    }

    private long copyValidated(MultipartFile file, Path temporary, MessageDigest digest)
            throws IOException {
        try (InputStream input = file.getInputStream();
             OutputStream output = Files.newOutputStream(
                     temporary, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] signature = input.readNBytes(PDF_SIGNATURE.length);
            if (!java.util.Arrays.equals(signature, PDF_SIGNATURE)) {
                throw new BusinessException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
            output.write(signature);
            digest.update(signature);
            long size = signature.length;
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
                if (size > maxSize) {
                    throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
                }
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
            }
            return size;
        }
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || !storageKey.matches("[0-9a-fA-F-]{36}\\.pdf")) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        return resolved;
    }
}
