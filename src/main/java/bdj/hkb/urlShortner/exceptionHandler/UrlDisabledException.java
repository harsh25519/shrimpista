package bdj.hkb.urlShortner.exceptionHandler;

public class UrlDisabledException extends RuntimeException {
    public UrlDisabledException() {
        super();
    }

    public UrlDisabledException(String message) {
        super(message);
    }

    public UrlDisabledException(String message, Throwable cause) {
        super(message, cause);
    }
}
