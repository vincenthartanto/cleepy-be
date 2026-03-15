package project;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class StorageService {

    private static final Logger LOG = Logger.getLogger(StorageService.class);

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

    public String generateSignedUrl(String bucketName, String objectName) {
        if (bucketName == null || bucketName.isBlank() || objectName == null || objectName.isBlank()) {
            return null;
        }
        
        if (storage == null) {
            LOG.error("Cannot generate signed URL because storage is null.");
            return null;
        }

        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectName).build();
            URL signedUrl = storage.signUrl(
                    blobInfo,
                    1, // 1 hour expiration
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
}
