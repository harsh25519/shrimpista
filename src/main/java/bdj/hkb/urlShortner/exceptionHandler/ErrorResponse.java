package bdj.hkb.urlShortner.exceptionHandler;

import java.time.OffsetDateTime;

public record ErrorResponse (
        int status,
        String message,
        OffsetDateTime timestamp
){
}
