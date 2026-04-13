# AGENTS.md

## Quick Context

HTTP streaming backpressure demo with two Spring Boot WebFlux apps:
- `sender-service`: streams text chunks via `GET /api/stream`
- `receiver-service`: consumes stream and applies strategies (`NORMAL`, `SLOW`, `BUFFER`, `DROP`, `LATEST`)

## Fast Start

```bash
mvn clean verify
mvn -pl sender-service spring-boot:run
mvn -pl receiver-service spring-boot:run
```

Start order:
1. Sender (`8081`)
2. Receiver (`8082`)
3. Trigger ingestion:

```bash
curl -X POST http://localhost:8082/api/ingestion/start \
  -H "Content-Type: application/json" \
  -d '{"strategy":"BUFFER"}'
```

## Important Defaults

Sender defaults:
- `stream.chunk-interval-ms=10`
- `stream.chunk-count=10000`

Receiver defaults:
- `ingestion.processing-delay-ms=200`
- `ingestion.buffer-size=128`
- `ingestion.limit-rate=32`
- `ingestion.strategy=BUFFER`

## Behavior to Expect

With default settings, sender is faster than receiver:
- backlog grows
- buffer overflow warnings appear
- receiver can terminate with `OverflowException`
- sender then logs stream cancellation

This is expected for stress/backpressure demos.

## Java and Build Notes

- Runtime target: Java 25
- Maven currently compiles to Java 21 bytecode for framework compatibility in this setup

## Useful Files

- Root build: `pom.xml`
- Service configs:
  - `sender-service/src/main/resources/application.yml`
  - `receiver-service/src/main/resources/application.yml`
- Core receiver logic:
  - `receiver-service/src/main/java/dev/compact/receiver/service/IngestionService.java`
- Core sender logic:
  - `sender-service/src/main/java/dev/compact/sender/service/StreamService.java`

## Diagrams

- Source: `C4_CONTEXT.mmd`, `C4_CONTAINER.mmd`
- Render script: `render-mermaid.sh`
- Example:

```bash
./render-mermaid.sh C4_CONTEXT.mmd svg
./render-mermaid.sh C4_CONTAINER.mmd jpg
```
