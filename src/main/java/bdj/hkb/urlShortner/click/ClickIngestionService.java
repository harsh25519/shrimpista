package bdj.hkb.urlShortner.click;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClickIngestionService {

    private final StringRedisTemplate redisTemplate;

    // This is the name of our Redis List (our buffer)
    private static final String EVENT_QUEUE = "events:url:clicks";

    public void publishClickEvent(String shortCode, String ipAddress, String userAgent, String referrer) {
        // Payload now uses shortCode instead of urlId
        String payload = String.format("%s|%s|%s|%s",
                shortCode,
                (ipAddress != null ? ipAddress : "unknown"),
                (userAgent != null ? userAgent : "unknown"),
                (referrer != null ? referrer : "unknown")
        );

        redisTemplate.opsForList().rightPush(EVENT_QUEUE, payload);
    }
}
