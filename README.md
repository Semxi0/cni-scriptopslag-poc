# CNI — Scriptopslag PoC (Docker, Java 21, TCP)

Een **onderzoeksprototype** voor de vraag hoe Agent- en NPC-scriptgegevens verdeeld kunnen worden over peers zonder centrale spelserver. Deze repository vergelijkt uiteindelijk vier methoden: Full Replication, Data Fragmentation zonder Redundancy, Partial Replication en Erasure Coding (Reed-Solomon 3+2).

## Huidige status: starter — alleen Full Replication

**Al geïmplementeerd:** 3 aparte Java-processen in Docker-containers, TCP-verkeer tussen de peers, Full Replication, in-memory opslag van willekeurige bytes, scriptversies, eenvoudige controle bij peeruitval. Een CLI gebruikt leesbare tekst als voorbeeldpayload; de opslaglaag is onafhankelijk van het formaat.

**Nog niet geïmplementeerd:** de overige drie methoden, Protocol Buffers, AST-parser/interpreter, ChaCha20-Poly1305, automatische herstelacties, opslag op schijf, systematische benchmarks, peerdiscovery, host election. Geen productiecode.

## Eerst uitvoeren (Windows 11 / PowerShell)

Vereist: Docker Desktop **gestart**, `docker compose version` beschikbaar. Open een terminal in de **projectroot** bij `compose.yaml`.

```powershell
docker compose up -d --build
docker compose ps
```

Verwacht: `peer-a`, `peer-b`, `peer-c` zijn *Up*. Docker bouwt zelf met Maven; lokaal `mvn compile` is **niet nodig** om Docker te starten.

### Controle 1: TCP-verbinding

```powershell
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient ping
```

Verwacht: `PONG|peer-a`.

### Controle 2: distribueren en ophalen

```powershell
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient put agent-default 1 MOVE_FORWARD
docker compose exec -T peer-b java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient get agent-default
```

Verwacht bij het opslaan: `OK|stored-locally|replicated-to=2/2` (als peers bereikbaar zijn). Bij ophalen op B: `version=1`, `content=MOVE_FORWARD`.

### Controle 3: Peer Failure

```powershell
docker compose stop peer-a
docker compose exec -T peer-c java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient get agent-default
```

Verwacht: `version=1`, `content=MOVE_FORWARD`. Peer C heeft al een kopie.

### Controle 4: simpele scriptupdate

```powershell
docker compose start peer-a
docker compose exec -T peer-b java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient put agent-default 2 DEFEND
docker compose exec -T peer-c java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient get agent-default
```

Verwacht op C: `version=2`, `content=DEFEND`. **Let op:** peer-a was gestopt, dus de oude in-memory inhoud van A is verdwenen; de update wordt opnieuw naar A gekopieerd als die weer bereikbaar is.

Optioneel: `docker compose exec -T peer-c java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient stats` geeft het aantal scripts en het aantal opgeslagen **payloadbytes**, niet het totale geheugengebruik.

**Opruimen:**

```powershell
docker compose down
```

## Maven: wanneer wel en niet?

- **Docker-route (aanbevolen voor de netwerk-PoC):** alleen `docker compose up -d --build` is nodig. De multi-stage `Dockerfile` voert `mvn package` in een buildcontainer uit.
- **Lokaal ontwikkelen in IntelliJ:** optioneel `mvn compile` of `mvn test` in de rootfolder waar `pom.xml` staat. Er zijn nog geen JUnit-tests aanwezig: `mvn test` voert op dit moment geen relevante tests uit.
- Na Java-codewijzigingen: `docker compose up -d --build` opnieuw draaien; alleen `mvn compile` op je pc verandert **niet** de draaiende containers.

## Relatie met de projectdocumenten (25 november 2026)

| Bron | Status | Afspraak / begrenzing voor deze PoC |
|---|---|---|
| Onderzoek scriptopslag | literatuurfase | Vier methoden vergelijken; deze starter demonstreert alleen Full Replication |
| Onderzoek *Hoe wordt het script opgeslagen?* | onderzoeksconclusie | `.script` als lokale brontekst; voorstel om AST te versturen; deze PoC slaat **opaque bytes** op (geen AST-implementatie) |
| ADR-17 berichtformaat | **Draft** | Protocol Buffers voorgesteld; nog niet geïmplementeerd; geen conclusies over daadwerkelijke Protocol Buffers-overhead |
| ADR-20 Network Security | **Accepted** | ChaCha20-Poly1305 voor game-communicatie; nog niet geïmplementeerd; timingresultaten zijn dus exclusief encryptie |
| ADR-11 Peer Discovery | **Accepted** | UDP broadcast binnen LAN; in Docker gebruikt de PoC vaste DNS-namen (`peer-a` etc.), dus **geen** discoverytest |
| ADR-10 Source of Truth | **Draft** | Voorkeur host-authoritative met event log voor game-state; deze PoC beslist **niet** over game-state authority |

De opslag van scriptbytes is **niet hetzelfde** als het repliceren van de command-log van de game. TCP is hier alleen een transportkeuze voor de opslagtest. Het berichtformaat van de starter is een simpele tekstregel met Base64; dat telt niet als Protocol Buffers of encryptie. De PoC gebruikt een vast ingestelde peer-lijst; deze vormt geen centrale game-server.

## Scope en beperkingen

- Docker-bridge op **één laptop** biedt echte communicatie tussen aparte processen maar niet dezelfde netwerkcondities als een echt LAN met losse computers.
- Scripts staan **in memory**. Bij stop/herstart van een container verdwijnen diens gegevens; heraansluiten en resynchroniseren moet later worden ontworpen.
- Schrijven naar een peer gebruikt Full Replication. `GET` leest alleen bij de aangesproken peer, dus er wordt nog **niet** automatisch gezocht bij andere peers.
- Replicatie is synchroon en best effort; bij mislukte verzending kunnen de kopieën uiteenlopen. Versienummers blokkeren oudere updates maar lossen gelijktijdige, conflicterende writes met hetzelfde versienummer nog niet op.
- Er ontbreken security, automatische recovery, volledige testmetingen en ondersteuning voor 50 peers. Deze starter bewijst **niet** QAR-14 (volledige Agent-takeover binnen 3 seconden).

## Bestanden

- `Dockerfile` — multi-stage Maven-build en Java-runtime
- `compose.yaml` — drie peers in een lokaal Docker-netwerk
- `src/main/java/nl/han/cni/scriptopslag` — server, CLI, in-memory store en immutable ScriptData
- `docs/testplan.md` — testscenario's, afbakening en reproduceerbaarheid
- `docs/testresultaten.md` — invuldocument voor echte waarnemingen

Deze README is al gericht op **reproduceren**. Vul testresultaten en exacte versies pas in nadat je het daadwerkelijk op jouw laptop hebt uitgevoerd.
