package project.dto;

import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.PartType;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public class ProjectUploadRequest {

    @RestForm("title")
    @PartType(MediaType.TEXT_PLAIN)
    public String title;

    @RestForm("customPrompt")
    @PartType(MediaType.TEXT_PLAIN)
    public String customPrompt;

    @RestForm("durationSeconds")
    @PartType(MediaType.TEXT_PLAIN)
    public String durationSeconds;

    @RestForm("file")
    public FileUpload file;
}
