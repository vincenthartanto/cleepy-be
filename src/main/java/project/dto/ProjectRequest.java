package project.dto;

public record ProjectRequest(
                String title,
                String customPrompt,
                String sourceUrl) {
}