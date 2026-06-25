package bdj.hkb.urlShortner.click;

import bdj.hkb.urlShortner.click.dto.ClickEventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clicks")
@RequiredArgsConstructor
public class ClickEventController {

    private final ClickEventReadService clickEventReadService;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Page<ClickEventResponse>> getClickHistory(
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Page<ClickEventResponse> response = clickEventReadService.getClickHistory(shortCode, page, size);
        return ResponseEntity.ok(response);
    }
}
