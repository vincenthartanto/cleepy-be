package project.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import clip.Clip;
import project.Project;

class ProjectReadResponseTest {

    @Test
    void projectDetailsResponse_shouldExcludeStorageMetadata() {
        Project project = new Project();
        project.id = UUID.randomUUID();
        project.title = "Test Project";
        project.status = "COMPLETED";
        project.customPrompt = "prompt";
        project.thumbnailUrl = "/api/project/test/media/thumbnail";
        project.thumbnailStorageUri = "gs://bucket/project-thumb.jpg";
        project.thumbnailBucket = "bucket";
        project.thumbnailObjectPath = "output/user/project-thumb.jpg";
        project.userId = "user-1";
        project.sourceUrl = "/api/project/test/media/source";
        project.sourceStorageUri = "gs://bucket/source.mp4";
        project.sourceBucket = "bucket";
        project.sourceObjectPath = "pending/user/source.mp4";
        project.sourceFileName = "source.mp4";
        project.sourceContentType = "video/mp4";
        project.sourceSizeBytes = 1024L;
        project.cost = 1;
        project.durationSeconds = 80;
        project.workerRetryCount = 0;
        project.lastFailedStage = null;
        project.lastFailureReason = null;
        project.clipsCount = 5;
        project.createdAt = LocalDateTime.parse("2026-03-22T17:00:29.580834");
        project.updatedAt = LocalDateTime.parse("2026-03-22T17:03:43.987730");

        ProjectDetailsResponse response = ProjectDetailsResponse.from(project);

        assertEquals(project.id, response.id());
        assertEquals(project.thumbnailUrl, response.thumbnailUrl());
        assertEquals(project.sourceUrl, response.sourceUrl());
        assertEquals(project.sourceFileName, response.sourceFileName());
        assertEquals(project.sourceContentType, response.sourceContentType());
        assertEquals(project.sourceSizeBytes, response.sourceSizeBytes());
        assertNull(findAccessor(response, "thumbnailStorageUri"));
        assertNull(findAccessor(response, "thumbnailBucket"));
        assertNull(findAccessor(response, "thumbnailObjectPath"));
        assertNull(findAccessor(response, "sourceStorageUri"));
        assertNull(findAccessor(response, "sourceBucket"));
        assertNull(findAccessor(response, "sourceObjectPath"));
    }

    @Test
    void projectClipResponse_shouldExcludeStorageMetadataAndParentProject() {
        Clip clip = new Clip();
        clip.id = UUID.randomUUID();
        clip.title = "Clip";
        clip.description = "Description";
        clip.videoUrl = "/api/project/test/clips/clip/media/video";
        clip.thumbnailUrl = "/api/project/test/clips/clip/media/thumbnail";
        clip.storageProvider = "gcs";
        clip.videoStorageUri = "gs://bucket/clip.mp4";
        clip.videoBucket = "bucket";
        clip.videoObjectPath = "output/user/project/clip.mp4";
        clip.thumbnailStorageUri = "gs://bucket/clip.jpg";
        clip.thumbnailBucket = "bucket";
        clip.thumbnailObjectPath = "output/user/project/clip.jpg";
        clip.startTime = LocalTime.parse("00:00:00");
        clip.endTime = LocalTime.parse("00:00:57");
        clip.viralScore = 92;
        clip.analysisResult = null;
        clip.createdAt = LocalDateTime.parse("2026-03-22T17:00:29.580834");
        clip.updatedAt = LocalDateTime.parse("2026-03-22T17:03:43.987730");
        clip.project = new Project();

        ProjectClipResponse response = ProjectClipResponse.from(clip);

        assertEquals(clip.id, response.id());
        assertEquals(clip.videoUrl, response.videoUrl());
        assertEquals(clip.thumbnailUrl, response.thumbnailUrl());
        assertEquals(clip.viralScore, response.viralScore());
        assertNull(findAccessor(response, "storageProvider"));
        assertNull(findAccessor(response, "videoStorageUri"));
        assertNull(findAccessor(response, "videoBucket"));
        assertNull(findAccessor(response, "videoObjectPath"));
        assertNull(findAccessor(response, "thumbnailStorageUri"));
        assertNull(findAccessor(response, "thumbnailBucket"));
        assertNull(findAccessor(response, "thumbnailObjectPath"));
        assertNull(findAccessor(response, "project"));
    }

    private Object findAccessor(Object response, String accessorName) {
        try {
            return response.getClass().getDeclaredMethod(accessorName);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
