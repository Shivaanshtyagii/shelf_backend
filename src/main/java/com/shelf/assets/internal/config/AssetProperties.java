package com.shelf.assets.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shelf.assets")
public record AssetProperties(boolean enabled, String r2Account, String r2Bucket, String r2KeyId, String r2Secret,
                       String clamavHost, int clamavPort, long accountBytes, long userBytes) {
    public boolean configured() {
        return enabled && text(r2Account) && text(r2Bucket) && text(r2KeyId)
                && text(r2Secret) && text(clamavHost) && clamavPort > 0;
    }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
}
