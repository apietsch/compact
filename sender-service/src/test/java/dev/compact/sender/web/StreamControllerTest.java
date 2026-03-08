package dev.compact.sender.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        classes = dev.compact.sender.SenderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "stream.chunk-count=3",
        "stream.chunk-interval-ms=1",
        "stream.payload-size=20",
        "logging.level.dev.compact.sender=OFF"
})
class StreamControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void shouldStreamConfiguredChunks() {
        FluxExchangeResult<String> result = webTestClient.get()
                .uri("/api/stream")
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(String.class);

        List<String> chunks = result.getResponseBody().take(3).collectList().block(Duration.ofSeconds(5));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.getFirst()).contains("seq=0");
        assertThat(chunks.getLast()).contains("seq=2");
    }
}
