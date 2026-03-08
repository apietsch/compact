package dev.compact.receiver.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.compact.receiver.service.IngestionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        classes = dev.compact.receiver.ReceiverServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@TestPropertySource(properties = "logging.level.dev.compact.receiver=OFF")
class IngestionControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldExposeIdleStatus() {
        IngestionStatus status = webTestClient.get()
                .uri("/api/ingestion/status")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(IngestionStatus.class)
                .returnResult()
                .getResponseBody();

        assertThat(status).isNotNull();
        assertThat(status.running()).isFalse();
        assertThat(status.sourceUrl()).contains("/api/stream");
    }

    @Test
    void shouldExposeHealthEndpoint() {
        webTestClient.get()
                .uri("/api/health")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("UP");
    }
}
