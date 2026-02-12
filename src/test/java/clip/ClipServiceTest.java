package clip;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import clip.dto.ClipRequest;
import common.dto.request.SpecificationRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import project.Project;
import project.ProjectRepository;

@QuarkusTest
public class ClipServiceTest {

    @InjectMock
    ClipRepository clipRepository;

    @InjectMock
    ProjectRepository projectRepository;

    @InjectMock
    ClipMapper clipMapper;

    @Inject
    ClipService clipService;

    @Test
    @RunOnVertxContext
    void testCreateClip_whenValidRequest_shouldReturnPersistedClip(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ClipRequest request = new ClipRequest(
            projectId.toString(), "Test Clip", "Test Description",
            "https://video.url", "https://thumbnail.url",
            LocalTime.of(0, 0, 10), LocalTime.of(0, 0, 30),
            85, "Analysis result"
        );

        Project mockProject = new Project();
        mockProject.id = projectId;

        Clip mockClip = new Clip();
        mockClip.id = UUID.randomUUID();
        mockClip.title = request.title();
        mockClip.project = mockProject;

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
        when(clipMapper.toEntity(request, mockProject)).thenReturn(mockClip);
        when(clipRepository.persist(any(Clip.class))).thenReturn(Uni.createFrom().item(mockClip));

        asserter.assertThat(() -> clipService.createClip(request),
            clip -> {
                assertNotNull(clip);
                assertEquals("Test Clip", clip.title);
                assertEquals(mockProject, clip.project);
                verify(projectRepository).findById(projectId);
                verify(clipMapper).toEntity(request, mockProject);
                verify(clipRepository).persist(any(Clip.class));
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenNullOptionalFields_shouldReturnClipWithNulls(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ClipRequest request = new ClipRequest(
            projectId.toString(), "Clip Title", null,
            null, null,
            null, null,
            null, null
        );

        Project mockProject = new Project();
        mockProject.id = projectId;

        Clip mockClip = new Clip();
        mockClip.id = UUID.randomUUID();
        mockClip.title = request.title();
        mockClip.project = mockProject;

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
        when(clipMapper.toEntity(request, mockProject)).thenReturn(mockClip);
        when(clipRepository.persist(any(Clip.class))).thenReturn(Uni.createFrom().item(mockClip));

        asserter.assertThat(() -> clipService.createClip(request),
            clip -> {
                assertNotNull(clip);
                assertEquals("Clip Title", clip.title);
                assertNull(clip.description);
                assertNull(clip.videoUrl);
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenZeroViralScore_shouldReturnClipWithZeroScore(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ClipRequest request = new ClipRequest(
            projectId.toString(), "Clip", "Desc",
            "https://video.url", "https://thumb.url",
            LocalTime.of(0, 0, 0), LocalTime.of(0, 0, 1),
            0, "Low score"
        );

        Project mockProject = new Project();
        mockProject.id = projectId;

        Clip mockClip = new Clip();
        mockClip.id = UUID.randomUUID();
        mockClip.title = request.title();
        mockClip.viralScore = 0;
        mockClip.project = mockProject;

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
        when(clipMapper.toEntity(request, mockProject)).thenReturn(mockClip);
        when(clipRepository.persist(any(Clip.class))).thenReturn(Uni.createFrom().item(mockClip));

        asserter.assertThat(() -> clipService.createClip(request),
            clip -> {
                assertNotNull(clip);
                assertEquals(0, clip.viralScore);
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenProjectNotFound_shouldThrowIllegalArgumentException(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ClipRequest request = new ClipRequest(
            projectId.toString(), "Test Clip", "Desc",
            "https://video.url", "https://thumb.url",
            LocalTime.of(0, 0, 10), LocalTime.of(0, 0, 30),
            85, "Analysis"
        );

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().nullItem());

        asserter.assertFailedWith(() -> clipService.createClip(request),
            throwable -> {
                assertTrue(throwable instanceof IllegalArgumentException);
                assertEquals("Project not found", throwable.getMessage());
                verify(clipRepository, never()).persist(any(Clip.class));
                verify(clipMapper, never()).toEntity(any(), any());
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenInvalidProjectIdFormat_shouldThrowIllegalArgumentException(UniAsserter asserter) {
        ClipRequest request = new ClipRequest(
            "not-a-valid-uuid", "Test Clip", "Desc",
            "https://video.url", "https://thumb.url",
            LocalTime.of(0, 0, 10), LocalTime.of(0, 0, 30),
            85, "Analysis"
        );

        asserter.assertFailedWith(() -> clipService.createClip(request),
            throwable -> {
                assertTrue(throwable instanceof IllegalArgumentException);
                verify(projectRepository, never()).findById(any());
                verify(clipRepository, never()).persist(any(Clip.class));
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenPersistFails_shouldThrowRuntimeException(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ClipRequest request = new ClipRequest(
            projectId.toString(), "Test Clip", "Desc",
            "https://video.url", "https://thumb.url",
            LocalTime.of(0, 0, 10), LocalTime.of(0, 0, 30),
            85, "Analysis"
        );

        Project mockProject = new Project();
        mockProject.id = projectId;

        Clip mockClip = new Clip();
        mockClip.title = request.title();

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
        when(clipMapper.toEntity(request, mockProject)).thenReturn(mockClip);
        when(clipRepository.persist(any(Clip.class)))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Database error")));

        asserter.assertFailedWith(() -> clipService.createClip(request),
            throwable -> {
                assertTrue(throwable instanceof RuntimeException);
                assertEquals("Database error", throwable.getMessage());
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenProjectRepositoryFails_shouldThrowRuntimeException(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ClipRequest request = new ClipRequest(
            projectId.toString(), "Test Clip", "Desc",
            "https://video.url", "https://thumb.url",
            LocalTime.of(0, 0, 10), LocalTime.of(0, 0, 30),
            85, "Analysis"
        );

        when(projectRepository.findById(projectId))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Connection refused")));

        asserter.assertFailedWith(() -> clipService.createClip(request),
            throwable -> {
                assertTrue(throwable instanceof RuntimeException);
                assertEquals("Connection refused", throwable.getMessage());
                verify(clipRepository, never()).persist(any(Clip.class));
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenMaxViralScore_shouldReturnClipWithMaxScore(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ClipRequest request = new ClipRequest(
            projectId.toString(), "Viral Clip", "Very viral",
            "https://video.url", "https://thumb.url",
            LocalTime.of(0, 0, 0), LocalTime.of(23, 59, 59),
            Integer.MAX_VALUE, "Max score"
        );

        Project mockProject = new Project();
        mockProject.id = projectId;

        Clip mockClip = new Clip();
        mockClip.id = UUID.randomUUID();
        mockClip.title = request.title();
        mockClip.viralScore = Integer.MAX_VALUE;
        mockClip.project = mockProject;

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
        when(clipMapper.toEntity(request, mockProject)).thenReturn(mockClip);
        when(clipRepository.persist(any(Clip.class))).thenReturn(Uni.createFrom().item(mockClip));

        asserter.assertThat(() -> clipService.createClip(request),
            clip -> {
                assertNotNull(clip);
                assertEquals(Integer.MAX_VALUE, clip.viralScore);
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenSameStartAndEndTime_shouldReturnClipWithEqualTimes(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        LocalTime sameTime = LocalTime.of(0, 1, 30);
        ClipRequest request = new ClipRequest(
            projectId.toString(), "Zero Duration Clip", "Desc",
            "https://video.url", "https://thumb.url",
            sameTime, sameTime,
            50, "Analysis"
        );

        Project mockProject = new Project();
        mockProject.id = projectId;

        Clip mockClip = new Clip();
        mockClip.id = UUID.randomUUID();
        mockClip.title = request.title();
        mockClip.startTime = sameTime;
        mockClip.endTime = sameTime;
        mockClip.project = mockProject;

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
        when(clipMapper.toEntity(request, mockProject)).thenReturn(mockClip);
        when(clipRepository.persist(any(Clip.class))).thenReturn(Uni.createFrom().item(mockClip));

        asserter.assertThat(() -> clipService.createClip(request),
            clip -> {
                assertNotNull(clip);
                assertEquals(sameTime, clip.startTime);
                assertEquals(sameTime, clip.endTime);
            });
    }

    @Test
    @RunOnVertxContext
    void testCreateClip_whenEmptyStringFields_shouldReturnClipWithEmptyStrings(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ClipRequest request = new ClipRequest(
            projectId.toString(), "", "",
            "", "",
            LocalTime.of(0, 0, 0), LocalTime.of(0, 0, 1),
            0, ""
        );

        Project mockProject = new Project();
        mockProject.id = projectId;

        Clip mockClip = new Clip();
        mockClip.id = UUID.randomUUID();
        mockClip.title = "";
        mockClip.description = "";
        mockClip.project = mockProject;

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
        when(clipMapper.toEntity(request, mockProject)).thenReturn(mockClip);
        when(clipRepository.persist(any(Clip.class))).thenReturn(Uni.createFrom().item(mockClip));

        asserter.assertThat(() -> clipService.createClip(request),
            clip -> {
                assertNotNull(clip);
                assertEquals("", clip.title);
                assertEquals("", clip.description);
            });
    }

    @Test
    @RunOnVertxContext
    void testGetClips_whenMatchingClipsExist_shouldReturnClipList(UniAsserter asserter) {
        SpecificationRequest request = new SpecificationRequest();
        request.search = "test";
        request.page = 0;
        request.size = 20;

        Clip clip1 = new Clip();
        clip1.id = UUID.randomUUID();
        clip1.title = "Test Clip 1";

        Clip clip2 = new Clip();
        clip2.id = UUID.randomUUID();
        clip2.title = "Test Clip 2";

        when(clipRepository.findByTitleLike(request)).thenReturn(Uni.createFrom().item(List.of(clip1, clip2)));

        asserter.assertThat(() -> clipService.getClips(request),
            clips -> {
                assertNotNull(clips);
                assertEquals(2, clips.size());
                assertEquals("Test Clip 1", clips.get(0).title);
                assertEquals("Test Clip 2", clips.get(1).title);
            });
    }

    @Test
    @RunOnVertxContext
    void testGetClips_whenNoMatchingClips_shouldReturnEmptyList(UniAsserter asserter) {
        SpecificationRequest request = new SpecificationRequest();
        request.search = "nonexistent";
        request.page = 0;
        request.size = 20;

        when(clipRepository.findByTitleLike(request)).thenReturn(Uni.createFrom().item(Collections.emptyList()));

        asserter.assertThat(() -> clipService.getClips(request),
            clips -> {
                assertNotNull(clips);
                assertTrue(clips.isEmpty());
            });
    }

    @Test
    @RunOnVertxContext
    void testGetClips_whenNullSearch_shouldReturnAllClips(UniAsserter asserter) {
        SpecificationRequest request = new SpecificationRequest();
        request.search = null;
        request.page = 0;
        request.size = 20;

        Clip clip = new Clip();
        clip.id = UUID.randomUUID();
        clip.title = "Any Clip";

        when(clipRepository.findByTitleLike(request)).thenReturn(Uni.createFrom().item(List.of(clip)));

        asserter.assertThat(() -> clipService.getClips(request),
            clips -> {
                assertNotNull(clips);
                assertEquals(1, clips.size());
            });
    }

    @Test
    @RunOnVertxContext
    void testGetClips_whenRepositoryFails_shouldThrowRuntimeException(UniAsserter asserter) {
        SpecificationRequest request = new SpecificationRequest();
        request.search = "test";
        request.page = 0;
        request.size = 20;

        when(clipRepository.findByTitleLike(request))
            .thenReturn(Uni.createFrom().failure(new RuntimeException("Database unavailable")));

        asserter.assertFailedWith(() -> clipService.getClips(request),
            throwable -> {
                assertTrue(throwable instanceof RuntimeException);
                assertEquals("Database unavailable", throwable.getMessage());
            });
    }
}
