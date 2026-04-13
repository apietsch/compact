package dev.compact.receiver.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.compact.receiver.ReceiverServiceApplication;
import dev.compact.receiver.service.IngestionStatus;
import dev.compact.receiver.service.IngestionStrategy;
import dev.compact.sender.SenderServiceApplication;
import java.time.Duration;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class IngestionIntegrationTest {

    private ConfigurableApplicationContext senderContext;
    private ConfigurableApplicationContext receiverContext;

    @AfterEach
    void tearDown() {
        if (receiverContext != null) {
            receiverContext.close();
        }
        if (senderContext != null) {
            senderContext.close();
        }
    }

    @Test
    void shouldIngestChunksFromRunningSenderService() {
        senderContext = new SpringApplicationBuilder(SenderServiceApplication.class)
                .run(
                        "--server.port=0",
                        "--stream.chunk-count=25",
                        "--stream.chunk-interval-ms=1",
                        "--stream.payload-size=24",
                        "--stream.infinite=false",
                        "--logging.level.dev.compact.sender=OFF");

        int senderPort = portOf(senderContext);

        receiverContext = new SpringApplicationBuilder(ReceiverServiceApplication.class)
                .run(
                        "--server.port=0",
                        "--sender.base-url=http://127.0.0.1:" + senderPort,
                        "--ingestion.processing-delay-ms=5",
                        "--ingestion.limit-rate=8",
                        "--ingestion.buffer-size=16",
                        "--ingestion.request-timeout=PT5S",
                        "--logging.level.dev.compact.receiver=OFF");

        WebTestClient client = WebTestClient.bindToServer()
                .baseUrl("http://127.0.0.1:" + portOf(receiverContext))
                .responseTimeout(Duration.ofSeconds(5))
                .build();

        IngestionStatus started = client.post()
                .uri("/api/ingestion/start")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("strategy", IngestionStrategy.NORMAL.name()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(IngestionStatus.class)
                .returnResult()
                .getResponseBody();

        assertThat(started).isNotNull();
        assertThat(started.running()).isTrue();
        assertThat(started.strategy()).isEqualTo(IngestionStrategy.NORMAL);

        IngestionStatus completed = awaitStatus(client, () -> {
            IngestionStatus status = fetchStatus(client);
            return status != null && !status.running();
        }, Duration.ofSeconds(10));

        assertThat(completed.processedChunks()).isEqualTo(25);
        assertThat(completed.droppedChunks()).isZero();
        assertThat(completed.sourceUrl()).isEqualTo("http://127.0.0.1:" + senderPort + "/api/stream");
    }

    private IngestionStatus awaitStatus(
            WebTestClient client, BooleanSupplier completed, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            IngestionStatus status = fetchStatus(client);
            if (completed.getAsBoolean()) {
                return status;
            }
            sleep(Duration.ofMillis(50));
        }
        throw new AssertionError("Timed out waiting for ingestion completion");
    }

    private IngestionStatus fetchStatus(WebTestClient client) {
        return client.get()
                .uri("/api/ingestion/status")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(IngestionStatus.class)
                .returnResult()
                .getResponseBody();
    }

    private int portOf(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for ingestion completion", exception);
        }
    }
}
