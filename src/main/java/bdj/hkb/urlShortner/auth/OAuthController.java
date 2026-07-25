package bdj.hkb.urlShortner.auth;

import bdj.hkb.urlShortner.auth.dto.AuthResponse;
import bdj.hkb.urlShortner.auth.dto.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthClientService oAuthClientService;

    @Value("${frontend.redirect")
    private String frontend;


    @GetMapping("/{provider}/start")
    public ResponseEntity<Void> startOAuth(@PathVariable OAuthProvider provider) {
        String redirectUrl = oAuthClientService.buildProviderStartUrl(provider);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<AuthResponse> exchangeOAuthCode(
            @RequestParam("code") String code) {

        AuthResponse tokens = oAuthClientService.exchangeCode(code);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.accessToken())
                .httpOnly(false)
                .path("/")
                .maxAge(Duration.ofHours(1))
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.refreshToken())
                .httpOnly(true)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .build();

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontend + "/dashboard"))
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .build();
    }
}
