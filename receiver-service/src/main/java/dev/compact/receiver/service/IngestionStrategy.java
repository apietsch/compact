package dev.compact.receiver.service;

public enum IngestionStrategy {
    NORMAL,
    SLOW,
    BUFFER,
    DROP,
    LATEST
}
