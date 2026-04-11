package dev.compact.receiver.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.compact.receiver.config.IngestionProperties;
import dev.compact.receiver.config.SenderProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class IngestionServiceTest {

    private static final DefaultDataBufferFactory BUFFER_FACTORY = new DefaultDataBufferFactory();

    @Test
    void shouldProcessStreamToCompletion() {
        IngestionService service = new IngestionService(
                webClientBuilder(Flux.just(chunk(0), chunk(1), chunk(2))),
                properties(5, 32, 16),
                senderProperties());

        IngestionStatus started = service.start(IngestionStrategy.NORMAL);
        assertThat(started.running()).isTrue();

        IngestionStatus completed = awaitStatus(service, () -> !service.status().running(), Duration.ofSeconds(5));

        assertThat(completed.running()).isFalse();
        assertThat(completed.strategy()).isEqualTo(IngestionStrategy.NORMAL);
        assertThat(completed.processedChunks()).isEqualTo(3);
        assertThat(completed.droppedChunks()).isZero();
    }

    @Test
    void shouldStopRunningIngestion() {
        IngestionService service = new IngestionService(
                webClientBuilder(Flux.interval(Duration.ofMillis(1)).map(IngestionServiceTest::chunk)),
                properties(25, 32, 16),
                senderProperties());

        service.start(IngestionStrategy.SLOW);
        awaitStatus(service, () -> service.status().processedChunks() > 0, Duration.ofSeconds(5));
        IngestionStatus stopped = service.stop();

        assertThat(stopped.running()).isFalse();
        assertThat(stopped.processedChunks()).isGreaterThan(0);
    }

    @Test
    void shouldReportDroppedChunksForLatestStrategy() {
        IngestionService service = new IngestionService(
                webClientBuilder(Flux.range(0, 200).map(index -> chunk(index.longValue()))),
                properties(10, 1, 16),
                senderProperties());

        service.start(IngestionStrategy.LATEST);
        IngestionStatus completed = awaitStatus(service, () -> !service.status().running(), Duration.ofSeconds(5));

        assertThat(completed.running()).isFalse();
        assertThat(completed.strategy()).isEqualTo(IngestionStrategy.LATEST);
        assertThat(completed.processedChunks()).isGreaterThan(0);
        assertThat(completed.processedChunks()).isLessThan(200);
        assertThat(completed.droppedChunks()).isGreaterThan(0);
    }

    private WebClient.Builder webClientBuilder(Flux<String> body) {
        ExchangeFunction exchangeFunction = request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.TEXT_PLAIN_VALUE)
                .body(body.map(this::toDataBuffer))
                .build());
        return WebClient.builder().exchangeFunction(exchangeFunction);
    }

    private DataBuffer toDataBuffer(String chunk) {
        return BUFFER_FACTORY.wrap(chunk.getBytes(StandardCharsets.UTF_8));
    }

    private IngestionProperties properties(long delayMs, int limitRate, int bufferSize) {
        IngestionProperties properties = new IngestionProperties();
        properties.setProcessingDelayMs(delayMs);
        properties.setLimitRate(limitRate);
        properties.setBufferSize(bufferSize);
        properties.setRequestTimeout(Duration.ofSeconds(5));
        return properties;
    }

    private SenderProperties senderProperties() {
        SenderProperties senderProperties = new SenderProperties();
        senderProperties.setBaseUrl("http://sender.test");
        return senderProperties;
    }

    private IngestionStatus awaitStatus(
            IngestionService service, BooleanSupplier condition, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            IngestionStatus status = service.status();
            if (condition.getAsBoolean()) {
                return status;
            }
            sleep(Duration.ofMillis(25));
        }
        throw new AssertionError("Timed out waiting for ingestion status");
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for ingestion status", exception);
        }
    }

    private static String chunk(long sequence) {
        return "seq=" + sequence + ";payload=test\n";
    }
}
