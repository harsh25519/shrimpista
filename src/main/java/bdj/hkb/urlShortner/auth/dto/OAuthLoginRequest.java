package bdj.hkb.urlShortner.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(
        @NotBlank String token,
        @NotBlank String authProvider
) {
}
