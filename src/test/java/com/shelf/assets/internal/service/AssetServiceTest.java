package com.shelf.assets.internal.service;

import com.shelf.assets.Assets.Kind;
import com.shelf.assets.internal.config.AssetProperties;
import com.shelf.assets.internal.integration.MalwareScanner;
import com.shelf.assets.internal.integration.PrivateStorage;
import com.shelf.assets.internal.persistence.AssetRepository;
import com.shelf.common.ApiFault;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
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

        when(repository.reserve(any(UUID.class), eq(owner), anyString(), eq("application/pdf"), eq((long) pdf.length), eq(Kind.PDF)))
                .thenReturn(true);

        var uploaded = service.upload(owner, Kind.PDF, new ByteArrayInputStream(pdf), pdf.length);
        var key = "assets/" + uploaded.id();
        var sequence = inOrder(repository, storage);
        sequence.verify(repository).reserve(uploaded.id(), owner, key, "application/pdf", pdf.length, Kind.PDF);
        sequence.verify(storage).put(eq(key), eq("application/pdf"), any(Path.class));
        sequence.verify(repository).finish(uploaded.id(), true);
    }

    @Test
    void aSkippedScanCannotWriteToObjectStorage() throws IOException {
        when(scanner.clean(any(Path.class))).thenThrow(new IOException("Scanner unavailable"));
        var pdf = pdf();

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), Kind.PDF, new ByteArrayInputStream(pdf), pdf.length))
                .isInstanceOf(ApiFault.class).hasMessage("The malware scanner is temporarily unavailable.");
        verifyNoInteractions(storage, repository);
    }

    private byte[] pdf() throws IOException {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(bytes);
            return bytes.toByteArray();
        }
    }
}
