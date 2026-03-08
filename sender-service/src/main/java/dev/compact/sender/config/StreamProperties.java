package dev.compact.sender.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stream")
public class StreamProperties {

    private long chunkCount = 10_000;
    private long chunkIntervalMs = 10;
    private int payloadSize = 64;
    private boolean infinite;

    public long getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(long chunkCount) {
        this.chunkCount = chunkCount;
    }

    public long getChunkIntervalMs() {
        return chunkIntervalMs;
    }

    public void setChunkIntervalMs(long chunkIntervalMs) {
        this.chunkIntervalMs = chunkIntervalMs;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public void setPayloadSize(int payloadSize) {
        this.payloadSize = payloadSize;
    }

    public boolean isInfinite() {
        return infinite;
    }

    public void setInfinite(boolean infinite) {
        this.infinite = infinite;
    }
}
