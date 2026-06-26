package bdj.hkb.urlShortner.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistEventSubscriber implements MessageListener {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "blacklist:";

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String payload = new String(message.getBody());
            String[] parts = payload.split(":");
            String jti = parts[0];
            long remainingMillis = Long.parseLong(parts[1]);

            if (remainingMillis > 0) {
                redisTemplate.opsForValue().set(
                        PREFIX + jti,
                        "true",
                        Duration.ofMillis(remainingMillis)
                );
                log.debug("Blacklisted JWT received via Pub/Sub: {}", jti);
            }
        } catch (Exception e) {
            log.error("Failed to process blacklist event: {}", e.getMessage());
        }
    }
}