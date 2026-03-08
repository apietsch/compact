# Spring Boot Projektplan: HTTP-Streaming mit Backpressure

## Ziel

Dieses Projekt besteht aus zwei getrennten Spring-Boot-Anwendungen:

- `sender-service`: streamt Textdaten per HTTP in Chunks
- `receiver-service`: konsumiert den Stream, verarbeitet die Chunks einzeln und demonstriert Backpressure unter Last

Die Umsetzung soll produktionsnah sein, aber weiterhin gezielt beobachtbares Verhalten fuer Demo, Test und Analyse bieten.

## Technische Leitentscheidungen

- Java `25`
- Spring Boot `3.x`
- Maven als Build-Tool
- Spring WebFlux fuer reaktives HTTP-Streaming
- Reines HTTP-Streaming mit `Transfer-Encoding: chunked`
- Text-Chunks, kein SSE, kein WebSocket, keine Persistenz
- Logging statt Metriken/Tracing-Stack
- Docker Compose fuer lokales Zusammenspiel beider Services

## Projektstruktur

Empfohlene Struktur als Multi-Module-Maven-Projekt:

```text
compact/
  pom.xml
  README.md
  docker-compose.yml
  sender-service/
    pom.xml
    src/main/java/...
    src/main/resources/application.yml
    src/test/java/...
  receiver-service/
    pom.xml
    src/main/java/...
    src/main/resources/application.yml
    src/test/java/...
```

## Fachlicher Ablauf

1. `receiver-service` startet einen Streaming-Request an `sender-service`.
2. `sender-service` liefert viele Text-Chunks fortlaufend ueber eine HTTP-Response.
3. `receiver-service` verarbeitet jeden Chunk einzeln.
4. Die Verarbeitung ist absichtlich konfigurierbar langsam.
5. Zusaetzlich werden verschiedene Backpressure-Strategien ausprobiert:
   - begrenzte Nachfrage
   - Buffering
   - Dropping
   - Latest-Wins
6. Das Verhalten wird ueber strukturierte Logs sichtbar gemacht.

## Sender-Service

### Verantwortung

Der `sender-service` produziert einen fortlaufenden Stream aus Text-Chunks.

### Endpunkte

- `GET /api/stream`
  - liefert `text/plain`
  - Response wird chunked uebertragen
  - jeder Chunk enthaelt:
    - laufende Sequenznummer
    - Erzeugungszeitpunkt
    - Payload-Text

- `GET /api/health`
  - einfacher Health-Endpunkt fuer lokale Checks und Docker

### Verhalten

Der Sender soll konfigurierbar sein:

- Gesamtzahl der Chunks
- Intervall zwischen Chunks
- Groesse des Payload-Texts
- optional unbegrenzter Stream fuer Dauertests

### Beispielinhalt eines Chunks

```text
seq=1042;createdAt=2026-03-08T12:00:00Z;payload=sample-data-1042
```

### Implementierungshinweise

- Rueckgabe als `Flux<String>`
- Media Type `text/plain`
- Jede Nachricht endet mit `\n`, damit der Client sauber pro Zeile verarbeiten kann
- Erzeugung z. B. ueber `Flux.interval(...)` oder einen kontrollierten Producer
- Logging auf Sender-Seite:
  - Stream gestartet
  - Chunk erzeugt
  - Stream beendet
  - Fehlerfall

### Produktionsnahe Aspekte

- Timeouts klar konfigurieren
- saubere Shutdown-Behandlung
- sinnvolle Log-Level
- Konfiguration ueber `application.yml` und Environment Variablen
- keine Business-Logik im Controller, sondern getrennte Service-Klasse

## Receiver-Service

### Verantwortung

Der `receiver-service` startet den HTTP-Streaming-Call, liest Chunks fortlaufend und simuliert einen langsamen, realistischen Consumer.

### Endpunkte

- `POST /api/ingestion/start`
  - startet den Konsum eines Streams
  - optional mit Parametern fuer Szenario und Ziel-URL

- `POST /api/ingestion/stop`
  - beendet einen laufenden Konsum kontrolliert

- `GET /api/ingestion/status`
  - zeigt, ob gerade konsumiert wird und welche Strategie aktiv ist

- `GET /api/health`
  - einfacher Health-Endpunkt

### HTTP-Client

- `WebClient`
- konsumiert `text/plain`
- Verarbeitung als `Flux<String>`
- Zerlegung pro Zeile bzw. pro Chunk

### Verarbeitung pro Chunk

Pro empfangenem Chunk:

- Eingang loggen
- Sequenznummer extrahieren
- Verzoegerung simulieren
- Verarbeitungsergebnis loggen

Es wird nichts persistiert. Die Verarbeitung bleibt absichtlich rein transient.

## Backpressure-Szenarien

Der `receiver-service` soll mehrere Modi unterstuetzen, damit Unterschiede klar sichtbar werden.

### 1. Normalmodus

- Empfang und Verarbeitung sind ungefaehr gleich schnell
- Erwartung: stabiler Durchsatz, kaum Rueckstau

### 2. Slow Consumer

- Verarbeitung ist deutlich langsamer als die Lieferfrequenz
- Erwartung: Buffer fuellen sich, Latenzen steigen

### 3. Buffering

- `onBackpressureBuffer(...)` mit begrenzter Kapazitaet
- Erwartung: zeitweilige Entkopplung, spaeter Buffer-Overflow

### 4. Drop

- `onBackpressureDrop(...)`
- Erwartung: einzelne Chunks gehen verloren, Durchsatz bleibt aber stabiler

### 5. Latest

- `onBackpressureLatest()`
- Erwartung: nur neueste Werte werden bevorzugt verarbeitet

### 6. Begrenzte Nachfrage

- `limitRate(...)`
- Erwartung: Nachfrage an Upstream wird kontrollierter, Verhalten wird berechenbarer

## Logging-Konzept

Es gibt bewusst nur Logging, daher muss es informativ und konsistent sein.

### Sender-Logs

- Stream-ID
- Ziel-Endpoint
- Chunk-Sequenz
- Zeitstempel
- gesendete Gesamtzahl
- Fehler und Abbruchgruende

### Receiver-Logs

- Stream-ID
- aktives Szenario
- empfangene Sequenz
- Start und Ende der Chunk-Verarbeitung
- Verarbeitungsdauer pro Chunk
- Anzahl gedroppter Elemente
- Buffer-Overflow oder Abbruch

### Format

Empfehlung:

- strukturiertes Log-Format mit klaren Keys
- keine uebermaessig langen Payload-Logs
- bei hoher Last ggf. nur jede n-te Nachricht auf INFO, Rest DEBUG

## Konfiguration

Beide Services sollen stark ueber Properties steuerbar sein.

### Sender-Konfiguration

- `server.port`
- `stream.chunk-count`
- `stream.chunk-interval-ms`
- `stream.payload-size`
- `stream.infinite`

### Receiver-Konfiguration

- `server.port`
- `sender.base-url`
- `ingestion.processing-delay-ms`
- `ingestion.limit-rate`
- `ingestion.buffer-size`
- `ingestion.strategy`
- `ingestion.request-timeout`

## Fehler- und Lastverhalten

Produktionsnah bedeutet hier, dass Fehlerfaelle bewusst behandelt werden:

- Sender nicht erreichbar
- Verbindungsabbruch mitten im Stream
- zu langsamer Consumer
- Buffer ausgeschopft
- Client-Stop waehrend laufendem Stream
- ungueltige Szenario-Parameter

Dafuer sollten klare Log-Meldungen und definierte Reaktionen umgesetzt werden:

- Retry nur wenn fachlich gewollt
- ansonsten fail-fast mit sauberem Log
- Streams sauber beenden statt haengen zu lassen

## Teststrategie

### Sender-Tests

- Controller-/Integrationstest fuer `GET /api/stream`
- pruefen, dass mehrere Chunks geliefert werden
- pruefen, dass das Format der Zeilen stimmt
- pruefen, dass Konfiguration uebernommen wird

### Receiver-Tests

- Test fuer WebClient-Streaming gegen Stub oder lokalen Testserver
- Test fuer Chunk-Verarbeitung mit kuenstlicher Verzoegerung
- Test fuer Buffer-, Drop- und Latest-Strategien
- Test fuer kontrollierten Start/Stop eines Streams

### Gemeinsame Integrationstests

- beide Services im Testkontext oder per Docker Compose
- End-to-End-Test:
  - Sender streamt
  - Receiver konsumiert
  - Logs/Zaehler bestaetigen das Verhalten

### Technische Testmittel

- JUnit 5
- Spring Boot Test
- Reactor Test mit `StepVerifier`
- optional OkHttp MockWebServer oder lokaler Spring-Testserver

## Docker Compose

`docker-compose.yml` soll beide Services lokal startbar machen.

### Ziele

- reproduzierbares Zusammenspiel
- einfache Demo fuer Slow Consumer und Backpressure
- konfigurierbare Parameter ueber Environment Variablen

### Compose-Services

- `sender-service`
  - exponiert z. B. Port `8081`
- `receiver-service`
  - exponiert z. B. Port `8082`
  - spricht intern mit `http://sender-service:8081`

### Compose-Anforderungen

- Build aus den lokalen Maven-Modulen
- Healthchecks
- Startreihenfolge ueber `depends_on` mit Health-Bedingung, sofern genutzt
- zentrale Environment-Konfiguration fuer Demoszenarien

## README-Inhalt

Die `README.md` sollte mindestens diese Punkte enthalten:

- Projektziel
- Architekturueberblick
- Voraussetzungen
- Build mit Maven
- Start lokal ohne Docker
- Start mit Docker Compose
- API-Endpunkte beider Services
- verfuegbare Backpressure-Szenarien
- typische Demoablaeufe
- Beispiel-Logs
- Testausfuehrung
- bekannte Grenzen von HTTP-Backpressure

## Umsetzungsreihenfolge

### Phase 1: Grundgeruest

- Maven Parent-Projekt anlegen
- Module `sender-service` und `receiver-service` erstellen
- gemeinsame Java- und Spring-Version festlegen

### Phase 2: Sender

- Streaming-Endpoint implementieren
- Chunk-Format festlegen
- Logging und Konfiguration ergaenzen
- Tests fuer Streaming-Verhalten schreiben

### Phase 3: Receiver

- `WebClient`-Anbindung implementieren
- Start/Stop/Status-Endpunkte bauen
- Chunk-Verarbeitung und Delay einbauen
- Basistests schreiben

### Phase 4: Backpressure-Modi

- `limitRate`
- `onBackpressureBuffer`
- `onBackpressureDrop`
- `onBackpressureLatest`
- Logging fuer Unterschiede schaerfen

### Phase 5: Containerisierung

- Dockerfiles erstellen
- Docker Compose aufsetzen
- lokale End-to-End-Ausfuehrung pruefen

### Phase 6: Dokumentation und Härtung

- README vervollstaendigen
- Konfiguration aufraeumen
- Fehlerfaelle pruefen
- Logs fuer Demo und Betrieb lesbar machen

## Produktionsnahe Grenzen und Realitaet

Wichtig fuer das Design: HTTP-Backpressure ist nicht deckungsgleich mit rein reaktivem In-Memory-Backpressure. Dazwischen liegen:

- TCP-Puffer
- HTTP-Buffering
- Netty-Interna
- Betriebssystem-Sockets

Deshalb soll das Projekt nicht behaupten, dass jedes verlangsamte `request(n)` direkt 1:1 bis zum Produzenten durchschlaegt. Stattdessen soll es realistisch demonstrieren:

- dass langsame Konsumenten Probleme verursachen
- dass Strategien unterschiedliche Konsequenzen haben
- dass Logging diese Unterschiede sichtbar macht

## Lieferumfang

Am Ende soll das Projekt folgende Artefakte enthalten:

- Multi-Module-Maven-Projekt
- zwei getrennte Spring-Boot-Apps
- Streaming zwischen beiden Services
- mehrere Backpressure-Szenarien
- automatisierte Tests
- Docker Compose Setup
- nachvollziehbare README

## Empfohlene Defaultwerte

### Sender

- Port `8081`
- `chunk-count=10000`
- `chunk-interval-ms=10`
- `payload-size=64`

### Receiver

- Port `8082`
- `processing-delay-ms=200`
- `limit-rate=32`
- `buffer-size=128`
- `strategy=buffer`

## Nächster Umsetzungsschritt

Nach diesem Plan waere der naechste konkrete Schritt die Umstellung des bestehenden Repositories auf ein Multi-Module-Maven-Projekt mit `sender-service` und `receiver-service`.
