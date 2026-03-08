package dev.compact.receiver.web;

import dev.compact.receiver.service.IngestionService;
import dev.compact.receiver.service.IngestionStatus;
import dev.compact.receiver.service.IngestionStrategy;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingestion/start")
    public IngestionStatus start(@RequestBody(required = false) StartIngestionRequest request) {
        IngestionStrategy strategy = request == null ? null : request.strategy();
        return ingestionService.start(strategy);
    }

    @PostMapping("/ingestion/stop")
    public IngestionStatus stop() {
        return ingestionService.stop();
    }

    @GetMapping("/ingestion/status")
    public IngestionStatus status() {
        return ingestionService.status();
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "receiver-service");
    }
}
