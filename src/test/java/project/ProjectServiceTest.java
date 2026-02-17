package project;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import clip.Clip;
import clip.ClipRepository;
import common.dto.request.SpecificationRequest;
import integration.AiClipperClient;
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

@QuarkusTest
public class ProjectServiceTest {

    @InjectMock
    ProjectRepository projectRepository;

    @InjectMock
    ProjectMapper projectMapper;

    @InjectMock
    @RestClient
    AiClipperClient aiClipperClient;

    @InjectMock
    ClipRepository clipRepository;

    @Inject
    ProjectService projectService;

    // ── createProject tests ──

    @Test
    @RunOnVertxContext
    void testCreateProject_whenValidRequest_shouldReturnPersistedProject(UniAsserter asserter) {
        String userId = "firebase-uid-123";
        ProjectRequest request = new ProjectRequest("Test Project", null, "https://youtube.com/watch?v=abc");

        Project mockProject = new Project();
        mockProject.id = UUID.randomUUID();
        mockProject.title = request.title();
        mockProject.userId = userId;
        mockProject.status = "PROCESSING";
        mockProject.sourceUrl = request.sourceUrl();

        when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
        when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));
        when(aiClipperClient.processVideo(any(VideoProcessRequest.class)))
                .thenReturn(Uni.createFrom()
                        .item(new VideoProcessResponse("Processing started", mockProject.id.toString())));

        asserter.assertThat(() -> projectService.createProject(userId, request),
                project -> {
                    assertNotNull(project);
                    assertEquals("Test Project", project.title);
                    assertEquals(userId, project.userId);
                    assertEquals("PROCESSING", project.status);
                    assertEquals("https://youtube.com/watch?v=abc", project.sourceUrl);
                    verify(projectMapper).toEntity(eq(request), eq(userId));
                    verify(projectRepository).persist(any(Project.class));
                    verify(aiClipperClient).processVideo(any(VideoProcessRequest.class));
                });
    }

    @Test
    @RunOnVertxContext
    void testCreateProject_whenPythonServiceFails_shouldThrowException(UniAsserter asserter) {
        String userId = "firebase-uid-789";
        ProjectRequest request = new ProjectRequest("Test Project", null, "https://youtube.com/watch?v=abc");

        Project mockProject = new Project();
        mockProject.id = UUID.randomUUID();
        mockProject.title = request.title();

        when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
        when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));
        when(aiClipperClient.processVideo(any(VideoProcessRequest.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Python service unreachable")));

        asserter.assertFailedWith(() -> projectService.createProject(userId, request),
                throwable -> {
                    assertTrue(throwable instanceof RuntimeException);
                    assertEquals("Python service unreachable", throwable.getMessage());
                });
    }

    @Test
    @RunOnVertxContext
    void testCreateProject_whenPersistFails_shouldThrowRuntimeException(UniAsserter asserter) {
        String userId = "firebase-uid-789";
        ProjectRequest request = new ProjectRequest("Test Project", null, "https://youtube.com/watch?v=abc");

        Project mockProject = new Project();
        mockProject.title = request.title();

        when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
        when(projectRepository.persist(any(Project.class)))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Database error")));

        asserter.assertFailedWith(() -> projectService.createProject(userId, request),
                throwable -> {
                    assertTrue(throwable instanceof RuntimeException);
                    assertEquals("Database error", throwable.getMessage());
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
                new ClipDTO("Funny Moment", "A funny moment", "http://localhost:8000/static/clip1.mp4", 10.0, 25.0,
                        95.5),
                new ClipDTO("Epic Scene", "An epic scene", "http://localhost:8000/static/clip2.mp4", 45.0, 60.0, 88.0));

        ProjectCompletionDTO completion = new ProjectCompletionDTO("COMPLETED", clipDTOs);

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
    void testHandleCompletion_whenFailed_shouldUpdateStatusWithoutClips(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        Project mockProject = new Project();
        mockProject.id = projectId;
        mockProject.status = "PROCESSING";

        ProjectCompletionDTO completion = new ProjectCompletionDTO("FAILED", Collections.emptyList());

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));

        asserter.assertThat(() -> projectService.handleCompletion(projectId, completion),
                result -> {
                    assertEquals("FAILED", mockProject.status);
                    verify(clipRepository, never()).persist(anyList());
                });
    }

    @Test
    @RunOnVertxContext
    void testHandleCompletion_whenProjectNotFound_shouldThrowNotFound(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ProjectCompletionDTO completion = new ProjectCompletionDTO("COMPLETED", Collections.emptyList());

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
                    assertEquals("You do not have permission to view this project", throwable.getMessage());
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
}
