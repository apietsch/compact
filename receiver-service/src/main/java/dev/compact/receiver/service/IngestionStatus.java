package dev.compact.receiver.service;

public record IngestionStatus(
        boolean running,
        String streamId,
        IngestionStrategy strategy,
        long processedChunks,
        long droppedChunks,
        String sourceUrl) {

    public static IngestionStatus idle(IngestionStrategy strategy, String sourceUrl) {
        return new IngestionStatus(false, "none", strategy, 0, 0, sourceUrl);
    }
}
