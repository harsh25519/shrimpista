package bdj.hkb.urlShortner.auth;

import bdj.hkb.urlShortner.auth.dto.AuthResponse;
import bdj.hkb.urlShortner.auth.dto.OAuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthClientService oAuthClientService;


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

        return ResponseEntity.ok(oAuthClientService.exchangeCode(code));
    }
}
