package dev.compact.receiver.web;

import dev.compact.receiver.service.IngestionStrategy;

public record StartIngestionRequest(IngestionStrategy strategy) {
}
