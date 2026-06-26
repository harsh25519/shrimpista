package bdj.hkb.urlShortner.admin;

import bdj.hkb.urlShortner.admin.dto.AdminUrlResponse;
import bdj.hkb.urlShortner.exceptionHandler.UrlNotFoundException;
import bdj.hkb.urlShortner.url.Url;
import bdj.hkb.urlShortner.url.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;

    // -------------------------------------------------------------------
    // GLOBAL VIEW: See all URLs across the entire system
    // -------------------------------------------------------------------
    @Transactional(readOnly = true)
    public Page<AdminUrlResponse> getSystemUrls(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return urlRepository.findAll(pageRequest)
                .map(url -> new AdminUrlResponse(
                        url.getId(),
                        url.getShortCode(),
                        url.getLongUrl(),
                        url.getUserId(),
                        url.getIsActive(),
                        url.getDeletedAt() != null,
                        url.getCreatedAt()
                ));
    }

    // -------------------------------------------------------------------
    // GLOBAL KILL SWITCH: Deactivate any URL, regardless of owner
    // -------------------------------------------------------------------
    @Transactional
    public void takeDownUrl(Long urlId) {
        Url url = urlRepository.findById(urlId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found in system"));

        url.setDeletedAt(OffsetDateTime.now());
        url.setIsActive(false);

        // Instantly sever the fast-path routing
        redisTemplate.delete("url:route:" + url.getShortCode());
        redisTemplate.delete("url:route:" + url.getShortCode() + ":id");
    }
}
