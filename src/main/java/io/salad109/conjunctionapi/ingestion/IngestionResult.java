package io.salad109.conjunctionapi.ingestion;

public record IngestionResult(int processed, int created, int updated, int skipped, int deleted) {
}
