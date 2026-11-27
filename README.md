# CNI — Scriptopslag PoC (Docker, Java 21, TCP)

Deze **onderzoeks-PoC** onderzoekt hoe scripts van Agents en NPC's over peers kunnen worden opgeslagen en verdeeld zonder centrale spelserver. We vergelijken vier methoden:

1. **Full Replication** — iedere peer bewaart een volledige kopie.
2. **Data Fragmentation (zonder redundancy)** — een script wordt verdeeld in losse delen, zonder reservekopieën.
3. **Partial Replication** — een vaste selectie peers bewaart een volledige kopie (replication factor 2).
4. **Erasure Coding (Reed-Solomon 3+2)** — drie datashards en twee herstelshards worden over vijf peers verdeeld.

De PoC gebruikt Java 21, TCP en **in-memory opslag**. De peers communiceren binnen één lokaal Docker-netwerk. Dit is een onderzoeksprototype, geen productieklare gamecomponent.

## Benodigdheden en starten

- Docker Desktop met Docker Compose.
- Een terminal in de projectroot (de map met `compose.yaml`).
- Java 21 en Maven zijn alleen lokaal nodig wanneer je buiten Docker de JUnit-tests wilt draaien. De Dockerfile bouwt de applicatie zelf met Maven.

```powershell
docker compose down
docker compose up -d --build
docker compose ps
```

Verwacht: **vijf draaiende containers**, `peer-a` t/m `peer-e`.

Alle voorbeelden gebruiken de CLI in een Docker-container. De PoC communiceert intern via TCP op poort 7000.

**Verbinding testen:**

```powershell
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient ping
```

Verwacht: `PONG|peer-a`.

## De vier opslagmethoden testen

> Voer de scenario's bij voorkeur in volgorde uit. Start gestopte peers opnieuw voordat je naar een andere methode gaat. **Een herstart wist de in-memory gegevens van die peer**; sla scripts dus opnieuw op als je een herstelscenario wilt herhalen.

### 1. Full Replication

Een `PUT` naar `peer-a` slaat het script lokaal op en verstuurt kopieën naar de ingestelde peers `peer-b` en `peer-c`. Een `GET` leest alleen van de aangesproken peer.

```powershell
# Opslaan en controleren
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient put regression-full 1 MOVE_FORWARD
docker compose exec -T peer-b java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient get regression-full

# Oorspronkelijke peer valt weg
docker compose stop peer-a
docker compose exec -T peer-c java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient get regression-full

# Herstellen voor de volgende test
docker compose start peer-a
```

Verwacht: `OK|stored-locally|replicated-to=2/2` bij opslaan. Zowel voor als na uitval van `peer-a` kan `MOVE_FORWARD` via een andere peer worden opgehaald.

### 2. Data Fragmentation zonder redundancy

Het script wordt verdeeld over `peer-a`, `peer-b` en `peer-c`. Voor reconstructie zijn **alle drie de fragmenten** nodig.

```powershell
# Verdelen en reconstrueren
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient fsplit regression-frag 1 MOVE_FORWARD
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient fjoin regression-frag 1

# Eén fragment wordt onbereikbaar
docker compose stop peer-b
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient fjoin regression-frag 1

# Herstellen voor de volgende test
docker compose start peer-b
```

Verwacht: aanvankelijk `Hersteld script: MOVE_FORWARD`. Bij uitval van `peer-b` mislukt reconstructie: er is geen redundancy. Na herstart van `peer-b` is het oude fragment verdwenen.

### 3. Partial Replication (replication factor 2)

De code kiest op basis van de scriptnaam steeds dezelfde twee peers uit `peer-a`, `peer-b` en `peer-c`. Voor `partial-test` zijn dat **`peer-c` en `peer-a`**. De volgorde is niet willekeurig.

```powershell
# Twee volledige kopieën opslaan en het script ophalen
docker compose exec -T peer-b java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient pput partial-test 1 MOVE_FORWARD
docker compose exec -T peer-b java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient pget partial-test

# De eerste replica valt weg; de tweede is nog beschikbaar
docker compose stop peer-c
docker compose exec -T peer-b java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient pget partial-test

# Beide replica's vallen weg
docker compose stop peer-a
docker compose exec -T peer-b java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient pget partial-test

# Herstellen voor de volgende test
docker compose start peer-a peer-c
```

Verwacht: eerst `Kopieën opgeslagen: 2/2` en `MOVE_FORWARD` van `peer-c`. Na uitval van `peer-c` wordt het script van `peer-a` opgehaald. Als beide replica's uitvallen, verschijnt `Script niet beschikbaar.`

### 4. Erasure Coding (Reed-Solomon 3+2)

De PoC maakt **vijf shards** voor `peer-a` t/m `peer-e`. Uit minimaal drie verschillende shards kan het originele script worden hersteld.

```powershell
# Vijf shards opslaan en script herstellen
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient eput regression-erasure 1 MOVE_FORWARD
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient eget regression-erasure 1

# Twee peers vallen weg: herstel moet nog lukken
docker compose stop peer-b peer-d
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient eget regression-erasure 1

# Drie peers vallen weg: herstel moet mislukken
docker compose stop peer-c
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient eget regression-erasure 1

# Containers weer starten
docker compose start peer-b peer-c peer-d
```

Verwacht: met vijf of drie beschikbare shards wordt `MOVE_FORWARD` hersteld; met slechts twee shards verschijnt `Reconstructie mislukt`.

## JUnit-tests

De unit tests voor `DataFragmentation` en `ErasureCoding` controleren onder andere correcte reconstructie, ontbrekende delen, ongeldige invoer en herstel bij verschillende ontbrekende shards.

```powershell
mvn clean test
```

Verwacht: `BUILD SUCCESS` en **8 geslaagde JUnit-tests**. Dit vereist lokaal Java 21 en Maven. De Docker-regressietests hierboven testen daarnaast de netwerkcommunicatie en het gedrag bij peeruitval.

## Metingen

De PoC is gebruikt voor een verkennende vergelijking van **opslaggebruik**, **uitvoeringstijd** (CLI-commando `time`) en **Docker-netwerkverkeer** (`docker stats`). De meetmethode, resultaten en kanttekeningen staan in [`docs/testplan.md`](docs/testplan.md) en [`docs/testresultaten.md`](docs/testresultaten.md).

Voorbeeld van een tijdmeting:

```powershell
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient time eput meting-erasure 1 MOVE_FORWARD
```

De gemeten tijd omvat ook de CLI- en TCP-verwerking. Alle containers draaien op één computer; dit zijn **geen** metingen op een fysiek netwerk met afzonderlijke apparaten. Het Docker-netwerkverkeer is indicatief en bevat mogelijk achtergrondverkeer.

## Relatie met het CNI-project

- **Scriptopslag:** de PoC bewaart scriptinhoud als bytes. Het is geen AST-parser of scriptinterpreter en test niet hoe of waar scripts worden uitgevoerd.
- **ADR-17 (berichtformaat):** de PoC gebruikt tekstberichten met Base64, **geen Protocol Buffers**.
- **ADR-20 (network security):** er is **geen ChaCha20-Poly1305-encryptie** geïmplementeerd; metingen bevatten dus geen encryptie-overhead.
- **ADR-11 (peer discovery):** de peers hebben vaste Docker-DNS-namen; er is **geen automatische peerdiscovery**.
- **ADR-10 (source of truth):** deze PoC neemt geen beslissing over game-state-authoriteit of de command-log.

De status van deze ADR's kan gedurende het project veranderen. De PoC onderzoekt uitsluitend de **distributie en beschikbaarheid van scriptgegevens**.

## Beperkingen

- **Geen persistente opslag:** stoppen en herstarten van een container verwijdert de gegevens in die container. Er is geen automatische resynchronisatie.
- **Vaste peerlijsten:** drie peers voor Full/Data/Partial en vijf voor Erasure Coding; geen peerdiscovery, host election of dynamische verdeling.
- **Eenvoudig netwerkprotocol:** TCP-tekstregels met Base64, zonder encryptie of authenticatie.
- **Full Replication is best effort:** `PUT` verspreidt synchroon; bij onbereikbare peers kunnen kopieën verschillen. Een `GET` zoekt niet automatisch op andere peers.
- **Vereenvoudigd versiebeheer:** oudere versies worden geweigerd, maar gelijktijdige conflicterende updates zijn niet volledig opgelost.
- **Erasure Coding is een eigen PoC-implementatie:** de Reed-Solomon-logica is geen externe productie-library en is niet bedoeld als productieklare codec.
- **Beperkte schaal en meetomgeving:** geen representatieve LAN-benchmark, geen grootschalige peerbelasting en geen bewijs voor volledige game-QAR's zoals automatische Agent-takeover.

## Projectbestanden

| Bestand | Functie |
|---|---|
| `Dockerfile` | Multi-stage Maven-build en Java-runtime |
| `compose.yaml` | Vijf peers in één Docker-netwerk |
| `src/main/java/nl/han/cni/scriptopslag/` | Opslagmethoden, TCP-server, CLI en in-memory opslag |
| `src/test/java/nl/han/cni/scriptopslag/` | JUnit-tests voor fragmentatie en erasure coding |
| `docs/testplan.md` | Testscenario's, meetmethode en afbakening |
| `docs/testresultaten.md` | Waarnemingen, metingen en beperkingen |

## Opruimen

```powershell
docker compose down
```
