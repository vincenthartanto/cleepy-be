package integration.dto;

public class VideoProcessRequest {

    public String projectId;
    public String sourceUrl;
    public String customPrompt;
    public boolean analyze = true;
    public int numMoments = 5;
    public double minDuration = 10.0;
    public double maxDuration = 60.0;
    public String modelSize = "base";
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

    public VideoProcessRequest(String projectId, String userId, String sourceUrl, String customPrompt) {
        this.projectId = projectId;
        this.userId = userId;
        this.sourceUrl = sourceUrl;
        this.customPrompt = customPrompt;
    }
}
