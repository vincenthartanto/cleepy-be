package social;

public enum SocialPlatform {
    TIKTOK,
    YOUTUBE,
    INSTAGRAM;

    public static SocialPlatform fromPath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Platform is required");
        }
        return SocialPlatform.valueOf(value.trim().toUpperCase());
    }
}
