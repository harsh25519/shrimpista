package bdj.hkb.urlShortner.auth.internalDto;

public record AuthForgotPasswordRequest(
        String email,
        String clientId
) {
}
