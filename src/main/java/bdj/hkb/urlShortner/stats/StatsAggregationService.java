package bdj.hkb.urlShortner.stats;

import bdj.hkb.urlShortner.click.ClickEvent;
import bdj.hkb.urlShortner.click.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
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
    private final UrlStatsRepository urlStatsRepository;

    private static final String EVENT_QUEUE = "events:url:clicks";
    private static final String CLICK_UNIQUE_PREFIX = "click:unique:";

    private static final int BATCH_SIZE = 10000;
    private static final int CLEANUP_BATCH_SIZE = 1000;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void consumeClickEvents() {
        try {
            List<String> rawEvents = new ArrayList<>();
            String event;

            // Safe atomic pop — no race condition unlike size() + range() + trim()
            while (rawEvents.size() < BATCH_SIZE &&
                    (event = redisTemplate.opsForList().leftPop(EVENT_QUEUE)) != null) {
                rawEvents.add(event);
            }

            if (rawEvents.isEmpty()) return;

            List<ClickEvent> clickEvents = new ArrayList<>();

            for (String raw : rawEvents) {
                String[] parts = raw.split("\\|");
                if (parts.length >= 4) {
                    try {
                        clickEvents.add(ClickEvent.builder()
                                .urlId(Long.parseLong(parts[0]))   // urlId, not shortCode
                                .ipAddress(parts[1])               // already hashed by ClickIngestionService
                                .userAgent(parts[2])
                                .referrer(parts[3])
                                .clickedAt(OffsetDateTime.now())
                                .build());
                    } catch (NumberFormatException e) {
                        log.warn("Skipping malformed click event: {}", raw);
                    }
                }
            }

            if (clickEvents.isEmpty())
                return;

            // Batch insert raw click events
            clickRepository.saveAll(clickEvents);

            // Group by urlId — one DB upsert per URL, not per click
            Map<Long, Long> clicksPerUrl = clickEvents.stream()
                    .collect(Collectors.groupingBy(
                            ClickEvent::getUrlId,
                            Collectors.counting()
                    ));

            clicksPerUrl.forEach((urlId, count) -> {
                // Fetch current unique visitor count from HyperLogLog
                Long uniqueVisitors = redisTemplate.opsForHyperLogLog()
                        .size(CLICK_UNIQUE_PREFIX + urlId);

                urlStatsRepository.upsertStats(urlId, count,
                        uniqueVisitors != null ? uniqueVisitors : 0L);
            });

            log.debug("Flushed {} click events to DB", clickEvents.size());
        } catch (Exception e) {
            log.error("Failed to aggregate click events", e);
        }
    }

    // -------------------------------------------------------------------
    // CLEANUP — daily at 2 AM, delete click_events older than 30 days
    // -------------------------------------------------------------------
    @Scheduled(cron = "0 0 2 * * *")
    public void deleteOldClickEvents() {
        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusDays(30);
            int deleted;
            int total = 0;

            do {
                deleted = clickRepository.deleteOldBatch(cutoff, CLEANUP_BATCH_SIZE);
                total += deleted;
                if (deleted > 0) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();

                        log.warn("Click cleanup interrupted");

                        break;
                    }
                }
            } while (deleted > 0);

            log.info("Cleanup complete — deleted {} click events older than 30 days", total);
        } catch (Exception e) {
            log.error("Failed during click cleanup", e);
        }
    }
}
