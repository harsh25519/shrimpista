package bdj.hkb.urlShortner.url.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record UrlCreateRequest(
        @NotBlank(message = "URL cannot be empty")
        @URL(message = "Must be a valid URL format")
        String longUrl,

        // Optional: Let users name their links for the dashboard
        String title
) {}
