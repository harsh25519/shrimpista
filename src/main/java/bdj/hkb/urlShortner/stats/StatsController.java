package bdj.hkb.urlShortner.stats;


import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import bdj.hkb.urlShortner.stats.dto.StatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/{shortCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StatsResponse> getStats(
            @PathVariable String shortCode,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(statsService.getStatsSummary(shortCode, principal));
    }
}
