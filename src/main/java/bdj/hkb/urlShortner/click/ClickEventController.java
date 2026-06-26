package bdj.hkb.urlShortner.click;

import bdj.hkb.urlShortner.click.dto.ClickEventResponse;
import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clicks")
@RequiredArgsConstructor
public class ClickEventController {

    private final ClickEventReadService clickEventReadService;

    @GetMapping("/{shortCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ClickEventResponse>> getClickHistory(
            @PathVariable String shortCode,
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<ClickEventResponse> response = clickEventReadService.getClickHistory(shortCode, principal, page, size);
        return ResponseEntity.ok(response);
    }
}
