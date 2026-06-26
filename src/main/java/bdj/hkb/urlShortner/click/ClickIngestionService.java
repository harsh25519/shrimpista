package bdj.hkb.urlShortner.click;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
public class ClickIngestionService {

    private final StringRedisTemplate redisTemplate;

    // This is the name of our Redis List (our buffer)
    private static final String EVENT_QUEUE = "events:url:clicks";

    public void publishClickEvent(Long urlId, String ipAddress,
                                  String userAgent, String referrer) {
        String hashedIp = hashIp(ipAddress);

        // HyperLogLog for unique visitors
        redisTemplate.opsForHyperLogLog()
                .add("click:unique:" + urlId, hashedIp);

        // Queue for batch DB write
        String payload = String.format("%s|%s|%s|%s",
                urlId,
                hashedIp,
                (userAgent != null ? userAgent : "unknown"),
                (referrer != null ? referrer : "unknown")
        );

        redisTemplate.opsForList().rightPush("events:url:clicks", payload);
    }

    private String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(ip.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
