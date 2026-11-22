package com.shelf.assets.internal.integration;

import com.shelf.assets.internal.config.AssetProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class R2StorageTest {
    private static final AssetProperties CONFIG = new AssetProperties(
            true, "00000000000000000000000000000000", "pilot-test",
            "test-key", "test-secret", "localhost", 3310, 8_000_000_000L, 100_000_000L);

    @Test
    void presignsThePrivateR2BucketWithAShortExpiryWithoutContactingCloudflare() throws java.io.IOException {
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
}
