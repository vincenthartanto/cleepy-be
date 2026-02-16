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
import project.dto.ProjectRequest;

@QuarkusTest
public class ProjectServiceTest {

    @InjectMock
    ProjectRepository projectRepository;

    @InjectMock
    ProjectMapper projectMapper;

    @Inject
    ProjectService projectService;

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
    void testCreateProject_whenWithCustomPrompt_shouldReturnPersistedProject(UniAsserter asserter) {
        String userId = "firebase-uid-456";
        ProjectRequest request = new ProjectRequest("Custom Prompt Project", "Analyze viral moments");

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
                    assertEquals("Custom Prompt Project", project.title);
                    assertEquals(userId, project.userId);
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

    @Test
    @RunOnVertxContext
    void testCreateProject_whenEmptyTitle_shouldReturnProjectWithEmptyTitle(UniAsserter asserter) {
        String userId = "firebase-uid-000";
        ProjectRequest request = new ProjectRequest("", null);

        Project mockProject = new Project();
        mockProject.id = UUID.randomUUID();
        mockProject.title = "";
        mockProject.userId = userId;
        mockProject.status = "processing";

        when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
        when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));

        asserter.assertThat(() -> projectService.createProject(userId, request),
                project -> {
                    assertNotNull(project);
                    assertEquals("", project.title);
                });
    }

    @Test
    @RunOnVertxContext
    void testCreateProject_whenVeryLongTitle_shouldReturnProjectWithLongTitle(UniAsserter asserter) {
        String userId = "firebase-uid-long";
        String longTitle = "A".repeat(1000);
        ProjectRequest request = new ProjectRequest(longTitle, null);

        Project mockProject = new Project();
        mockProject.id = UUID.randomUUID();
        mockProject.title = longTitle;
        mockProject.userId = userId;
        mockProject.status = "processing";

        when(projectMapper.toEntity(eq(request), eq(userId))).thenReturn(mockProject);
        when(projectRepository.persist(any(Project.class))).thenReturn(Uni.createFrom().item(mockProject));

        asserter.assertThat(() -> projectService.createProject(userId, request),
                project -> {
                    assertNotNull(project);
                    assertEquals(1000, project.title.length());
                    assertEquals(longTitle, project.title);
                });
    }

    @Test
    @RunOnVertxContext
    void testGetProjects_whenMatchingProjectsExist_shouldReturnProjectList(UniAsserter asserter) {
        SpecificationRequest request = new SpecificationRequest();
        request.search = "test";
        request.page = 0;
        request.size = 20;

        Project project1 = new Project();
        project1.id = UUID.randomUUID();
        project1.title = "Test Project 1";

        Project project2 = new Project();
        project2.id = UUID.randomUUID();
        project2.title = "Test Project 2";

        when(projectRepository.findByTitleLike(request))
                .thenReturn(Uni.createFrom().item(List.of(project1, project2)));

        asserter.assertThat(() -> projectService.getProjects(request),
                projects -> {
                    assertNotNull(projects);
                    assertEquals(2, projects.size());
                    assertEquals("Test Project 1", projects.get(0).title);
                    assertEquals("Test Project 2", projects.get(1).title);
                });
    }

    @Test
    @RunOnVertxContext
    void testGetProjects_whenNoMatchingProjects_shouldReturnEmptyList(UniAsserter asserter) {
        SpecificationRequest request = new SpecificationRequest();
        request.search = "nonexistent";
        request.page = 0;
        request.size = 20;

        when(projectRepository.findByTitleLike(request))
                .thenReturn(Uni.createFrom().item(Collections.emptyList()));

        asserter.assertThat(() -> projectService.getProjects(request),
                projects -> {
                    assertNotNull(projects);
                    assertTrue(projects.isEmpty());
                });
    }

    @Test
    @RunOnVertxContext
    void testGetProjects_whenNullSearch_shouldReturnAllProjects(UniAsserter asserter) {
        SpecificationRequest request = new SpecificationRequest();
        request.search = null;
        request.page = 0;
        request.size = 20;

        Project project = new Project();
        project.id = UUID.randomUUID();
        project.title = "Any Project";

        when(projectRepository.findByTitleLike(request))
                .thenReturn(Uni.createFrom().item(List.of(project)));

        asserter.assertThat(() -> projectService.getProjects(request),
                projects -> {
                    assertNotNull(projects);
                    assertEquals(1, projects.size());
                });
    }

    @Test
    @RunOnVertxContext
    void testGetProjects_whenRepositoryFails_shouldThrowRuntimeException(UniAsserter asserter) {
        SpecificationRequest request = new SpecificationRequest();
        request.search = "test";
        request.page = 0;
        request.size = 20;

        when(projectRepository.findByTitleLike(request))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Database unavailable")));

        asserter.assertFailedWith(() -> projectService.getProjects(request),
                throwable -> {
                    assertTrue(throwable instanceof RuntimeException);
                    assertEquals("Database unavailable", throwable.getMessage());
                });
    }
}
