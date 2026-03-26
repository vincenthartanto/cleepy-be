package project;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import clip.Clip;
import clip.ClipRepository;
import common.dto.request.SpecificationRequest;
import common.dto.response.PagedResponse;
import integration.AiClipperClient;
import integration.dto.VideoMetadataDTO;
import integration.dto.VideoProcessRequest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.vertx.VertxContextSupport;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import project.dto.ProjectCompletionDTO;
import project.dto.ProjectRequest;
import project.dto.ProjectSourceEstimateResponse;
import project.dto.ProjectUploadRequest;
import user.PlanMode;
import user.User;
import user.UserRepository;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@ApplicationScoped
public class ProjectService {

    private static final Logger LOG = Logger.getLogger(ProjectService.class);
    private static final long MAX_UPLOAD_SIZE_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_AUTO_REQUEUE_ATTEMPTS = 1;
    private static final String SOURCE_KIND_UPLOAD_FILE = "UPLOAD_FILE";
    private static final String SOURCE_KIND_YOUTUBE_URL = "YOUTUBE_URL";
    private static final String SOURCE_PROVIDER_YOUTUBE = "youtube";
    private static final Set<String> ALLOWED_VIDEO_CONTENT_TYPES = Set.of(
            "video/mp4",
            "video/quicktime",
            "video/webm",
            "video/x-matroska");

    @Inject
    ProjectRepository projectRepository;

    @Inject
    ProjectMapper projectMapper;

    @Inject
    @RestClient
    AiClipperClient aiClipperClient;

    @Inject
    ClipRepository clipRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    StorageService storageService;

    @Inject
    Instance<ProjectService> self;

    @ConfigProperty(name = "project.media.base-path", defaultValue = "/api")
    String mediaBasePath;

    @WithSession
    public Uni<PagedResponse<Project>> getProjects(SpecificationRequest request, String userId) {
        return projectRepository.findByTitleLike(request, userId)
                .invoke(projects -> projects.forEach(this::applyProjectMediaUrls))
                .flatMap(projects -> projectRepository.countByTitleLike(request, userId)
                        .map(total -> PagedResponse.of(projects, total, request.page, request.size)));
    }

    @WithSession
    public Uni<Project> getProjectById(UUID projectId, String userId) {
        return findOwnedProject(projectId, userId)
                .invoke(this::applyProjectMediaUrls);
    }

    @WithSession
    public Uni<List<Clip>> getProjectClips(UUID projectId, String userId) {
        return findOwnedProject(projectId, userId)
                .flatMap(project -> clipRepository.list("project.id", projectId))
                .invoke(clips -> clips.forEach(clip -> applyClipMediaUrls(projectId, clip)));
    }

    @WithSession
    public Uni<Response> streamProjectMedia(UUID projectId, String userId, ProjectMediaKind mediaKind, String rangeHeader) {
        return findOwnedProject(projectId, userId)
                .map(project -> switch (mediaKind) {
                    case SOURCE -> buildStorageResponse(project.sourceBucket, project.sourceObjectPath, rangeHeader);
                    case THUMBNAIL -> buildStorageResponse(project.thumbnailBucket, project.thumbnailObjectPath, null);
                });
    }

    @WithSession
    public Uni<Response> streamClipMedia(UUID projectId, UUID clipId, String userId, ClipMediaKind mediaKind, String rangeHeader) {
        return findOwnedProject(projectId, userId)
                .flatMap(project -> clipRepository.find("id = ?1 and project.id = ?2", clipId, projectId).firstResult())
                .onItem().ifNull().failWith(() -> new NotFoundException("Clip not found"))
                .map(Clip.class::cast)
                .map(clip -> switch (mediaKind) {
                    case VIDEO -> buildStorageResponse(clip.videoBucket, clip.videoObjectPath, rangeHeader);
                    case THUMBNAIL -> buildStorageResponse(clip.thumbnailBucket, clip.thumbnailObjectPath, null);
                });
    }

    @WithSession
    public Uni<ProjectSourceEstimateResponse> estimateSource(String url) {
        if (url == null || url.isBlank()) {
            throw new BadRequestException("url is required.");
        }
        return aiClipperClient.getMetadata(url.trim())
                .map(metadata -> ProjectSourceEstimateResponse.from(
                        metadata,
                        calculateCost(metadata.duration())));
    }

    @WithTransaction
    public Uni<Project> createProject(String userId, ProjectRequest request) {
        return switch (normalizeSourceKind(request)) {
            case SOURCE_KIND_YOUTUBE_URL -> createProjectFromSourceUrl(userId, request);
            case SOURCE_KIND_UPLOAD_FILE -> createProjectFromStoredSourceRequest(userId, request);
            default -> Uni.createFrom().failure(new BadRequestException("Unsupported sourceKind."));
        };
    }

    public Uni<Project> createProjectFromUpload(String userId, ProjectUploadRequest request) {
        validateUploadRequest(request);
        Integer durationSeconds = parseDurationSeconds(request.durationSeconds);

        return VertxContextSupport.executeBlocking(() -> uploadSourceToStorage(userId, request.file))
                .flatMap(source -> self.get().createProjectFromUploadedSource(userId, request, durationSeconds, source)
                        .onFailure().invoke(throwable -> {
                            LOG.warnf(
                                    throwable,
                                    "Project creation failed after backend upload for user %s. Cleaning up %s/%s",
                                    userId,
                                    source.bucketName(),
                                    source.objectName());
                            storageService.deleteObject(source.bucketName(), source.objectName());
                        }));
    }

    @WithTransaction
    public Uni<Void> handleCompletion(UUID projectId, ProjectCompletionDTO completion) {
        return projectRepository.findById(projectId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Project not found"))
                .flatMap(project -> {
                    if ("FAILED".equals(completion.status())) {
                        return handleWorkerFailure(project, completion);
                    }
                    return handleWorkerSuccess(project, completion);
                })
                .onFailure(t -> !(t instanceof NotFoundException))
                .recoverWithUni(t -> markProjectFailed(projectId, t.getMessage(), "completion"));
    }

    @WithTransaction
    public Uni<Void> markProjectFailed(UUID projectId, String reason) {
        return markProjectFailed(projectId, reason, null);
    }

    @WithTransaction
    public Uni<Void> markProjectFailed(UUID projectId, String reason, String failedStage) {
        return projectRepository.findById(projectId)
                .onItem().ifNotNull().transformToUni(project -> {
                    if ("FAILED".equals(project.status)) {
                        return Uni.createFrom().voidItem();
                    }
                    project.status = "FAILED";
                    project.lastFailedStage = failedStage;
                    project.lastFailureReason = reason;
                    LOG.warnf("Project %s marked as FAILED. Reason: %s", projectId, reason);

                    return userRepository.findById(project.userId)
                            .flatMap(user -> {
                                if (user != null) {
                                    int refundAmount = project.cost > 0 ? project.cost : 1;
                                    user.creditsRemaining += refundAmount;
                                    LOG.infof("Refunded %d credit(s) to user %s for failed project %s", refundAmount,
                                            user.id, projectId);
                                    return userRepository.persist(user);
                                }
                                return Uni.createFrom().nullItem();
                            })
                            .flatMap(ignored -> projectRepository.persist(project))
                            .replaceWithVoid();
                })
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Project> retryProject(UUID projectId, String userId) {
        return projectRepository.findById(projectId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Project not found"))
                .onItem().invoke(project -> {
                    if (!userId.equals(project.userId)) {
                        throw new ForbiddenException("You do not have permission to retry this project");
                    }
                    ensureSourceAvailable(project);
                })
                .flatMap(project -> {
                    project.status = "PROCESSING";
                    project.lastFailedStage = null;
                    project.lastFailureReason = null;
                    project.workerRetryCount = 0;

                    return clipRepository.delete("project.id", projectId)
                            .flatMap(ignored -> userRepository.findById(userId))
                            .flatMap(user -> {
                                if (user == null) {
                                    throw new ForbiddenException("User not found for retry.");
                                }
                                if (user.creditsRemaining <= 0) {
                                    return Uni.createFrom().failure(
                                            new ForbiddenException("Insufficient credits for retry."));
                                }
                                user.creditsRemaining -= 1;
                                return userRepository.persist(user);
                            })
                            .flatMap(ignored -> projectRepository.persist(project))
                            .flatMap(ignored -> dispatchProcessing(project)
                                    .replaceWith(project)
                                    .onFailure().recoverWithUni(t -> {
                                        LOG.errorf(t, "Failed to reach AI Worker for project retry %s", project.id);
                                        return markProjectFailed(project.id,
                                                "AI service unavailable during retry: " + t.getMessage(),
                                                "dispatch")
                                                        .replaceWith(project);
                                    }));
                });
    }

    @Scheduled(every = "1h")
    @WithTransaction
    public Uni<Void> sweepStuckProjects() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        return projectRepository.find("status = ?1 and createdAt < ?2", "PROCESSING", threshold)
                .list()
                .flatMap(stuckProjects -> {
                    if (stuckProjects.isEmpty()) {
                        return Uni.createFrom().voidItem();
                    }
                    LOG.warnf("Sweeper found %d jobs stuck in PROCESSING for over 24 hours. Issuing refunds.",
                            stuckProjects.size());

                    return Multi.createFrom().iterable(stuckProjects)
                            .onItem()
                            .transformToUniAndConcatenate(project -> markProjectFailed(project.id,
                                    "24-hour timeout sweep (Silent crash presumed)",
                                    "watchdog"))
                            .collect().asList()
                            .replaceWithVoid();
                });
    }

    public int calculateCost(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) {
            return 1;
        }
        int minutes = (int) Math.ceil(durationSeconds / 60.0);
        int cost = (int) Math.ceil(minutes / 2.0);
        return Math.max(1, cost);
    }

    private Uni<Void> handleWorkerFailure(Project project, ProjectCompletionDTO completion) {
        String reason = completion.error() != null && !completion.error().isBlank()
                ? completion.error()
                : "Worker reported FAILED status via webhook.";
        applySourceArtifactMetadata(project, completion);
        project.lastFailedStage = completion.failedStage();
        project.lastFailureReason = reason;

        if (Boolean.TRUE.equals(completion.retryable())
                && project.workerRetryCount < MAX_AUTO_REQUEUE_ATTEMPTS
                && retrySourceStillAvailable(project)) {
            project.workerRetryCount += 1;
            project.status = "PROCESSING";
            return projectRepository.persist(project)
                    .flatMap(ignored -> dispatchProcessing(project))
                    .replaceWithVoid()
                    .onFailure().recoverWithUni(t -> markProjectFailed(
                            project.id,
                            "Auto requeue failed: " + t.getMessage(),
                            completion.failedStage()));
        }

        return markProjectFailed(project.id, reason, completion.failedStage());
    }

    private Uni<Void> handleWorkerSuccess(Project project, ProjectCompletionDTO completion) {
        project.status = completion.status();
        project.lastFailedStage = null;
        project.lastFailureReason = null;
        applySourceArtifactMetadata(project, completion);

        if (completion.thumbnailUrl() != null && !completion.thumbnailUrl().isBlank()) {
            project.thumbnailUrl = completion.thumbnailUrl();
        }
        if (completion.thumbnailStorageUri() != null && !completion.thumbnailStorageUri().isBlank()) {
            project.thumbnailStorageUri = completion.thumbnailStorageUri();
        }
        if (completion.thumbnailBucket() != null && !completion.thumbnailBucket().isBlank()) {
            project.thumbnailBucket = completion.thumbnailBucket();
        }
        if (completion.thumbnailObjectPath() != null && !completion.thumbnailObjectPath().isBlank()) {
            project.thumbnailObjectPath = completion.thumbnailObjectPath();
        }

        return adjustCredits(project, completion.actualDuration())
                .flatMap(ignored -> clipRepository.delete("project.id", project.id))
                .flatMap(ignored -> {
                    try {
                        List<ProjectCompletionDTO.ClipDTO> clipDtos = completion.clips() != null ? completion.clips() : List.of();
                        List<Clip> clips = clipDtos.stream().map(dto -> {
                            Clip clip = new Clip();
                            clip.id = UUID.fromString(dto.id());
                            clip.title = dto.name();
                            clip.description = dto.description();
                            clip.videoUrl = dto.videoUrl();
                            clip.thumbnailUrl = dto.thumbnailUrl();
                            clip.storageProvider = "gcs";
                            clip.videoStorageUri = dto.videoStorageUri();
                            clip.videoBucket = dto.videoBucket();
                            clip.videoObjectPath = dto.videoObjectPath();
                            clip.thumbnailStorageUri = dto.thumbnailStorageUri();
                            clip.thumbnailBucket = dto.thumbnailBucket();
                            clip.thumbnailObjectPath = dto.thumbnailObjectPath();
                            clip.viralScore = (int) dto.viralityScore();
                            clip.startTime = LocalTime.ofSecondOfDay((long) dto.startTime());
                            clip.endTime = LocalTime.ofSecondOfDay((long) dto.endTime());
                            clip.project = project;
                            if (clip.videoStorageUri == null && clip.thumbnailStorageUri == null) {
                                clip.storageProvider = null;
                            }
                            return clip;
                        }).toList();
                        return clipRepository.persist(clips).replaceWithVoid();
                    } catch (Exception e) {
                        return markProjectFailed(project.id, "Failed to save clips: " + e.getMessage(), "completion");
                    }
                })
                .flatMap(ignored -> projectRepository.persist(project).replaceWithVoid())
                .onItem().invoke(() -> cleanupSuccessfulArtifacts(project));
    }

    private Uni<User> findOrCreateUser(String userId) {
        return userRepository.findById(userId)
                .onItem().ifNull().switchTo(() -> {
                    User newUser = new User();
                    newUser.id = userId;
                    newUser.creditsRemaining = 3;
                    newUser.planMode = PlanMode.FREE_TRIAL;
                    return userRepository.persist(newUser);
                });
    }

    private Uni<User> deductCredits(User user, int cost, int durationSeconds) {
        if (user.creditsRemaining < cost) {
            return Uni.createFrom().failure(new ForbiddenException(
                    "Insufficient credits. This <b>%d-minute</b> video requires <b>%d credits</b>, but you only have <b>%d</b>. Please top up."
                            .formatted((durationSeconds / 60), cost, user.creditsRemaining)));
        }
        user.creditsRemaining -= cost;
        return userRepository.persist(user);
    }

    private Uni<Void> adjustCredits(Project project, Integer actualDuration) {
        if (actualDuration == null) {
            return Uni.createFrom().voidItem();
        }
        project.durationSeconds = actualDuration;
        int realCost = calculateCost(actualDuration);

        return userRepository.findById(project.userId)
                .flatMap(user -> {
                    if (user == null) {
                        project.cost = realCost;
                        return Uni.createFrom().voidItem();
                    }
                    if (realCost > project.cost) {
                        user.creditsRemaining -= (realCost - project.cost);
                    } else if (realCost < project.cost) {
                        user.creditsRemaining += (project.cost - realCost);
                    }
                    project.cost = realCost;
                    return userRepository.persist(user).replaceWithVoid();
                });
    }

    private Uni<Void> dispatchProcessing(Project project) {
        return aiClipperClient.processVideo(buildProcessRequest(project)).replaceWithVoid();
    }

    @WithTransaction
    Uni<Project> createProjectFromUploadedSource(
            String userId,
            ProjectUploadRequest uploadRequest,
            Integer durationSeconds,
            StorageService.StoredObjectMetadata source) {
        ProjectRequest storedSourceRequest = new ProjectRequest(
                uploadRequest.title.trim(),
                normalizeOptionalText(uploadRequest.customPrompt),
                durationSeconds,
                buildStorageUri(source.bucketName(), source.objectName()),
                source.bucketName(),
                source.objectName(),
                uploadRequest.file.fileName(),
                source.contentType(),
                source.sizeBytes());

        return createProjectFromStoredSource(userId, storedSourceRequest, source);
    }

    private Uni<Project> createProjectFromStoredSource(
            String userId,
            ProjectRequest request,
            StorageService.StoredObjectMetadata source) {
        LOG.infov(
                "Creating project for user {0} from uploaded object {1}/{2}",
                userId,
                source.bucketName(),
                source.objectName());

        if (source.sizeBytes() <= 0) {
            throw new BadRequestException("Uploaded source file is empty.");
        }

        int duration = request.durationSeconds() != null ? request.durationSeconds() : 0;
        int cost = calculateCost(duration);

        return findOrCreateUser(userId)
                .flatMap(user -> deductCredits(user, cost, duration))
                .flatMap(user -> {
                    Project project = projectMapper.toEntity(request, userId);
                    project.status = "PROCESSING";
                    project.durationSeconds = duration;
                    project.cost = cost;
                    project.workerRetryCount = 0;
                    project.lastFailedStage = null;
                    project.lastFailureReason = null;
                    project.sourceKind = normalizeSourceKind(request);
                    project.sourceBucket = source.bucketName();
                    project.sourceObjectPath = source.objectName();
                    project.sourceStorageUri = buildStorageUri(source.bucketName(), source.objectName());
                    project.sourceFileName = request.sourceFileName();
                    project.sourceContentType = source.contentType();
                    project.sourceSizeBytes = source.sizeBytes();

                    return projectRepository.persist(project)
                            .flatMap(saved -> dispatchProcessing(saved)
                                    .replaceWith(saved)
                                    .onFailure().recoverWithUni(t -> {
                                        LOG.errorf(t, "Failed to reach AI Worker for project %s", saved.id);
                                        return markProjectFailed(saved.id,
                                                "AI service unavailable: " + t.getMessage(),
                                                "dispatch")
                                                        .replaceWith(saved);
                                    }));
                });
    }

    private Uni<Project> createProjectFromStoredSourceRequest(String userId, ProjectRequest request) {
        validateStoredSourceRequest(userId, request);
        StorageService.StoredObjectMetadata source = storageService.getObjectMetadata(
                request.sourceBucket(),
                request.sourceObjectPath());
        return createProjectFromStoredSource(userId, request, source);
    }

    private Uni<Project> createProjectFromSourceUrl(String userId, ProjectRequest request) {
        validateSourceUrlRequest(request);
        return aiClipperClient.getMetadata(request.sourceOriginUrl().trim())
                .map(this::requireIngestableMetadata)
                .flatMap(metadata -> createProjectFromResolvedSourceUrl(userId, request, metadata));
    }

    private Uni<Project> createProjectFromResolvedSourceUrl(String userId, ProjectRequest request, VideoMetadataDTO metadata) {
        String resolvedTitle = request.title() != null && !request.title().isBlank()
                ? request.title().trim()
                : resolveDefaultTitle(metadata);
        Integer duration = metadata.duration() != null ? metadata.duration() : request.durationSeconds();
        int normalizedDuration = duration != null ? duration : 0;
        int cost = calculateCost(normalizedDuration);

        ProjectRequest normalizedRequest = new ProjectRequest(
                resolvedTitle,
                normalizeOptionalText(request.customPrompt()),
                normalizedDuration,
                SOURCE_KIND_YOUTUBE_URL,
                metadata.normalizedUrl() != null ? metadata.normalizedUrl() : request.sourceOriginUrl().trim(),
                metadata.provider() != null ? metadata.provider() : normalizeSourceProvider(request.sourceProvider()),
                null,
                null,
                null,
                null,
                null,
                null);

        return findOrCreateUser(userId)
                .flatMap(user -> deductCredits(user, cost, normalizedDuration))
                .flatMap(user -> {
                    Project project = projectMapper.toEntity(normalizedRequest, userId);
                    project.status = "PROCESSING";
                    project.title = normalizedRequest.title();
                    project.customPrompt = normalizedRequest.customPrompt();
                    project.durationSeconds = normalizedDuration;
                    project.cost = cost;
                    project.workerRetryCount = 0;
                    project.lastFailedStage = null;
                    project.lastFailureReason = null;
                    project.sourceKind = normalizedRequest.sourceKind();
                    project.sourceOriginUrl = normalizedRequest.sourceOriginUrl();
                    project.sourceProvider = normalizedRequest.sourceProvider();

                    return projectRepository.persist(project)
                            .flatMap(saved -> dispatchProcessing(saved)
                                    .replaceWith(saved)
                                    .onFailure().recoverWithUni(t -> {
                                        LOG.errorf(t, "Failed to reach AI Worker for URL project %s", saved.id);
                                        return markProjectFailed(saved.id,
                                                "AI service unavailable: " + t.getMessage(),
                                                "dispatch")
                                                        .replaceWith(saved);
                                    }));
                });
    }

    private Uni<Project> findOwnedProject(UUID projectId, String userId) {
        return projectRepository.findById(projectId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Project not found"))
                .invoke(project -> {
                    if (!userId.equals(project.userId)) {
                        throw new ForbiddenException("You do not have permission to view this project");
                    }
                });
    }

    private VideoProcessRequest buildProcessRequest(Project project) {
        return new VideoProcessRequest(
                project.id.toString(),
                project.userId,
                project.customPrompt,
                project.sourceOriginUrl,
                project.sourceStorageUri,
                project.sourceBucket,
                project.sourceObjectPath,
                project.sourceFileName,
                project.sourceContentType,
                project.sourceSizeBytes);
    }

    private void validateUploadRequest(ProjectUploadRequest request) {
        if (request == null) {
            throw new BadRequestException("Multipart project upload is required.");
        }
        if (request.title == null || request.title.isBlank()) {
            throw new BadRequestException("title is required.");
        }
        if (request.file == null) {
            throw new BadRequestException("file is required.");
        }
        validateUploadedFile(request.file.fileName(), request.file.contentType(), request.file.size());
        parseDurationSeconds(request.durationSeconds);
    }

    private void validateUploadedFile(String fileName, String contentType, long sizeBytes) {
        if (fileName == null || fileName.isBlank()) {
            throw new BadRequestException("fileName is required.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("contentType is required.");
        }
        if (!ALLOWED_VIDEO_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Unsupported video content type: " + contentType);
        }
        if (sizeBytes <= 0) {
            throw new BadRequestException("sizeBytes must be greater than zero.");
        }
        if (sizeBytes > MAX_UPLOAD_SIZE_BYTES) {
            throw new BadRequestException("Uploads larger than 2GB are not allowed.");
        }
    }

    private void validateCreateRequest(String userId, ProjectRequest request) {
        if (request == null) {
            throw new BadRequestException("Project request is required.");
        }
        if (normalizeSourceKind(request).equals(SOURCE_KIND_UPLOAD_FILE)) {
            validateStoredSourceRequest(userId, request);
            return;
        }
        validateSourceUrlRequest(request);
    }

    private void validateStoredSourceRequest(String userId, ProjectRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new BadRequestException("title is required.");
        }
        if (request.sourceBucket() == null || request.sourceBucket().isBlank()
                || request.sourceObjectPath() == null || request.sourceObjectPath().isBlank()) {
            throw new BadRequestException("Uploaded source metadata is required.");
        }
        validatePendingObjectOwnership(userId, request.sourceBucket(), request.sourceObjectPath());
    }

    private void validateSourceUrlRequest(ProjectRequest request) {
        if (request == null) {
            throw new BadRequestException("Project request is required.");
        }
        if (request.sourceOriginUrl() == null || request.sourceOriginUrl().isBlank()) {
            throw new BadRequestException("sourceOriginUrl is required.");
        }
    }

    private void validatePendingObjectOwnership(String userId, String bucket, String objectPath) {
        if (bucket == null || bucket.isBlank() || objectPath == null || objectPath.isBlank()) {
            throw new BadRequestException("bucket and objectPath are required.");
        }
        if (!bucket.equals(storageService.defaultBucketName())) {
            throw new ForbiddenException("Unexpected source bucket.");
        }
        String requiredPrefix = "pending/" + userId + "/";
        if (!objectPath.startsWith(requiredPrefix)) {
            throw new ForbiddenException("You do not have permission to access this uploaded source.");
        }
    }

    private String buildPendingObjectPath(String userId, String fileName) {
        String sanitized = sanitizeFileName(fileName);
        String uploadId = UUID.randomUUID().toString();
        return "pending/" + userId + "/" + uploadId + "/" + sanitized;
    }

    private String sanitizeFileName(String fileName) {
        String sanitized = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "upload.mp4" : sanitized;
    }

    private StorageService.StoredObjectMetadata uploadSourceToStorage(String userId, FileUpload file) {
        String objectPath = buildPendingObjectPath(userId, file.fileName());
        try (var inputStream = Files.newInputStream(file.filePath())) {
            StorageService.StoredObjectMetadata storedObject = storageService.storeObject(
                    objectPath,
                    file.contentType(),
                    inputStream);
            LOG.infov(
                    "Stored uploaded source for user {0}, object {1}, size {2} bytes",
                    userId,
                    storedObject.objectName(),
                    storedObject.sizeBytes());
            return storedObject;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read uploaded source file.", e);
        }
    }

    private String buildStorageUri(String bucketName, String objectPath) {
        return "gs://" + bucketName + "/" + objectPath;
    }

    private Integer parseDurationSeconds(String rawDurationSeconds) {
        if (rawDurationSeconds == null || rawDurationSeconds.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(rawDurationSeconds.trim());
            if (parsed < 0) {
                throw new BadRequestException("durationSeconds must be zero or greater.");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new BadRequestException("durationSeconds must be a valid integer.");
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeSourceKind(ProjectRequest request) {
        if (request == null) {
            return SOURCE_KIND_UPLOAD_FILE;
        }
        if (request.sourceKind() != null && !request.sourceKind().isBlank()) {
            return request.sourceKind().trim().toUpperCase(Locale.ROOT);
        }
        if (request.sourceOriginUrl() != null && !request.sourceOriginUrl().isBlank()) {
            return SOURCE_KIND_YOUTUBE_URL;
        }
        return SOURCE_KIND_UPLOAD_FILE;
    }

    private String normalizeSourceProvider(String sourceProvider) {
        if (sourceProvider == null || sourceProvider.isBlank()) {
            return SOURCE_PROVIDER_YOUTUBE;
        }
        return sourceProvider.trim().toLowerCase(Locale.ROOT);
    }

    private VideoMetadataDTO requireIngestableMetadata(VideoMetadataDTO metadata) {
        if (metadata == null) {
            throw new BadRequestException("Could not resolve media metadata.");
        }
        if (!Boolean.TRUE.equals(metadata.ingestable())) {
            throw new BadRequestException(resolveMetadataFailureMessage(metadata));
        }
        return metadata;
    }

    private String resolveMetadataFailureMessage(VideoMetadataDTO metadata) {
        if (metadata.failureMessage() != null && !metadata.failureMessage().isBlank()) {
            return metadata.failureMessage();
        }
        return "This YouTube URL cannot be processed.";
    }

    private String resolveDefaultTitle(VideoMetadataDTO metadata) {
        if (metadata.title() != null && !metadata.title().isBlank()) {
            return metadata.title().trim();
        }
        return "YouTube Video";
    }

    private void applySourceArtifactMetadata(Project project, ProjectCompletionDTO completion) {
        if (completion.sourceStorageUri() != null && !completion.sourceStorageUri().isBlank()) {
            project.sourceStorageUri = completion.sourceStorageUri();
        }
        if (completion.sourceBucket() != null && !completion.sourceBucket().isBlank()) {
            project.sourceBucket = completion.sourceBucket();
        }
        if (completion.sourceObjectPath() != null && !completion.sourceObjectPath().isBlank()) {
            project.sourceObjectPath = completion.sourceObjectPath();
        }
        if (completion.sourceFileName() != null && !completion.sourceFileName().isBlank()) {
            project.sourceFileName = completion.sourceFileName();
        }
        if (completion.sourceContentType() != null && !completion.sourceContentType().isBlank()) {
            project.sourceContentType = completion.sourceContentType();
        }
        if (completion.sourceSizeBytes() != null && completion.sourceSizeBytes() > 0) {
            project.sourceSizeBytes = completion.sourceSizeBytes();
        }
    }

    private void applyProjectMediaUrls(Project project) {
        if (project.thumbnailBucket != null && project.thumbnailObjectPath != null) {
            project.thumbnailUrl = buildProjectMediaUrl(project.id, "thumbnail");
        }
        if (project.sourceBucket != null && project.sourceObjectPath != null) {
            project.sourceUrl = buildProjectMediaUrl(project.id, "source");
        }
    }

    private void applyClipMediaUrls(UUID projectId, Clip clip) {
        if (clip.videoBucket != null && clip.videoObjectPath != null) {
            clip.videoUrl = buildClipMediaUrl(projectId, clip.id, "video");
        }
        if (clip.thumbnailBucket != null && clip.thumbnailObjectPath != null) {
            clip.thumbnailUrl = buildClipMediaUrl(projectId, clip.id, "thumbnail");
        }
    }

    private String buildProjectMediaUrl(UUID projectId, String mediaName) {
        return normalizedMediaBasePath() + "/project/" + projectId + "/media/" + mediaName;
    }

    private String buildClipMediaUrl(UUID projectId, UUID clipId, String mediaName) {
        return normalizedMediaBasePath() + "/project/" + projectId + "/clips/" + clipId + "/media/" + mediaName;
    }

    private String normalizedMediaBasePath() {
        String configuredPath = mediaBasePath == null ? "" : mediaBasePath.trim();
        if (configuredPath.isEmpty()) {
            return "";
        }
        if (configuredPath.endsWith("/")) {
            configuredPath = configuredPath.substring(0, configuredPath.length() - 1);
        }
        if (configuredPath.startsWith("http://") || configuredPath.startsWith("https://")) {
            return configuredPath;
        }
        return configuredPath.startsWith("/") ? configuredPath : "/" + configuredPath;
    }

    private Response buildStorageResponse(String bucket, String objectPath, String rangeHeader) {
        if (bucket == null || bucket.isBlank() || objectPath == null || objectPath.isBlank()) {
            throw new NotFoundException("Requested media is unavailable.");
        }

        StorageService.StoredObjectStream objectStream;
        try {
            objectStream = storageService.openObjectStream(bucket, objectPath, rangeHeader);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Requested media was not found.");
        }

        long contentLength = objectStream.totalSizeBytes() == 0
                ? 0
                : objectStream.endByte() - objectStream.startByte() + 1;

        Response.ResponseBuilder response = objectStream.partialContent()
                ? Response.status(Response.Status.PARTIAL_CONTENT)
                : Response.ok();

        response.entity(objectStream.output())
                .type(objectStream.contentType())
                .header("Accept-Ranges", "bytes")
                .header("Cache-Control", "private, max-age=300")
                .header("Content-Length", contentLength);

        if (objectStream.partialContent()) {
            response.header(
                    "Content-Range",
                    "bytes %d-%d/%d".formatted(
                            objectStream.startByte(),
                            objectStream.endByte(),
                            objectStream.totalSizeBytes()));
        }

        return response.build();
    }

    private boolean sourceStillExists(Project project) {
        if (project.sourceBucket == null || project.sourceObjectPath == null) {
            return false;
        }
        return storageService.objectExists(project.sourceBucket, project.sourceObjectPath);
    }

    private boolean retrySourceStillAvailable(Project project) {
        return sourceStillExists(project)
                || (SOURCE_KIND_YOUTUBE_URL.equals(project.sourceKind)
                        && project.sourceOriginUrl != null
                        && !project.sourceOriginUrl.isBlank());
    }

    private void ensureSourceAvailable(Project project) {
        if (!retrySourceStillAvailable(project)) {
            if (SOURCE_KIND_YOUTUBE_URL.equals(project.sourceKind)) {
                throw new BadRequestException("The original YouTube source is no longer available. Please create the project again.");
            }
            throw new BadRequestException("Uploaded source has expired. Please re-upload the video.");
        }
    }

    private void cleanupSuccessfulArtifacts(Project project) {
        if (project.sourceBucket == null || project.sourceObjectPath == null) {
            return;
        }
        if (!SOURCE_KIND_YOUTUBE_URL.equals(project.sourceKind)) {
            storageService.deleteObject(project.sourceBucket, project.sourceObjectPath);
        }
        storageService.deletePrefix(project.sourceBucket, "checkpoints/" + project.userId + "/" + project.id + "/");
    }

    public enum ProjectMediaKind {
        SOURCE,
        THUMBNAIL
    }

    public enum ClipMediaKind {
        VIDEO,
        THUMBNAIL
    }
}
