package io.salad109.conjunctionapi.ingestion;

public class FailedSyncException extends RuntimeException {
    public FailedSyncException(Throwable throwable) {
        super(throwable);
    }
}
