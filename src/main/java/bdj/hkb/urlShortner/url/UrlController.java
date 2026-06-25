package bdj.hkb.urlShortner.url;

import bdj.hkb.urlShortner.click.ClickIngestionService;
import bdj.hkb.urlShortner.url.dto.UrlCreateRequest;
import bdj.hkb.urlShortner.url.dto.UrlResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/urls")
@RequiredArgsConstructor
public class UrlController {
    private final UrlService urlService;
    private final ClickIngestionService clickIngestionService;

    // -------------------------------------------------------------------
    // CORE API: Create Short Link (Requires Valid JWT)
    // -------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<UrlResponse> createShortLink(
            @Valid @RequestBody UrlCreateRequest request,
            @AuthenticationPrincipal Authentication authentication) {

        // Extract the user ID directly from the validated JWT Principal
        UrlResponse response = urlService.createShortLink(request, authentication);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------
    // ROUTING ENGINE: Public Redirect (No Authentication Required)
    // -------------------------------------------------------------------
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {

        // 1. Fetch the long URL (hits Redis first, then Postgres)
        // 1. Get long URL (This gives us the long URL AND the urlId)
        // You'll need to update getLongUrl to return an object or string with pipe-delimited data
        String cachedData = urlService.getLongUrl(shortCode);

        // 2. Fire and Forget: Send to Redis buffer
        // 2. Fire the event with the shortCode, NOT the ID
        clickIngestionService.publishClickEvent(
                shortCode,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("Referer")
        );

        // 3. Perform the actual browser redirect
        return ResponseEntity.status(HttpStatus.FOUND) // 302 Redirect
                .location(URI.create(cachedData))
                .build();
    }
}
