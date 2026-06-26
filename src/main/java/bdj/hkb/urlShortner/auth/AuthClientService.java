package bdj.hkb.urlShortner.auth;

import bdj.hkb.urlShortner.auth.dto.*;
import bdj.hkb.urlShortner.auth.internalDto.AuthServiceLoginRequest;
import bdj.hkb.urlShortner.auth.internalDto.AuthServiceOAuthRequest;
import bdj.hkb.urlShortner.auth.internalDto.AuthServiceSignupRequest;
import bdj.hkb.urlShortner.exceptionHandler.AuthServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class AuthClientService {

    private final RestTemplate restTemplate;

    @Value("${auth-service.base-url}")
    private String authServiceBaseUrl;

    @Value("${auth-service.client-id}")
    private String clientId;

    @Value("${auth-service.client-secret}")
    private String clientSecret;

    // -------------------------------------------------------------------
    // SIGNUP
    // -------------------------------------------------------------------
    public AuthResponse signup(UserSignupRequest request) {
        // Inject server-side credentials before forwarding
        AuthServiceSignupRequest authRequest = new AuthServiceSignupRequest(
                request.email(),
                request.password(),
                clientId,
                clientSecret
        );

        return post("/auth/signup", authRequest, AuthResponse.class);
    }

    // -------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------
    public AuthResponse login(UserLoginRequest request) {
        System.out.println("hahahahahha");
        AuthServiceLoginRequest authRequest = new AuthServiceLoginRequest(
                request.email(),
                request.password(),
                clientId
        );
        System.out.println("hahahahahha");

        return post("/auth/login", authRequest, AuthResponse.class);
    }

    // -------------------------------------------------------------------
    // OAUTH LOGIN
    // -------------------------------------------------------------------
    public AuthResponse oauthLogin(OAuthLoginRequest request) {
        AuthServiceOAuthRequest authRequest = new AuthServiceOAuthRequest(
                request.token(),
                request.authProvider(),
                clientId,
                clientSecret
        );

        return post("/auth/oauth-login", authRequest, AuthResponse.class);
    }

    // -------------------------------------------------------------------
    // REFRESH
    // -------------------------------------------------------------------
    public AuthResponse refresh(String refreshToken) {
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        return post("/auth/refresh", refreshRequest, AuthResponse.class);
    }

    // -------------------------------------------------------------------
    // LOGOUT
    // -------------------------------------------------------------------
    public void logout(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", bearerToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(
                    authServiceBaseUrl + "/auth/logout",
                    HttpMethod.POST,
                    entity,
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            // Token already expired or invalid — treat as successful logout
        }
    }

    // -------------------------------------------------------------------
    // HELPER
    // -------------------------------------------------------------------
    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            System.out.println("hahahahahha");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);

            ResponseEntity<T> response = restTemplate.exchange(
                    authServiceBaseUrl + path,
                    HttpMethod.POST,
                    entity,
                    responseType
            );
            System.out.println("hahahahahha");

            return response.getBody();
        } catch (HttpClientErrorException e) {
            throw new AuthServiceException(e.getStatusCode(), e.getResponseBodyAsString());
        }
    }


}
