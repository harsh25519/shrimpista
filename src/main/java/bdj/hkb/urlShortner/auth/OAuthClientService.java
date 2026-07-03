package bdj.hkb.urlShortner.auth;

import bdj.hkb.urlShortner.auth.dto.AuthResponse;
import bdj.hkb.urlShortner.auth.dto.OAuthProvider;
import bdj.hkb.urlShortner.auth.internalDto.OAuthExchangeRequest;
import bdj.hkb.urlShortner.exceptionHandler.AuthServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthClientService {


    private final RestTemplate restTemplate;

    @Value("${auth-service.base-url}")
    private String authServiceBaseUrl;

    @Value("${auth-service.client-id}")
    private String clientId;

    @Value("${auth-service.client-secret}")
    private String clientSecret;


    // -------------------------------------------------------------------
    // Build the redirect URL to auth-service's /oauth/{provider}/start
    // Frontend never sees clientId — this stays server-side
    // -------------------------------------------------------------------
    public String buildProviderStartUrl(OAuthProvider provider) {
        return UriComponentsBuilder.fromUriString(authServiceBaseUrl)
                .pathSegment("oauth", provider.name().toLowerCase(), "start")
                .queryParam("clientId", clientId)
                .toUriString();
    }

    // -------------------------------------------------------------------
    // Forward the bridge code to auth-service for the real token exchange
    // -------------------------------------------------------------------
    public AuthResponse exchangeCode(String code) {
        OAuthExchangeRequest exchangeRequest =
                new OAuthExchangeRequest(code, clientId, clientSecret);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<OAuthExchangeRequest> entity = new HttpEntity<>(exchangeRequest, headers);

            ResponseEntity<AuthResponse> response = restTemplate.exchange(
                    authServiceBaseUrl + "/oauth/exchange",
                    HttpMethod.POST,
                    entity,
                    AuthResponse.class
            );

            return response.getBody();
        } catch (RestClientException e) {
            log.error("OAuth exchange request to auth-service failed", e);

            throw new AuthServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication service is unavailable."
            );
        }
    }
}
