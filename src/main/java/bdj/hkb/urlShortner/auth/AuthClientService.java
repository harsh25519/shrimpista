package bdj.hkb.urlShortner.auth;

import bdj.hkb.urlShortner.auth.dto.*;
import bdj.hkb.urlShortner.auth.internalDto.AuthForgotPasswordRequest;
import bdj.hkb.urlShortner.auth.internalDto.AuthResendVerificationRequest;
import bdj.hkb.urlShortner.auth.internalDto.AuthServiceLoginRequest;
import bdj.hkb.urlShortner.auth.internalDto.AuthServiceSignupRequest;
import bdj.hkb.urlShortner.exceptionHandler.AuthServiceException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
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
    public MessageResponse signup(UserSignupRequest request) {
        // Inject server-side credentials before forwarding
        AuthServiceSignupRequest authRequest = new AuthServiceSignupRequest(
                request.email(),
                request.password(),
                clientId,
                clientSecret
        );

        return post("/auth/signup", authRequest, MessageResponse.class);
    }

    // -------------------------------------------------------------------
    // LOGIN
    // -------------------------------------------------------------------
    public AuthResponse login(UserLoginRequest request) {
        AuthServiceLoginRequest authRequest = new AuthServiceLoginRequest(
                request.email(),
                request.password(),
                clientId
        );

        return post("/auth/login", authRequest, AuthResponse.class);
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
            log.info(
                    "Logout request ignored because auth service rejected the token (status={})",
                    e.getStatusCode()
            );
        }
        catch (RestClientException e) {
            log.error("Failed to contact authentication service during logout", e);
            throw new AuthServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication service is unavailable."
            );
        }
    }

    // -------------------------------------------------------------------
    // RESEND VERIFICATION EMAIL
    // -------------------------------------------------------------------
    public MessageResponse resendVerificationEmail(@Valid ResendVerificationRequest request) {
        AuthResendVerificationRequest resendRequest = new AuthResendVerificationRequest(
                request.email(),
                clientId
        );

        return post("/auth/resend-verification", resendRequest, MessageResponse.class);
    }

    // -------------------------------------------------------------------
    // RESET PASSWORD REQUEST
    // -------------------------------------------------------------------
    public MessageResponse requestPasswordReset(@Valid ForgotPasswordRequest request) {
        AuthForgotPasswordRequest passwordRequest = new AuthForgotPasswordRequest(
                request.email(),
                clientId
        );

        return post("/auth/forgot-password", passwordRequest, MessageResponse.class);
    }

    // -------------------------------------------------------------------
    // HELPER
    // -------------------------------------------------------------------
    private <T> T post(String path, Object body, Class<T> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(body, headers);

            ResponseEntity<T> response = restTemplate.exchange(
                    authServiceBaseUrl + path,
                    HttpMethod.POST,
                    entity,
                    responseType
            );

            return response.getBody();

        } catch (HttpClientErrorException e) {
            log.warn(
                    "Auth service returned {} for {}",
                    e.getStatusCode(),
                    path
            );

            throw new AuthServiceException(
                    e.getStatusCode(),
                    e.getResponseBodyAsString()
            );
        }
        catch (RestClientException e) {
            log.error(
                    "Unable to communicate with auth service while calling {}",
                    path,
                    e
            );

            throw new AuthServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication service is unavailable."
            );
        }

    }
}
