package bdj.hkb.urlShortner.click;

import bdj.hkb.urlShortner.click.dto.ClickEventResponse;
import bdj.hkb.urlShortner.exceptionHandler.AccessDeniedException;
import bdj.hkb.urlShortner.exceptionHandler.UrlNotFoundException;
import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import bdj.hkb.urlShortner.url.Url;
import bdj.hkb.urlShortner.url.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClickEventReadService {
    private final ClickEventRepository clickRepository;
    private final UrlRepository urlRepository;

    public Page<ClickEventResponse> getClickHistory(String shortCode, JwtPrincipal principal,
                                                    int page, int size) {
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        if (url.getUserId() == null || !url.getUserId().equals(principal.userId())) {
            log.warn(
                    "User {} attempted to access analytics for URL {} owned by {}",
                    principal.userId(),
                    url.getId(),
                    url.getUserId()
            );
            throw new AccessDeniedException("You don't have access to this URL's analytics");
        }

        if (url.getDeletedAt() != null) {
            throw new UrlNotFoundException("URL not found");
        }

        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("clickedAt").descending());

        Page<ClickEvent> rawEvents = clickRepository
                .findByUrlIdOrderByClickedAtDesc(url.getId(), pageRequest);

        log.info(
                "User {} retrieved click history for URL {} (page={}, size={}, returned={})",
                principal.userId(),
                url.getId(),
                page,
                size,
                rawEvents.getNumberOfElements()
        );
        return rawEvents.map(event -> new ClickEventResponse(
                event.getIpAddress(),
                event.getUserAgent(),
                event.getReferrer(),
                event.getClickedAt()
        ));
    }
}
