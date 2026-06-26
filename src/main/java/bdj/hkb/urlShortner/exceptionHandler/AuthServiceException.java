package bdj.hkb.urlShortner.exceptionHandler;

import org.springframework.http.HttpStatusCode;

public class AuthServiceException extends RuntimeException {

    private final HttpStatusCode statusCode;
    private final String responseBody;

    public AuthServiceException(HttpStatusCode statusCode, String responseBody) {
        super("Auth service error: " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public HttpStatusCode getStatusCode() { return statusCode; }
    public String getResponseBody() { return responseBody; }
}