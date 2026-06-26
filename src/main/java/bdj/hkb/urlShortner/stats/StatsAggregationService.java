package bdj.hkb.urlShortner.stats;

import bdj.hkb.urlShortner.click.ClickEvent;
import bdj.hkb.urlShortner.click.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsAggregationService {

    private final StringRedisTemplate redisTemplate;
    private final ClickEventRepository clickRepository;
    private final UrlStatsRepository statsRepository;

    private static final String EVENT_QUEUE = "events:url:clicks";
    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void consumeClickEvents() {
        List<ClickEvent> clickEvents = new ArrayList<>();

        // 1. Thread-Safe Popping (Eliminates LTRIM race condition)
        for (int i = 0; i < BATCH_SIZE; i++) {
            // rightPop pulls and removes the oldest event atomically
            String rawEvent = redisTemplate.opsForList().rightPop(EVENT_QUEUE);
            if (rawEvent == null) {
                break; // Queue is empty, stop pulling
            }

            String[] parts = rawEvent.split("\\|");
            if (parts.length >= 4) {
                try {
                    clickEvents.add(ClickEvent.builder()
                            .urlId(Long.parseLong(parts[0])) // Now we have the ID directly!
                            .ipAddress(parts[1])             // This is already hashed
                            .userAgent(parts[2])
                            .referrer(parts[3])
                            .build());
                } catch (NumberFormatException e) {
                    log.error("Malformed URL ID in click event: {}", parts[0]);
                }
            }
        }

        if (clickEvents.isEmpty()) {
            return;
        }

        // 2. Save the raw audit logs
        clickRepository.saveAll(clickEvents);

        // 3. Group clicks by URL ID to optimize DB calls
        Map<Long, Long> clicksPerUrl = clickEvents.stream()
                .collect(Collectors.groupingBy(ClickEvent::getUrlId, Collectors.counting()));

        // 4. Fetch HLL stats and update the DB
        clicksPerUrl.forEach((urlId, newClicks) -> {
            // Get the absolute total of unique visitors directly from HyperLogLog
            Long uniqueVisitors = redisTemplate.opsForHyperLogLog().size("click:unique:" + urlId);
            long safeUniqueVisitors = uniqueVisitors != null ? uniqueVisitors : 0L;

            // Execute the Upsert
            statsRepository.incrementClicksAndUpdateUnique(urlId, newClicks, safeUniqueVisitors);
        });

        log.debug("Processed batch of {} clicks for {} URLs", clickEvents.size(), clicksPerUrl.size());
    }
}
