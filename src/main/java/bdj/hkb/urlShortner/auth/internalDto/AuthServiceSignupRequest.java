package bdj.hkb.urlShortner.auth.internalDto;

public record AuthServiceSignupRequest(
        String email,
        String password,
        String clientId,
        String clientSecret
) {
}
