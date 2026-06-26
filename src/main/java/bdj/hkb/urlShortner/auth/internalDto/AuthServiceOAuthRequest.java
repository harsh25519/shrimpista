package bdj.hkb.urlShortner.auth.internalDto;

public record AuthServiceOAuthRequest(
        String token,
        String authProvider,
        String clientId,
        String clientSecret
) {
}
