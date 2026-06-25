package bdj.hkb.urlShortner.stats;

import bdj.hkb.urlShortner.click.ClickEvent;
import bdj.hkb.urlShortner.click.ClickEventRepository;
import bdj.hkb.urlShortner.url.Url;
import bdj.hkb.urlShortner.url.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StatsAggregationService {

    private final StringRedisTemplate redisTemplate;
    private final ClickEventRepository clickRepository;
    private final UrlRepository urlRepository; // Inject the repository here!

    private static final String EVENT_QUEUE = "events:url:clicks";

    @Scheduled(fixedDelay = 5000) // Runs every 5 seconds
    @Transactional
    public void consumeClickEvents() {
        Long queueSize = redisTemplate.opsForList().size(EVENT_QUEUE);
        if (queueSize == null || queueSize == 0) return;

        List<String> rawEvents = redisTemplate.opsForList().range(EVENT_QUEUE, 0, queueSize - 1);
        redisTemplate.opsForList().trim(EVENT_QUEUE, queueSize, -1);

        if (rawEvents == null || rawEvents.isEmpty()) return;

        List<ClickEvent> clickEvents = new ArrayList<>();

        for (String event : rawEvents) {
            String[] parts = event.split("\\|");
            if (parts.length >= 4) {
                String shortCode = parts[0];

                // Perform the query here in the background!
                Optional<Url> urlOpt = urlRepository.findByShortCode(shortCode);

                if (urlOpt.isPresent()) {
                    clickEvents.add(ClickEvent.builder()
                            .urlId(urlOpt.get().getId()) // Extract the ID
                            .ipAddress(parts[1])
                            .userAgent(parts[2])
                            .referrer(parts[3])
                            .build());
                }
            }
        }

        // Bulk insert the verified events
        if (!clickEvents.isEmpty()) {
            clickRepository.saveAll(clickEvents);
        }
    }
}
