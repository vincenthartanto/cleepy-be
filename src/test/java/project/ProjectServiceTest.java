package project;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import clip.Clip;
import clip.ClipRepository;
import common.dto.request.SpecificationRequest;
import integration.AiClipperClient;
import integration.dto.VideoMetadataDTO;
import integration.dto.VideoProcessRequest;
import integration.dto.VideoProcessResponse;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import project.dto.ProjectCompletionDTO;
import project.dto.ProjectCompletionDTO.ClipDTO;
import project.dto.ProjectRequest;
import project.dto.ProjectUploadRequest;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import project.StorageService;
import user.User;
import user.UserRepository;

@QuarkusTest
public class ProjectServiceTest {

        @InjectMock
        ProjectRepository projectRepository;

        @InjectMock
        ProjectMapper projectMapper;

        @InjectMock
        UserRepository userRepository;

        @InjectMock
        @RestClient
        AiClipperClient aiClipperClient;

        @InjectMock
        ClipRepository clipRepository;

        @Inject
        ProjectService projectService;

        @InjectMock
        StorageService storageService;

        @Test
        @RunOnVertxContext
        void testGetProjectById_whenProjectHasStoredMedia_shouldExposeRelativeMediaUrls(UniAsserter asserter) {
                String userId = "firebase-uid-media";
                UUID projectId = UUID.randomUUID();

                Project project = new Project();
                project.id = projectId;
                project.userId = userId;
                project.thumbnailBucket = "test-bucket";
                project.thumbnailObjectPath = "output/user/project-thumb.jpg";
                project.sourceBucket = "test-bucket";
                project.sourceObjectPath = "pending/user/source.mp4";

                projectService.mediaBasePath = "/api/";
                when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(project));

                asserter.assertThat(() -> projectService.getProjectById(projectId, userId),
                                loadedProject -> {
                                        assertEquals("/api/project/" + projectId + "/media/thumbnail",
                                                        loadedProject.thumbnailUrl);
                                        assertEquals("/api/project/" + projectId + "/media/source",
                                                        loadedProject.sourceUrl);
                                        assertFalse(loadedProject.thumbnailUrl.startsWith("http://localhost"));
                                        assertFalse(loadedProject.sourceUrl.startsWith("http://localhost"));
                                });
        }

        @Test
        @RunOnVertxContext
        void testGetProjectClips_whenClipsHaveStoredMedia_shouldExposeRelativeMediaUrls(UniAsserter asserter) {
                String userId = "firebase-uid-clips";
                UUID projectId = UUID.randomUUID();
                UUID clipId = UUID.randomUUID();

                Project project = new Project();
                project.id = projectId;
                project.userId = userId;

                Clip clip = new Clip();
                clip.id = clipId;
                clip.videoBucket = "test-bucket";
                clip.videoObjectPath = "output/user/project/clip.mp4";
                clip.thumbnailBucket = "test-bucket";
                clip.thumbnailObjectPath = "output/user/project/clip.jpg";

                projectService.mediaBasePath = "api";
                when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(project));
                when(clipRepository.list("project.id", projectId)).thenReturn(Uni.createFrom().item(List.of(clip)));

                asserter.assertThat(() -> projectService.getProjectClips(projectId, userId),
                                loadedClips -> {
                                        assertEquals(1, loadedClips.size());
                                        Clip loadedClip = loadedClips.get(0);
                                        assertEquals(
                                                        "/api/project/" + projectId + "/clips/" + clipId + "/media/video",
                                                        loadedClip.videoUrl);
                                        assertEquals(
                                                        "/api/project/" + projectId + "/clips/" + clipId
                                                                        + "/media/thumbnail",
                                                        loadedClip.thumbnailUrl);
                                        assertFalse(loadedClip.videoUrl.startsWith("http://localhost"));
                                        assertFalse(loadedClip.thumbnailUrl.startsWith("http://localhost"));
                                });
        }

        // ── createProject tests ──

        @Test
        @RunOnVertxContext
        void testCreateProject_whenValidRequest_shouldReturnPersistedProject(UniAsserter asserter) {
                String userId = "firebase-uid-123";
                String bucket = "test-bucket";
                String objectPath = "pending/" + userId + "/upload-id/video.mp4";
                ProjectRequest request = new ProjectRequest("Test Project", null, null,
                                "gs://" + bucket + "/" + objectPath, bucket, objectPath, "video.mp4", "video/mp4", 1024L);

                Project mockProject = new Project();
                mockProject.id = UUID.randomUUID();
                mockProject.title = request.title();
                mockProject.userId = userId;
                mockProject.status = "PROCESSING";
                mockProject.sourceUrl = "https://youtube.com/watch?v=abc";

                User mockUser = new User();
                mockUser.id = userId;
                mockUser.creditsRemaining = 5;
                mockUser.planMode = user.PlanMode.PRO;

                when(storageService.defaultBucketName()).thenReturn(bucket);
                when(storageService.getObjectMetadata(bucket, objectPath))
                                .thenReturn(new StorageService.StoredObjectMetadata(bucket, objectPath, 1024L, "video/mp4"));
                when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
                when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(mockUser));
                when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
                when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));
                when(aiClipperClient.processVideo(any(VideoProcessRequest.class)))
                                .thenReturn(Uni.createFrom()
                                                .item(new VideoProcessResponse("Processing started",
                                                                mockProject.id.toString())));

                asserter.assertThat(() -> projectService.createProject(userId, request),
                                project -> {
                                        assertNotNull(project);
                                        assertEquals("Test Project", project.title);
                                        assertEquals(userId, project.userId);
                                        assertEquals("PROCESSING", project.status);
                                        assertNotNull(project.sourceUrl);
                                        verify(projectMapper).toEntity(eq(request), eq(userId));
                                        verify(projectRepository).persist(any(Project.class));
                                        verify(aiClipperClient).processVideo(any(VideoProcessRequest.class));
                                });
        }

        @Test
        @RunOnVertxContext
        void testCreateProject_whenPythonServiceFails_shouldMarkFailedAndRefund(UniAsserter asserter) {
                String userId = "firebase-uid-789";
                String bucket = "test-bucket";
                String objectPath = "pending/" + userId + "/upload-id/video.mp4";
                ProjectRequest request = new ProjectRequest("Test Project", null, null,
                                "gs://" + bucket + "/" + objectPath, bucket, objectPath, "video.mp4", "video/mp4", 1024L);

                User mockUser = new User();
                mockUser.id = userId;
                mockUser.creditsRemaining = 5;
                mockUser.planMode = user.PlanMode.PRO;

                Project mockProject = new Project();
                mockProject.id = UUID.randomUUID();
                mockProject.title = request.title();
                mockProject.userId = userId;
                mockProject.status = "PROCESSING";

                // Setup Storage
                when(storageService.defaultBucketName()).thenReturn(bucket);
                when(storageService.getObjectMetadata(bucket, objectPath))
                                .thenReturn(new StorageService.StoredObjectMetadata(bucket, objectPath, 1024L, "video/mp4"));

                // Setup User deduction
                when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
                when(userRepository.persist(any(User.class)))
                                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));

                // Setup Project Creation
                when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
                when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));
                when(projectRepository.findById(mockProject.id)).thenReturn(Uni.createFrom().item(mockProject));

                // Python Client Failure
                when(aiClipperClient.processVideo(any(VideoProcessRequest.class)))
                                .thenReturn(Uni.createFrom()
                                                .failure(new RuntimeException("Python service unreachable")));

                asserter.assertThat(() -> projectService.createProject(userId, request),
                                project -> {
                                        assertNotNull(project);
                                        // Due to immediate catch logic, it should end with FAILED status and 5 credits
                                        // (4 + 1 refund)
                                        assertEquals("FAILED", project.status);
                                        assertEquals(5, mockUser.creditsRemaining);
                                        verify(aiClipperClient).processVideo(any(VideoProcessRequest.class));
                                        // Check persist was called twice: once for deduction, once for refund
                                        verify(userRepository, times(2)).persist(any(User.class));
                                });
        }

        @Test
        @RunOnVertxContext
        void testCreateProject_whenPersistFails_shouldThrowRuntimeException(UniAsserter asserter) {
                String userId = "firebase-uid-789";
                String bucket = "test-bucket";
                String objectPath = "pending/" + userId + "/upload-id/video.mp4";
                ProjectRequest request = new ProjectRequest("Test Project", null, null,
                                "gs://" + bucket + "/" + objectPath, bucket, objectPath, "video.mp4", "video/mp4", 1024L);

                Project mockProject = new Project();
                mockProject.title = request.title();

                User mockUser = new User();
                mockUser.id = userId;
                mockUser.creditsRemaining = 5;
                mockUser.planMode = user.PlanMode.PRO;

                when(storageService.defaultBucketName()).thenReturn(bucket);
                when(storageService.getObjectMetadata(bucket, objectPath))
                                .thenReturn(new StorageService.StoredObjectMetadata(bucket, objectPath, 1024L, "video/mp4"));
                when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
                when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(mockUser));
                when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);

                when(projectRepository.persist(any(Project.class)))
                                .thenReturn(Uni.createFrom().failure(new RuntimeException("Database error")));

                asserter.assertFailedWith(() -> projectService.createProject(userId, request),
                                throwable -> {
                                        assertTrue(throwable instanceof RuntimeException);
                                        assertEquals("Database error", throwable.getMessage());
                                });
        }

        @Test
        @RunOnVertxContext
        void testCreateProject_whenMetadataLookupFails_shouldStillCreateProject(UniAsserter asserter) {
                String userId = "firebase-uid-456";
                String bucket = "test-bucket";
                String objectPath = "pending/" + userId + "/upload-id/video.mp4";
                ProjectRequest request = new ProjectRequest("Test Project", null, null,
                                "gs://" + bucket + "/" + objectPath, bucket, objectPath, "video.mp4", "video/mp4", 1024L);

                Project mockProject = new Project();
                mockProject.id = UUID.randomUUID();
                mockProject.title = request.title();
                mockProject.userId = userId;
                mockProject.status = "PROCESSING";
                mockProject.sourceUrl = "https://youtube.com/watch?v=abc";

                User mockUser = new User();
                mockUser.id = userId;
                mockUser.creditsRemaining = 5;
                mockUser.planMode = user.PlanMode.PRO;

                when(storageService.defaultBucketName()).thenReturn(bucket);
                when(storageService.getObjectMetadata(bucket, objectPath))
                                .thenReturn(new StorageService.StoredObjectMetadata(bucket, objectPath, 1024L, "video/mp4"));
                when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
                when(userRepository.persist(any(User.class)))
                                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));
                when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
                when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));
                when(aiClipperClient.processVideo(any(VideoProcessRequest.class)))
                                .thenReturn(Uni.createFrom()
                                                .item(new VideoProcessResponse("Processing started",
                                                                mockProject.id.toString())));

                asserter.assertThat(() -> projectService.createProject(userId, request),
                                project -> {
                                        assertNotNull(project);
                                        assertEquals(0, project.durationSeconds);
                                        assertEquals(4, mockUser.creditsRemaining);
                                        verify(aiClipperClient).processVideo(any(VideoProcessRequest.class));
                                });
        }

        @Test
        @RunOnVertxContext
        void testCreateProjectFromUpload_whenValidRequest_shouldUploadViaBackendAndDispatchWorker(UniAsserter asserter)
                        throws Exception {
                String userId = "firebase-uid-upload";
                String bucket = "test-bucket";
                Path uploadedFile = Files.createTempFile("project-upload-", ".mp4");
                Files.write(uploadedFile, new byte[] { 1, 2, 3, 4 });

                FileUpload fileUpload = mock(FileUpload.class);
                when(fileUpload.fileName()).thenReturn("my video.mp4");
                when(fileUpload.filePath()).thenReturn(uploadedFile);
                when(fileUpload.contentType()).thenReturn("video/mp4");
                when(fileUpload.size()).thenReturn(4L);

                ProjectUploadRequest request = new ProjectUploadRequest();
                request.title = "Backend Upload Project";
                request.customPrompt = "find the best hooks";
                request.durationSeconds = "120";
                request.file = fileUpload;

                Project mockProject = new Project();
                mockProject.id = UUID.randomUUID();
                mockProject.title = request.title;
                mockProject.userId = userId;
                mockProject.status = "PROCESSING";

                User mockUser = new User();
                mockUser.id = userId;
                mockUser.creditsRemaining = 5;
                mockUser.planMode = user.PlanMode.PRO;

                when(storageService.storeObject(anyString(), eq("video/mp4"), any()))
                                .thenAnswer(invocation -> new StorageService.StoredObjectMetadata(
                                                bucket,
                                                invocation.getArgument(0, String.class),
                                                4L,
                                                "video/mp4"));
                when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
                when(userRepository.persist(any(User.class)))
                                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));
                when(projectMapper.toEntity(any(ProjectRequest.class), eq(userId))).thenReturn(mockProject);
                when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));
                when(aiClipperClient.processVideo(any(VideoProcessRequest.class)))
                                .thenReturn(Uni.createFrom()
                                                .item(new VideoProcessResponse("Processing started",
                                                                mockProject.id.toString())));

                asserter.assertThat(() -> projectService.createProjectFromUpload(userId, request),
                                project -> {
                                        assertNotNull(project);
                                        assertEquals("Backend Upload Project", project.title);
                                        assertEquals("PROCESSING", project.status);

                                        ArgumentCaptor<String> objectPathCaptor = ArgumentCaptor.forClass(String.class);
                                        verify(storageService).storeObject(objectPathCaptor.capture(), eq("video/mp4"), any());
                                        assertTrue(objectPathCaptor.getValue().startsWith("pending/" + userId + "/"));
                                        assertTrue(objectPathCaptor.getValue().endsWith("/my_video.mp4"));

                                        ArgumentCaptor<ProjectRequest> requestCaptor = ArgumentCaptor
                                                        .forClass(ProjectRequest.class);
                                        verify(projectMapper).toEntity(requestCaptor.capture(), eq(userId));
                                        ProjectRequest persistedRequest = requestCaptor.getValue();
                                        assertEquals("Backend Upload Project", persistedRequest.title());
                                        assertEquals("find the best hooks", persistedRequest.customPrompt());
                                        assertEquals(120, persistedRequest.durationSeconds());
                                        assertEquals("my video.mp4", persistedRequest.sourceFileName());
                                        assertEquals("video/mp4", persistedRequest.sourceContentType());
                                        assertEquals(4L, persistedRequest.sourceSizeBytes());
                                        assertEquals(bucket, persistedRequest.sourceBucket());
                                        assertEquals(objectPathCaptor.getValue(), persistedRequest.sourceObjectPath());
                                        assertEquals(
                                                        "gs://" + bucket + "/" + objectPathCaptor.getValue(),
                                                        persistedRequest.sourceStorageUri());

                                        ArgumentCaptor<VideoProcessRequest> processRequestCaptor = ArgumentCaptor
                                                        .forClass(VideoProcessRequest.class);
                                        verify(aiClipperClient).processVideo(processRequestCaptor.capture());
                                        VideoProcessRequest dispatchedRequest = processRequestCaptor.getValue();
                                        assertEquals("large-v3", dispatchedRequest.modelSize);
                                        assertNull(dispatchedRequest.language);
                                        verify(storageService, never()).deleteObject(anyString(), anyString());
                                });
        }

        @Test
        @RunOnVertxContext
        void testCreateProjectFromUpload_whenCreationFails_shouldDeleteUploadedObject(UniAsserter asserter)
                        throws Exception {
                String userId = "firebase-uid-upload-fail";
                String bucket = "test-bucket";
                Path uploadedFile = Files.createTempFile("project-upload-fail-", ".mp4");
                Files.write(uploadedFile, new byte[] { 9, 8, 7, 6 });

                FileUpload fileUpload = mock(FileUpload.class);
                when(fileUpload.fileName()).thenReturn("cleanup.mp4");
                when(fileUpload.filePath()).thenReturn(uploadedFile);
                when(fileUpload.contentType()).thenReturn("video/mp4");
                when(fileUpload.size()).thenReturn(4L);

                ProjectUploadRequest request = new ProjectUploadRequest();
                request.title = "Should Fail";
                request.durationSeconds = "30";
                request.file = fileUpload;

                User mockUser = new User();
                mockUser.id = userId;
                mockUser.creditsRemaining = 0;
                mockUser.planMode = user.PlanMode.PRO;

                when(storageService.storeObject(anyString(), eq("video/mp4"), any()))
                                .thenAnswer(invocation -> new StorageService.StoredObjectMetadata(
                                                bucket,
                                                invocation.getArgument(0, String.class),
                                                4L,
                                                "video/mp4"));
                when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));

                asserter.assertFailedWith(() -> projectService.createProjectFromUpload(userId, request),
                                throwable -> {
                                        assertTrue(throwable instanceof ForbiddenException);
                                        verify(storageService).deleteObject(eq(bucket),
                                                        argThat(path -> path.startsWith("pending/" + userId + "/")));
                                        verify(projectRepository, never()).persist(any(Project.class));
                                        verify(aiClipperClient, never()).processVideo(any(VideoProcessRequest.class));
                                });
        }

        // ── handleCompletion tests ──

        @Test
        @RunOnVertxContext
        void testHandleCompletion_whenCompleted_shouldUpdateStatusAndSaveClips(UniAsserter asserter) {
                UUID projectId = UUID.randomUUID();
                Project mockProject = new Project();
                mockProject.id = projectId;
                mockProject.status = "PROCESSING";

                List<ClipDTO> clipDTOs = List.of(
                                new ClipDTO(UUID.randomUUID().toString(), "Funny Moment", "A funny moment",
                                                "http://localhost:8000/static/clip1.mp4",
                                                "http://localhost:8000/static/thumb1.jpg", 
                                                null, null, null, null, null, null,
                                                10.0, 25.0,
                                                95.5),
                                new ClipDTO(UUID.randomUUID().toString(), "Epic Scene", "An epic scene",
                                                "http://localhost:8000/static/clip2.mp4",
                                                "http://localhost:8000/static/thumb2.jpg", 
                                                null, null, null, null, null, null,
                                                45.0, 60.0,
                                                88.0));

                ProjectCompletionDTO completion = new ProjectCompletionDTO("COMPLETED", null, null, null,
                                null, null, null, null, clipDTOs, null);

                when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
                when(clipRepository.persist(anyList())).thenReturn(Uni.createFrom().voidItem());

                asserter.assertThat(() -> projectService.handleCompletion(projectId, completion),
                                result -> {
                                        assertEquals("COMPLETED", mockProject.status);
                                        verify(clipRepository).persist(anyList());
                                });
        }

        @Test
        @RunOnVertxContext
        void testHandleCompletion_whenFailed_shouldUpdateStatusAndRefundCredits(UniAsserter asserter) {
                UUID projectId = UUID.randomUUID();
                String userId = "user123";
                Project mockProject = new Project();
                mockProject.id = projectId;
                mockProject.userId = userId;
                mockProject.status = "PROCESSING";
                mockProject.cost = 5; // Simulating a longer 10-minute video

                User mockUser = new User();
                mockUser.id = userId;
                mockUser.creditsRemaining = 5;

                ProjectCompletionDTO completion = new ProjectCompletionDTO("FAILED", null, null, null,
                                null, null, null, null, Collections.emptyList(), null);

                when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
                when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
                when(userRepository.persist(any(User.class)))
                                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));
                when(projectRepository.persist(any(Project.class)))
                                .thenAnswer(invocation -> Uni.createFrom().item((Project) invocation.getArgument(0)));

                asserter.assertThat(() -> projectService.handleCompletion(projectId, completion),
                                result -> {
                                        assertEquals("FAILED", mockProject.status);
                                        assertEquals(10, mockUser.creditsRemaining); // 5 + 5 refund
                                        verify(clipRepository, never()).persist(anyList());
                                        verify(userRepository).persist(any(User.class));
                                        verify(projectRepository).persist(any(Project.class));
                                });
        }

        @Test
        @RunOnVertxContext
        void testHandleCompletion_whenProjectNotFound_shouldThrowNotFound(UniAsserter asserter) {
                UUID projectId = UUID.randomUUID();
                ProjectCompletionDTO completion = new ProjectCompletionDTO("COMPLETED", null, null, null,
                                null, null, null, null, Collections.emptyList(), null);

                when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().nullItem());

                asserter.assertFailedWith(() -> projectService.handleCompletion(projectId, completion),
                                throwable -> {
                                        assertTrue(throwable instanceof NotFoundException);
                                        assertEquals("Project not found", throwable.getMessage());
                                });
        }

        // ── getProjects tests ──

        @Test
        @RunOnVertxContext
        void testGetProjects_whenMatchingProjectsExist_shouldReturnPagedResponse(UniAsserter asserter) {
                String userId = "firebase-uid-123";
                SpecificationRequest request = new SpecificationRequest();
                request.search = "test";
                request.page = 0;
                request.size = 20;

                Project project1 = new Project();
                project1.id = UUID.randomUUID();
                project1.title = "Test Project 1";
                project1.userId = userId;

                Project project2 = new Project();
                project2.id = UUID.randomUUID();
                project2.title = "Test Project 2";
                project2.userId = userId;

                when(projectRepository.findByTitleLike(request, userId))
                                .thenReturn(Uni.createFrom().item(List.of(project1, project2)));
                when(projectRepository.countByTitleLike(request, userId))
                                .thenReturn(Uni.createFrom().item(2L));

                asserter.assertThat(() -> projectService.getProjects(request, userId),
                                pagedResponse -> {
                                        assertNotNull(pagedResponse);
                                        assertEquals(2, pagedResponse.items().size());
                                        assertEquals(2L, pagedResponse.totalItems());
                                        assertEquals(0, pagedResponse.page());
                                        assertEquals(20, pagedResponse.size());
                                        assertEquals(1, pagedResponse.totalPages());
                                        assertEquals("Test Project 1", pagedResponse.items().get(0).title);
                                });
        }

        @Test
        @RunOnVertxContext
        void testGetProjects_whenNoMatchingProjects_shouldReturnEmptyPagedResponse(UniAsserter asserter) {
                String userId = "firebase-uid-123";
                SpecificationRequest request = new SpecificationRequest();
                request.search = "nonexistent";
                request.page = 0;
                request.size = 20;

                when(projectRepository.findByTitleLike(request, userId))
                                .thenReturn(Uni.createFrom().item(Collections.emptyList()));
                when(projectRepository.countByTitleLike(request, userId))
                                .thenReturn(Uni.createFrom().item(0L));

                asserter.assertThat(() -> projectService.getProjects(request, userId),
                                pagedResponse -> {
                                        assertNotNull(pagedResponse);
                                        assertTrue(pagedResponse.items().isEmpty());
                                        assertEquals(0L, pagedResponse.totalItems());
                                });
        }

        @Test
        @RunOnVertxContext
        void testGetProjects_whenRepositoryFails_shouldThrowRuntimeException(UniAsserter asserter) {
                String userId = "firebase-uid-123";
                SpecificationRequest request = new SpecificationRequest();
                request.search = "test";
                request.page = 0;
                request.size = 20;

                when(projectRepository.findByTitleLike(request, userId))
                                .thenReturn(Uni.createFrom().failure(new RuntimeException("Database unavailable")));

                asserter.assertFailedWith(() -> projectService.getProjects(request, userId),
                                throwable -> {
                                        assertTrue(throwable instanceof RuntimeException);
                                        assertEquals("Database unavailable", throwable.getMessage());
                                });
        }

        // ── getProjectById tests ──

        @Test
        @RunOnVertxContext
        void testGetProjectById_whenOwnerRequests_shouldReturnProject(UniAsserter asserter) {
                String userId = "firebase-uid-123";
                UUID projectId = UUID.randomUUID();

                Project mockProject = new Project();
                mockProject.id = projectId;
                mockProject.title = "My Project";
                mockProject.userId = userId;

                when(projectRepository.findById(projectId))
                                .thenReturn(Uni.createFrom().item(mockProject));

                asserter.assertThat(() -> projectService.getProjectById(projectId, userId),
                                project -> {
                                        assertNotNull(project);
                                        assertEquals(projectId, project.id);
                                        assertEquals("My Project", project.title);
                                        assertEquals(userId, project.userId);
                                });
        }

        @Test
        @RunOnVertxContext
        void testGetProjectById_whenNonOwnerRequests_shouldThrowForbidden(UniAsserter asserter) {
                String ownerId = "firebase-uid-owner";
                String requesterId = "firebase-uid-intruder";
                UUID projectId = UUID.randomUUID();

                Project mockProject = new Project();
                mockProject.id = projectId;
                mockProject.title = "Private Project";
                mockProject.userId = ownerId;

                when(projectRepository.findById(projectId))
                                .thenReturn(Uni.createFrom().item(mockProject));

                asserter.assertFailedWith(() -> projectService.getProjectById(projectId, requesterId),
                                throwable -> {
                                        assertTrue(throwable instanceof ForbiddenException);
                                        assertEquals("You do not have permission to view this project",
                                                        throwable.getMessage());
                                });
        }

        @Test
        @RunOnVertxContext
        void testGetProjectById_whenProjectNotFound_shouldThrowNotFound(UniAsserter asserter) {
                String userId = "firebase-uid-123";
                UUID projectId = UUID.randomUUID();

                when(projectRepository.findById(projectId))
                                .thenReturn(Uni.createFrom().nullItem());

                asserter.assertFailedWith(() -> projectService.getProjectById(projectId, userId),
                                throwable -> {
                                        assertTrue(throwable instanceof NotFoundException);
                                        assertEquals("Project not found", throwable.getMessage());
                                });
        }

        // ── sweepStuckProjects tests ──
        @Test
        @RunOnVertxContext
        void testSweepStuckProjects_whenStuckProjectsExist_shouldMarkFailedAndRefund(UniAsserter asserter) {
                Project stuckProject = new Project();
                stuckProject.id = UUID.randomUUID();
                stuckProject.userId = "user1";
                stuckProject.status = "PROCESSING";
                stuckProject.cost = 10; // 20 minute stuck project

                User mockUser = new User();
                mockUser.id = "user1";
                mockUser.creditsRemaining = 1;

                // Mock panache query for 24h old projects
                io.quarkus.hibernate.reactive.panache.PanacheQuery<Project> mockQuery = mock(
                                io.quarkus.hibernate.reactive.panache.PanacheQuery.class);
                when(projectRepository.find(eq("status = ?1 and createdAt < ?2"), any(Object[].class)))
                                .thenReturn(mockQuery);
                when(mockQuery.list()).thenReturn(Uni.createFrom().item(List.of(stuckProject)));

                when(projectRepository.findById(stuckProject.id)).thenReturn(Uni.createFrom().item(stuckProject));
                when(userRepository.findById("user1")).thenReturn(Uni.createFrom().item(mockUser));
                when(userRepository.persist(any(User.class)))
                                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));
                when(projectRepository.persist(any(Project.class)))
                                .thenAnswer(invocation -> Uni.createFrom().item((Project) invocation.getArgument(0)));

                asserter.assertThat(() -> projectService.sweepStuckProjects(),
                                result -> {
                                        assertEquals("FAILED", stuckProject.status);
                                        assertEquals(11, mockUser.creditsRemaining); // 1 + 10 refund
                                        verify(userRepository).persist(any(User.class));
                                        verify(projectRepository).persist(any(Project.class));
                                });
        }
}
