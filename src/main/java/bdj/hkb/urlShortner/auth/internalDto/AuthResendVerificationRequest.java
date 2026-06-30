package bdj.hkb.urlShortner.auth.internalDto;

public record AuthResendVerificationRequest(
        String email,
        String clientId
) {
}
