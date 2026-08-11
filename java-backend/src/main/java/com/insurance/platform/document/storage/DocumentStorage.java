package com.insurance.platform.document.storage;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorage {
    StoredDocument store(UUID documentId, MultipartFile file);
    Resource load(String storageKey);
    void delete(String storageKey);
}
