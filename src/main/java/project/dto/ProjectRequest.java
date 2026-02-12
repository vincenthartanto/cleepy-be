package project.dto;

public record ProjectRequest(
    String title,
    String userId,
    String status,
    String thumbnailUrl
) {}