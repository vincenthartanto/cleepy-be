package project;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.gax.paging.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import io.smallrye.config.ConfigMapping;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import jakarta.ws.rs.core.StreamingOutput;

@ApplicationScoped
public class StorageService {

    private static final Logger LOG = Logger.getLogger(StorageService.class);

    @ConfigMapping(prefix = "app.gcs")
    public interface StorageConfig {
        String bucketName();
    }

    @jakarta.inject.Inject
    StorageConfig storageConfig;

    private Storage storage;

    @PostConstruct
    void init() {
        try {
            LOG.info("Initializing Google Cloud Storage manually...");
            storage = StorageOptions.newBuilder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream("/Users/vincenthartanto/Documents/2026 commercial project/cleepy-ai/cleepy-be/gcp-credentials.json")))
                    .setProjectId("cleepy")
                    .build()
                    .getService();
            LOG.info("GCS Initialized successfully.");
        } catch (IOException e) {
            LOG.error("Failed to initialize Google Cloud Storage", e);
        }
    }

    public String defaultBucketName() {
        return storageConfig.bucketName();
    }

    public StoredObjectMetadata storeObject(String objectName, String contentType, InputStream inputStream) {
        ensureStorageReady();

        try (InputStream stream = inputStream) {
            BlobInfo blobInfo = BlobInfo.newBuilder(defaultBucketName(), objectName)
                    .setContentType(contentType)
                    .build();
            Blob storedBlob = storage.create(blobInfo, stream);
            return new StoredObjectMetadata(
                    storedBlob.getBucket(),
                    storedBlob.getName(),
                    storedBlob.getSize(),
                    storedBlob.getContentType() != null ? storedBlob.getContentType() : contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store uploaded object in GCS.", e);
        }
    }

    public String generateSignedUrl(String bucketName, String objectName) {
        if (bucketName == null || bucketName.isBlank() || objectName == null || objectName.isBlank()) {
            return null;
        }

        ensureStorageReady();

        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName).build();
            URL signedUrl = storage.signUrl(
                    blobInfo,
                    1,
                    TimeUnit.HOURS,
                    Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                    Storage.SignUrlOption.withV4Signature()
            );
            return signedUrl.toString();
        } catch (StorageException e) {
            LOG.errorf("Failed to generate signed URL for bucket %s and object %s: %s", bucketName, objectName, e.getMessage());
            return null;
        }
    }

    public StoredObject readObject(String bucketName, String objectName) {
        Blob blob = requireBlob(bucketName, objectName);
        String contentType = blob.getContentType() != null ? blob.getContentType() : "video/mp4";
        return new StoredObject(blob.getContent(), contentType);
    }

    public StoredObjectMetadata getObjectMetadata(String bucketName, String objectName) {
        Blob blob = requireBlob(bucketName, objectName);
        return new StoredObjectMetadata(
                bucketName,
                objectName,
                blob.getSize(),
                blob.getContentType() != null ? blob.getContentType() : "application/octet-stream");
    }

    public StoredObjectStream openObjectStream(String bucketName, String objectName, String rangeHeader) {
        Blob blob = requireBlob(bucketName, objectName);
        String contentType = blob.getContentType() != null ? blob.getContentType() : "application/octet-stream";
        long totalSize = blob.getSize();
        ByteRange range = ByteRange.parse(rangeHeader, totalSize);
        long start = range != null ? range.start() : 0L;
        long end = range != null ? range.end() : Math.max(totalSize - 1, 0L);
        long contentLength = totalSize == 0 ? 0L : end - start + 1;

        StreamingOutput output = stream -> {
            if (totalSize == 0) {
                stream.flush();
                return;
            }

            try (ReadChannel reader = blob.reader()) {
                reader.seek(start);
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                long remaining = contentLength;
                while (remaining > 0) {
                    buffer.clear();
                    buffer.limit((int) Math.min(buffer.capacity(), remaining));
                    int read = reader.read(buffer);
                    if (read < 0) {
                        break;
                    }
                    stream.write(buffer.array(), 0, read);
                    remaining -= read;
                }
                stream.flush();
            }
        };

        return new StoredObjectStream(output, contentType, totalSize, start, end, range != null);
    }

    public boolean objectExists(String bucketName, String objectName) {
        ensureStorageReady();
        Blob blob = storage.get(bucketName, objectName);
        return blob != null && blob.exists();
    }

    public boolean deleteObject(String bucketName, String objectName) {
        ensureStorageReady();
        try {
            return storage.delete(bucketName, objectName);
        } catch (StorageException e) {
            LOG.warnf(e, "Failed to delete object %s/%s", bucketName, objectName);
            return false;
        }
    }

    public void deletePrefix(String bucketName, String prefix) {
        ensureStorageReady();
        try {
            Page<Blob> blobs = storage.list(bucketName, BlobListOption.prefix(prefix));
            for (Blob blob : blobs.iterateAll()) {
                storage.delete(BlobId.of(bucketName, blob.getName()));
            }
        } catch (StorageException e) {
            LOG.warnf(e, "Failed to delete prefix %s/%s", bucketName, prefix);
        }
    }

    private Blob requireBlob(String bucketName, String objectName) {
        ensureStorageReady();
        Blob blob = storage.get(bucketName, objectName);
        if (blob == null || !blob.exists()) {
            throw new IllegalArgumentException("Storage object not found: " + bucketName + "/" + objectName);
        }
        return blob;
    }

    private void ensureStorageReady() {
        if (storage == null) {
            throw new IllegalStateException("Storage is not initialized.");
        }
        if (defaultBucketName() == null || defaultBucketName().isBlank()) {
            throw new IllegalStateException("app.gcs.bucket-name must be configured.");
        }
    }

    public record StoredObject(byte[] bytes, String contentType) {
    }

    public record StoredObjectMetadata(
            String bucketName,
            String objectName,
            long sizeBytes,
            String contentType) {
    }

    public record StoredObjectStream(
            StreamingOutput output,
            String contentType,
            long totalSizeBytes,
            long startByte,
            long endByte,
            boolean partialContent) {
    }

    private record ByteRange(long start, long end) {
        static ByteRange parse(String header, long totalSize) {
            if (header == null || header.isBlank() || totalSize <= 0) {
                return null;
            }

            String normalized = header.trim().toLowerCase(Locale.ROOT);
            if (!normalized.startsWith("bytes=")) {
                return null;
            }

            String value = normalized.substring("bytes=".length());
            int separatorIndex = value.indexOf('-');
            if (separatorIndex < 0) {
                return null;
            }

            String startPart = value.substring(0, separatorIndex).trim();
            String endPart = value.substring(separatorIndex + 1).trim();

            try {
                if (startPart.isEmpty()) {
                    long suffixLength = Long.parseLong(endPart);
                    if (suffixLength <= 0) {
                        return null;
                    }
                    long start = Math.max(totalSize - suffixLength, 0);
                    return new ByteRange(start, totalSize - 1);
                }

                long start = Long.parseLong(startPart);
                if (start >= totalSize) {
                    return null;
                }

                long end = endPart.isEmpty() ? totalSize - 1 : Math.min(Long.parseLong(endPart), totalSize - 1);
                if (end < start) {
                    return null;
                }

                return new ByteRange(start, end);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
