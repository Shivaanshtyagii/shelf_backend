package com.shelf.api.assets.integration;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

@Component
class R2Storage implements PrivateStorage {
    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    R2Storage(AssetProperties properties) {
        bucket = properties.r2Bucket();
        if (!properties.configured()) {
            client = null;
            presigner = null;
            return;
        }
        var endpoint = URI.create("https://" + properties.r2Account() + ".r2.cloudflarestorage.com");
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.r2KeyId(), properties.r2Secret()));
        var options = S3Configuration.builder().pathStyleAccessEnabled(true).build();
        client = S3Client.builder().endpointOverride(endpoint).credentialsProvider(credentials)
                .region(Region.of("auto")).serviceConfiguration(options)
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .overrideConfiguration(config -> config.apiCallTimeout(Duration.ofSeconds(45))
                        .apiCallAttemptTimeout(Duration.ofSeconds(20))).build();
        presigner = S3Presigner.builder().endpointOverride(endpoint).credentialsProvider(credentials)
                .region(Region.of("auto")).serviceConfiguration(options).build();
    }

    @Override
    public void put(String key, String contentType, Path file) throws IOException {
        try {
            client.putObject(req -> req.bucket(bucket).key(key).contentType(contentType)
                    .cacheControl("private, no-store").contentDisposition(disposition(contentType)), RequestBody.fromFile(file));
        } catch (SdkException ex) {
            throw new IOException("Private storage upload failed.", ex);
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            client.deleteObject(req -> req.bucket(bucket).key(key));
        } catch (SdkException ex) {
            throw new IOException("Private storage deletion failed.", ex);
        }
    }

    @Override
    public URI link(String key) throws IOException {
        var request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        try {
            return URI.create(presigner.presignGetObject(builder -> builder.signatureDuration(Duration.ofMinutes(2))
                    .getObjectRequest(request)).url().toExternalForm());
        } catch (SdkException ex) {
            throw new IOException("Private storage link generation failed.", ex);
        }
    }

    private static String disposition(String contentType) {
        return contentType.equals("application/pdf") ? "attachment; filename=notes.pdf" : "inline";
    }

    @PreDestroy
    void close() {
        if (client != null) {
            presigner.close();
            client.close();
        }
    }
}
