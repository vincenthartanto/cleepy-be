package project;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import common.dto.request.SpecificationRequest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import project.dto.ProjectRequest;

@QuarkusTest
public class ProjectServiceTest {

    @InjectMock
    ProjectRepository projectRepository;

    @InjectMock
    ProjectMapper projectMapper;

    @Inject
    ProjectService projectService;

    // ── createProject tests ──

    @Test
    @RunOnVertxContext
    void testCreateProject_whenValidRequest_shouldReturnPersistedProject(UniAsserter asserter) {
        String userId = "firebase-uid-123";
        ProjectRequest request = new ProjectRequest("Test Project", null);

        Project mockProject = new Project();
        mockProject.id = UUID.randomUUID();
        mockProject.title = request.title();
        mockProject.userId = userId;
        mockProject.status = "processing";

        when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
        when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));

        asserter.assertThat(() -> projectService.createProject(userId, request),
                project -> {
                    assertNotNull(project);
                    assertEquals("Test Project", project.title);
                    assertEquals(userId, project.userId);
                    assertEquals("processing", project.status);
                    verify(projectMapper).toEntity(eq(request), eq(userId));
                    verify(projectRepository).persist(any(Project.class));
                });
    }

    @Test
    @RunOnVertxContext
    void testCreateProject_whenPersistFails_shouldThrowRuntimeException(UniAsserter asserter) {
        String userId = "firebase-uid-789";
        ProjectRequest request = new ProjectRequest("Test Project", null);

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
