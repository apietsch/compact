package dev.compact.receiver.config;

import dev.compact.receiver.service.IngestionStrategy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ingestion")
public class IngestionProperties {

    private long processingDelayMs = 200;
    private int limitRate = 32;
    private int bufferSize = 128;
    private IngestionStrategy strategy = IngestionStrategy.BUFFER;
    private Duration requestTimeout = Duration.ofSeconds(30);

    public long getProcessingDelayMs() {
        return processingDelayMs;
    }

    public void setProcessingDelayMs(long processingDelayMs) {
        this.processingDelayMs = processingDelayMs;
    }

    public int getLimitRate() {
        return limitRate;
    }

    public void setLimitRate(int limitRate) {
        this.limitRate = limitRate;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public void setBufferSize(int bufferSize) {
        this.bufferSize = bufferSize;
    }

    public IngestionStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(IngestionStrategy strategy) {
        this.strategy = strategy;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
