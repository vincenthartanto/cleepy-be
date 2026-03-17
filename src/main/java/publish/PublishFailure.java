package publish;

public class PublishFailure extends RuntimeException {

    private final boolean retryable;

    public PublishFailure(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public PublishFailure(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
