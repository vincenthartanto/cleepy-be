package integration.dto;

public class VideoProcessRequest {

    public String projectId;
    public String sourceOriginUrl;
    public String sourceUrl;
    public String customPrompt;
    public String sourceStorageUri;
    public String sourceBucket;
    public String sourceObjectPath;
    public String sourceFileName;
    public String sourceContentType;
    public Long sourceSizeBytes;
    public boolean analyze = true;
    public Integer numMoments = null;
    public double minDuration = 10.0;
    public double maxDuration = 60.0;
    public String modelSize = "large-v3";
    public String language;
    public Integer minSpeakers;
    public Integer maxSpeakers;
    public boolean smartCrop = true;
    public String platform = "youtube_shorts";
    public boolean addCaptions = true;
    public String captionStyle = "classic";
    public int splitThreshold = 2;

    public String userId;

    public VideoProcessRequest() {
    }

    public VideoProcessRequest(
            String projectId,
            String userId,
            String customPrompt,
            String sourceOriginUrl,
            String sourceStorageUri,
            String sourceBucket,
            String sourceObjectPath,
            String sourceFileName,
            String sourceContentType,
            Long sourceSizeBytes) {
        this.projectId = projectId;
        this.userId = userId;
        this.customPrompt = customPrompt;
        this.sourceOriginUrl = sourceOriginUrl;
        this.sourceUrl = sourceOriginUrl;
        this.sourceStorageUri = sourceStorageUri;
        this.sourceBucket = sourceBucket;
        this.sourceObjectPath = sourceObjectPath;
        this.sourceFileName = sourceFileName;
        this.sourceContentType = sourceContentType;
        this.sourceSizeBytes = sourceSizeBytes;
    }
}
