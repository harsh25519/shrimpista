package bdj.hkb.urlShortner.click;

import bdj.hkb.urlShortner.click.dto.ClickEventResponse;
import bdj.hkb.urlShortner.url.Url;
import bdj.hkb.urlShortner.url.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClickEventReadService {
    private final ClickEventRepository clickRepository;
    private final UrlRepository urlRepository;

    public Page<ClickEventResponse> getClickHistory(String shortCode, int page, int size) {
        // 1. Get the URL entity to find its ID
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("Short code not found"));

        // 2. Create the pagination request
        PageRequest pageRequest = PageRequest.of(page, size);

        // 3. Fetch the raw paginated entities from the database
        Page<ClickEvent> rawEvents = clickRepository.findByUrlIdOrderByClickedAtDesc(url.getId(), pageRequest);

        // 4. Map the Page of Entities to a Page of DTOs
        return rawEvents.map(event -> new ClickEventResponse(
                event.getIpAddress(),
                event.getUserAgent(),
                event.getReferrer(),
                event.getClickedAt()
        ));
    }
}
