# HTTP Streaming Backpressure Demo

Dieses Repository ist als Multi-Module-Maven-Projekt fuer zwei getrennte Spring-Boot-Anwendungen vorbereitet:

- `sender-service`: liefert Text-Chunks per HTTP-Streaming
- `receiver-service`: konsumiert den Stream und demonstriert verschiedene Backpressure-Strategien

Die Container und die lokale Laufzeit sind auf Java `25` ausgelegt. Der Maven-Compiler erzeugt aktuell Java-`21`-Bytecode, weil der verwendete Spring-Stack Java-`25`-Classfiles beim Classpath-Scanning noch nicht sauber verarbeitet.

## Module

- `sender-service`
- `receiver-service`

## Build

```bash
mvn clean verify
```

## Lokaler Start

Sender:

```bash
mvn -pl sender-service spring-boot:run
```

Receiver:

```bash
mvn -pl receiver-service spring-boot:run
```

## Docker Compose

```bash
docker compose up --build
```

## Nächste Schritte

Die Projektstruktur und ein erstes lauffaehiges Grundgeruest sind vorhanden. Fachliche Feinheiten und weitere Tests koennen darauf aufbauen.
