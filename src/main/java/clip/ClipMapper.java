package clip;

import java.util.UUID;

import clip.dto.ClipRequest;
import jakarta.enterprise.context.ApplicationScoped;
import project.Project;

@ApplicationScoped
public class ClipMapper {

    public Clip toEntity(ClipRequest request, Project project) {
        Clip clip = new Clip();
        clip.id = UUID.randomUUID();
        clip.project = project;
        clip.title = request.title();
        clip.description = request.description();
        clip.videoUrl = request.videoUrl();
        clip.thumbnailUrl = request.thumbnailUrl();
        clip.startTime = request.startTime();
        clip.endTime = request.endTime();
        clip.viralScore = request.viralScore();
        clip.analysisResult = request.analysisResult();
        return clip;
    }
}
