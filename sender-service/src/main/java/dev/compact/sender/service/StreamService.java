package dev.compact.sender.service;

import dev.compact.sender.config.StreamProperties;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class StreamService {

    private static final Logger log = LoggerFactory.getLogger(StreamService.class);

    private final StreamProperties properties;

    public StreamService(StreamProperties properties) {
        this.properties = properties;
    }

    public Flux<String> streamChunks() {
        Flux<Long> sequences = Flux.generate(
                () -> 0L,
                (state, sink) -> {
                    sink.next(state);
                    return state + 1;
                });

        if (!properties.isInfinite()) {
            sequences = sequences.take(properties.getChunkCount());
        }

        if (properties.getChunkIntervalMs() > 0) {
            sequences = sequences.delayElements(Duration.ofMillis(properties.getChunkIntervalMs()));
        }

        return sequences
                .map(this::toChunk)
                .doOnSubscribe(subscription -> log.info(
                        "sender stream started chunkCount={} intervalMs={} payloadSize={} infinite={}",
                        properties.getChunkCount(),
                        properties.getChunkIntervalMs(),
                        properties.getPayloadSize(),
                        properties.isInfinite()))
                .doOnNext(chunk -> log.debug("sender emitted {}", chunk))
                .doOnCancel(() -> log.info("sender stream cancelled"))
                .doOnComplete(() -> log.info("sender stream completed"));
    }

    private String toChunk(long sequence) {
        return "seq=" + sequence
                + ";createdAt=" + Instant.now()
                + ";payload=" + payload(sequence)
                + "\n";
    }

    private String payload(long sequence) {
        var base = "sample-data-" + sequence;
        if (base.length() >= properties.getPayloadSize()) {
            return base.substring(0, properties.getPayloadSize());
        }

        return base + "x".repeat(properties.getPayloadSize() - base.length());
    }
}
