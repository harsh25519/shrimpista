package bdj.hkb.urlShortner.url;

import bdj.hkb.urlShortner.click.ClickIngestionService;
import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import bdj.hkb.urlShortner.url.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

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
            @AuthenticationPrincipal JwtPrincipal principal) {

        UUID userId = principal != null ? principal.userId() : null;
        UrlResponse response = urlService.createShortLink(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------
    // ROUTING ENGINE: Public Redirect (No Authentication Required)
    // -------------------------------------------------------------------
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            HttpServletRequest request) {
        RedirectResult result = urlService.getLongUrl(shortCode);

        // Async click tracking — non-blocking
        clickIngestionService.publishClickEvent(
                result.urlId(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                request.getHeader("Referer")
        );

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(result.longUrl()))
                .build();
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UrlDashboardResponse>> getUserUrls(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(urlService.getUserUrls(principal, page, size));
    }

    @DeleteMapping("/{urlId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteUrl(
            @PathVariable Long urlId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        urlService.deleteUrl(urlId, principal);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{urlId}/toggle")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UrlResponse> toggleActive(
            @PathVariable Long urlId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(urlService.toggleActive(urlId, principal));
    }

    @PatchMapping("/{urlId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UrlResponse> updateUrl(
            @PathVariable Long urlId,
            @Valid @RequestBody UrlUpdateRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        UrlResponse response = urlService.updateUrl(urlId, request, principal);
        return ResponseEntity.ok(response);
    }
}
