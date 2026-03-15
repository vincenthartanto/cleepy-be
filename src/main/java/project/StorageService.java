package project;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URL;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class StorageService {

    private static final Logger LOG = Logger.getLogger(StorageService.class);

    @Inject
    Storage storage;

    public String generateSignedUrl(String bucketName, String objectName) {
        if (bucketName == null || bucketName.isBlank() || objectName == null || objectName.isBlank()) {
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
