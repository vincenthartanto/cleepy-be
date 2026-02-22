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

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.creditsRemaining = 5;
        mockUser.planMode = user.PlanMode.PRO;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(mockUser));
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
    void testCreateProject_whenPythonServiceFails_shouldMarkFailedAndRefund(UniAsserter asserter) {
        String userId = "firebase-uid-789";
        ProjectRequest request = new ProjectRequest("Test Project", null, "https://youtube.com/watch?v=abc");

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.creditsRemaining = 5;
        mockUser.planMode = user.PlanMode.PRO;

        Project mockProject = new Project();
        mockProject.id = UUID.randomUUID();
        mockProject.title = request.title();
        mockProject.userId = userId;
        mockProject.status = "PROCESSING";

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
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Python service unreachable")));

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
        ProjectRequest request = new ProjectRequest("Test Project", null, "https://youtube.com/watch?v=abc");

        Project mockProject = new Project();
        mockProject.title = request.title();

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.creditsRemaining = 5;
        mockUser.planMode = user.PlanMode.PRO;

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
                        "http://localhost:8000/static/clip1.mp4", "http://localhost:8000/static/thumb1.jpg", 10.0, 25.0,
                        95.5),
                new ClipDTO(UUID.randomUUID().toString(), "Epic Scene", "An epic scene",
                        "http://localhost:8000/static/clip2.mp4", "http://localhost:8000/static/thumb2.jpg", 45.0, 60.0,
                        88.0));

        ProjectCompletionDTO completion = new ProjectCompletionDTO("COMPLETED", null, clipDTOs);

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

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.creditsRemaining = 5;

        ProjectCompletionDTO completion = new ProjectCompletionDTO("FAILED", null, Collections.emptyList());

        when(projectRepository.findById(projectId)).thenReturn(Uni.createFrom().item(mockProject));
        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
        when(userRepository.persist(any(User.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));
        when(projectRepository.persist(any(Project.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((Project) invocation.getArgument(0)));

        asserter.assertThat(() -> projectService.handleCompletion(projectId, completion),
                result -> {
                    assertEquals("FAILED", mockProject.status);
                    assertEquals(6, mockUser.creditsRemaining); // Refunded
                    verify(clipRepository, never()).persist(anyList());
                    verify(userRepository).persist(any(User.class));
                    verify(projectRepository).persist(any(Project.class));
                });
    }

    @Test
    @RunOnVertxContext
    void testHandleCompletion_whenProjectNotFound_shouldThrowNotFound(UniAsserter asserter) {
        UUID projectId = UUID.randomUUID();
        ProjectCompletionDTO completion = new ProjectCompletionDTO("COMPLETED", null, Collections.emptyList());

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

    // ── sweepStuckProjects tests ──
    @Test
    @RunOnVertxContext
    void testSweepStuckProjects_whenStuckProjectsExist_shouldMarkFailedAndRefund(UniAsserter asserter) {
        Project stuckProject = new Project();
        stuckProject.id = UUID.randomUUID();
        stuckProject.userId = "user1";
        stuckProject.status = "PROCESSING";

        User mockUser = new User();
        mockUser.id = "user1";
        mockUser.creditsRemaining = 1;

        // Mock panache query for 24h old projects
        io.quarkus.hibernate.reactive.panache.PanacheQuery<Project> mockQuery = mock(
                io.quarkus.hibernate.reactive.panache.PanacheQuery.class);
        when(projectRepository.find(eq("status = ?1 and createdAt < ?2"), any(Object[].class))).thenReturn(mockQuery);
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
                    assertEquals(2, mockUser.creditsRemaining); // 1 + 1 refund
                    verify(userRepository).persist(any(User.class));
                    verify(projectRepository).persist(any(Project.class));
                });
    }
}
