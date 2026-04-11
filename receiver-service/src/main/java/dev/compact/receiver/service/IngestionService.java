package dev.compact.receiver.service;

import dev.compact.receiver.config.IngestionProperties;
import dev.compact.receiver.config.SenderProperties;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final WebClient webClient;
    private final IngestionProperties ingestionProperties;
    private final SenderProperties senderProperties;
    private final AtomicReference<Disposable.Swap> runningSubscription = new AtomicReference<>();
    private final AtomicReference<IngestionStatus> status = new AtomicReference<>();

    public IngestionService(
            WebClient.Builder webClientBuilder,
            IngestionProperties ingestionProperties,
            SenderProperties senderProperties) {
        this.webClient = webClientBuilder.baseUrl(senderProperties.getBaseUrl()).build();
        this.ingestionProperties = ingestionProperties;
        this.senderProperties = senderProperties;
        this.status.set(IngestionStatus.idle(ingestionProperties.getStrategy(), streamUrl()));
    }

    public synchronized IngestionStatus start(IngestionStrategy requestedStrategy) {
        if (runningSubscription.get() != null) {
            return status.get();
        }

        var strategy = requestedStrategy == null ? ingestionProperties.getStrategy() : requestedStrategy;
        var streamId = UUID.randomUUID().toString();
        var processedCounter = new AtomicLong();
        var droppedCounter = new AtomicLong();

        Flux<String> source = webClient.get()
                .uri("/api/stream")
                .accept(MediaType.TEXT_PLAIN)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(ingestionProperties.getRequestTimeout())
                .limitRate(ingestionProperties.getLimitRate());

        Flux<String> configured = applyStrategy(source, strategy, droppedCounter)
                .concatMap(chunk -> processChunk(streamId, chunk, strategy, processedCounter));

        Disposable.Swap subscriptionSlot = Disposables.swap();
        runningSubscription.set(subscriptionSlot);
        status.set(new IngestionStatus(true, streamId, strategy, 0, 0, streamUrl()));
        Disposable subscription = configured.subscribe(
                chunk -> status.set(new IngestionStatus(
                        true,
                        streamId,
                        strategy,
                        processedCounter.get(),
                        droppedCounter.get(),
                        streamUrl())),
                error -> {
                    log.error("receiver streamId={} failed", streamId, error);
                    status.set(new IngestionStatus(
                            false,
                            streamId,
                            strategy,
                            processedCounter.get(),
                            droppedCounter.get(),
                            streamUrl()));
                    clearRunningSubscription(subscriptionSlot);
                },
                () -> {
                    log.info("receiver streamId={} completed processed={} dropped={}",
                            streamId,
                            processedCounter.get(),
                            droppedCounter.get());
                    status.set(new IngestionStatus(
                            false,
                            streamId,
                            strategy,
                            processedCounter.get(),
                            droppedCounter.get(),
                            streamUrl()));
                    clearRunningSubscription(subscriptionSlot);
                });

        subscriptionSlot.update(subscription);
        log.info("receiver streamId={} started strategy={} sourceUrl={}", streamId, strategy, streamUrl());
        return status.get();
    }

    public synchronized IngestionStatus stop() {
        Disposable.Swap subscription = runningSubscription.getAndSet(null);
        if (subscription != null) {
            subscription.dispose();
        }

        IngestionStatus current = status.get();
        IngestionStatus updated = new IngestionStatus(
                false,
                current.streamId(),
                current.strategy(),
                current.processedChunks(),
                current.droppedChunks(),
                current.sourceUrl());
        status.set(updated);
        return updated;
    }

    public IngestionStatus status() {
        return status.get();
    }

    private Flux<String> applyStrategy(Flux<String> source, IngestionStrategy strategy, AtomicLong droppedCounter) {
        return switch (strategy) {
            case NORMAL -> source;
            case SLOW -> source;
            case BUFFER -> source.onBackpressureBuffer(
                    ingestionProperties.getBufferSize(),
                    chunk -> {
                        droppedCounter.incrementAndGet();
                        log.warn("receiver buffer overflow dropped chunk={}", preview(chunk));
                    });
            case DROP -> source.onBackpressureDrop(chunk -> {
                droppedCounter.incrementAndGet();
                log.warn("receiver dropped chunk={}", preview(chunk));
            });
            case LATEST -> applyLatestStrategy(source, droppedCounter);
        };
    }

    private Flux<String> processChunk(
            String streamId,
            String chunk,
            IngestionStrategy strategy,
            AtomicLong processedCounter) {
        Duration delay = switch (strategy) {
            case NORMAL -> Duration.ZERO;
            case SLOW, BUFFER, DROP, LATEST -> Duration.ofMillis(ingestionProperties.getProcessingDelayMs());
        };

        return Flux.just(chunk)
                .delayElements(delay)
                .doOnNext(value -> {
                    long processed = processedCounter.incrementAndGet();
                    log.info("receiver streamId={} strategy={} processed={} chunk={}",
                            streamId,
                            strategy,
                            processed,
                            preview(value));
                });
    }

    private String streamUrl() {
        return senderProperties.getBaseUrl() + "/api/stream";
    }

    private void clearRunningSubscription(Disposable.Swap subscriptionSlot) {
        runningSubscription.compareAndSet(subscriptionSlot, null);
        subscriptionSlot.dispose();
    }

    private Flux<String> applyLatestStrategy(Flux<String> source, AtomicLong droppedCounter) {
        AtomicLong lastDeliveredIndex = new AtomicLong(-1);
        return source.index()
                .onBackpressureLatest()
                .doOnNext(indexedChunk -> {
                    long previousIndex = lastDeliveredIndex.getAndSet(indexedChunk.getT1());
                    long dropped = indexedChunk.getT1() - previousIndex - 1;
                    if (dropped > 0) {
                        droppedCounter.addAndGet(dropped);
                        log.warn("receiver latest dropped={} chunk={}", dropped, preview(indexedChunk.getT2()));
                    }
                })
                .map(indexedChunk -> indexedChunk.getT2());
    }

    private String preview(String chunk) {
        String compact = chunk.replace('\n', ' ').trim();
        return compact.length() <= 120 ? compact : compact.substring(0, 120);
    }
}
