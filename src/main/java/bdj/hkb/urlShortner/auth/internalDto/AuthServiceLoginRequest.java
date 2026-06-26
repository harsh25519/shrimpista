package bdj.hkb.urlShortner.auth.internalDto;

public record AuthServiceLoginRequest(
        String email,
        String password,
        String clientId
) {
}
