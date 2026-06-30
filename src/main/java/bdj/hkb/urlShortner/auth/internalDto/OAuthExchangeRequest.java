package bdj.hkb.urlShortner.auth.internalDto;

public record OAuthExchangeRequest (
        String code,
        String clientId,
        String clientSecret
){
}
