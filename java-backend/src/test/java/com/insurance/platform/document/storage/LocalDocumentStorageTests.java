package com.insurance.platform.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.insurance.platform.common.error.ErrorCode;
import com.insurance.platform.common.exception.BusinessException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

class LocalDocumentStorageTests {
    private static final byte[] PDF = "%PDF-1.7\nphase ten".getBytes();

    @TempDir Path tempDir;

    @Test
    void validPdfUsesControlledNameAndDigest() throws Exception {
        LocalDocumentStorage storage = storage(DataSize.ofMegabytes(20));
        UUID documentId = UUID.randomUUID();
        StoredDocument stored = storage.store(documentId, file("terms.pdf", PDF, "application/pdf"));

        assertThat(stored.storageKey()).isEqualTo(documentId + ".pdf");
        assertThat(stored.originalFilename()).isEqualTo("terms.pdf");
        assertThat(stored.sizeBytes()).isEqualTo(PDF.length);
        assertThat(stored.sha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(PDF)));
        assertThat(storage.load(stored.storageKey()).getContentAsByteArray()).isEqualTo(PDF);

        storage.delete(stored.storageKey());
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void invalidMetadataSignatureAndSizeAreRejectedWithoutResidue() {
        LocalDocumentStorage storage = storage(DataSize.ofBytes(PDF.length - 1));
        assertError(storage, file("terms.pdf", PDF, "application/pdf"), ErrorCode.FILE_TOO_LARGE);

        storage = storage(DataSize.ofMegabytes(20));
        assertError(storage, file("../terms.pdf", PDF, "application/pdf"),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertError(storage, file("terms.pdf", PDF, "text/plain"),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertError(storage, file("terms.pdf", "not-pdf".getBytes(), "application/pdf"),
                ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertThat(tempDir).isEmptyDirectory();
    }

    private LocalDocumentStorage storage(DataSize maxSize) {
        LocalDocumentStorage storage = new LocalDocumentStorage(
                new DocumentStorageProperties(tempDir, maxSize));
        storage.initialize();
        return storage;
    }

    private static MockMultipartFile file(String name, byte[] content, String type) {
        return new MockMultipartFile("file", name, type, content);
    }

    private static void assertError(
            LocalDocumentStorage storage, MockMultipartFile file, ErrorCode expected) {
        assertThatThrownBy(() -> storage.store(UUID.randomUUID(), file))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(expected));
    }
}
