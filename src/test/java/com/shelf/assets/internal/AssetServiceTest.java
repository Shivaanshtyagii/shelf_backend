package com.shelf.assets.internal;

import com.shelf.assets.Assets.Kind;
import com.shelf.common.ApiFault;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssetServiceTest {
    private static final AssetProperties CONFIG = new AssetProperties(
            true, "00000000000000000000000000000000", "pilot-test",
            "test-key", "test-secret", "localhost", 3310, 8_000_000_000L, 100_000_000L);
    private final AssetRepository repository = mock(AssetRepository.class);
    private final PrivateStorage storage = mock(PrivateStorage.class);
    private final MalwareScanner scanner = mock(MalwareScanner.class);
    private final AssetService service = new AssetService(CONFIG, repository, storage, scanner, new DocumentValidator());

    @Test
    void storesOnlyScannedPdfBytesWithReservedQuota() throws IOException {
        var pdf = pdf();
        var owner = UUID.randomUUID();
        when(scanner.clean(any(Path.class))).thenReturn(true);
        doAnswer(invocation -> {
            Path received = invocation.getArgument(2);
            assertThat(Files.readAllBytes(received)).isEqualTo(pdf);
            return null;
        }).when(storage).put(anyString(), eq("application/pdf"), any(Path.class));

        var uploaded = service.upload(owner, Kind.PDF, new MockMultipartFile("file", "notes.pdf", "application/pdf", pdf));
        var key = "assets/" + uploaded.id();
        var sequence = inOrder(repository, storage);
        sequence.verify(repository).reserve(uploaded.id(), owner, key, "application/pdf", pdf.length, Kind.PDF);
        sequence.verify(storage).put(eq(key), eq("application/pdf"), any(Path.class));
        sequence.verify(repository).finish(uploaded.id(), true);
    }

    @Test
    void aSkippedScanCannotWriteToObjectStorage() throws IOException {
        when(scanner.clean(any(Path.class))).thenThrow(new IOException("Scanner unavailable"));
        var file = new MockMultipartFile("file", "notes.pdf", "application/pdf", pdf());

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), Kind.PDF, file))
                .isInstanceOf(ApiFault.class).hasMessage("The malware scanner is temporarily unavailable.");
        verifyNoInteractions(storage, repository);
    }

    @Test
    void presignsThePrivateR2BucketWithAShortExpiryWithoutContactingCloudflare() {
        var r2 = new R2Storage(CONFIG);
        try {
            var url = r2.link("assets/test-object");
            assertThat(url.getScheme()).isEqualTo("https");
            assertThat(url.getHost()).isEqualTo(CONFIG.r2Account() + ".r2.cloudflarestorage.com");
            assertThat(url.getPath()).isEqualTo("/pilot-test/assets/test-object");
            assertThat(url.getQuery()).contains("X-Amz-Expires=120");
        } finally {
            r2.close();
        }
    }

    private byte[] pdf() throws IOException {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
