package bdj.hkb.urlShortner.exceptionHandler;

public class UrlNotFoundException extends RuntimeException{
    public UrlNotFoundException() {
        super();
    }

    public UrlNotFoundException(String message) {
        super(message);
    }

    public UrlNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
