package bdj.hkb.urlShortner.exceptionHandler;

public class UrlExpiredException extends RuntimeException {
    public UrlExpiredException() {
        super();
    }

    public UrlExpiredException(String message) {
        super(message);
    }

    public UrlExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
