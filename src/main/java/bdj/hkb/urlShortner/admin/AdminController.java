package bdj.hkb.urlShortner.admin;

import bdj.hkb.urlShortner.admin.dto.AdminUrlResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
// THE VAULT DOOR: Rejects any JWT that does not contain the ADMIN role
@PreAuthorize("hasAuthority('ADMIN') or hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/urls")
    public ResponseEntity<Page<AdminUrlResponse>> getAllSystemUrls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return ResponseEntity.ok(adminService.getSystemUrls(page, size));
    }

    @DeleteMapping("/urls/{urlId}")
    public ResponseEntity<Void> forceDeleteUrl(@PathVariable Long urlId) {
        adminService.takeDownUrl(urlId);
        return ResponseEntity.noContent().build();
    }
}