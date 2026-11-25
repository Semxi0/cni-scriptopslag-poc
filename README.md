# CNI — Scriptopslag PoC (Docker / TCP)

Dit is een **onderzoeksprototype** voor de opslag van Agent- en NPC-scripts in een peer-to-peer-omgeving zonder centrale spelserver. Iedere Docker-container is een apart Java-proces en communiceert met andere peers via **TCP-sockets**.

## Status

- **Stap 1 (starter):** Docker-netwerk met drie peers; werkende **Full Replication** van scripts in memory.
- **Nog te ontwikkelen:** Partial Replication, Data Fragmentation, Erasure Coding, geautomatiseerde meetruns, peerherstel en scripts op schijf.
- **Geen definitieve architectuurkeuze:** TCP is alleen gekozen voor deze onderzoeks-PoC. Het project hoeft TCP niet als definitief netwerkprotocol te gebruiken.

## Benodigd

- Docker Desktop met Docker Compose (`docker compose version`)
- Optioneel: Java 21 en Maven voor lokale compilatie

## Eerste demo — in de projectroot (bij `compose.yaml`)

```powershell
docker compose up -d --build
docker compose ps
```

Het eerste commando bouwt de Java-app en start drie containers (`peer-a`, `peer-b`, `peer-c`). Als `docker compose ps` alle drie als *Up* laat zien, kun je de volgende stappen uitvoeren.

### 1. Controleer het netwerk

```powershell
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient ping
```

Verwachte uitvoer: `PONG|peer-a`.

### 2. Schrijf een testscript op Peer A

```powershell
docker compose exec -T peer-a java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient put agent-default 'MOVE_FORWARD'
```

De server stuurt via TCP kopieën naar peer-b en peer-c. Verwachte uitvoer als alle peers bereikbaar zijn: `OK|stored-locally|replicated-to=2/2`.

### 3. Haal het script op bij een andere peer

```powershell
docker compose exec -T peer-b java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient get agent-default
```

Verwachte uitvoer: `MOVE_FORWARD`.

### 4. Simuleer Peer Failure

```powershell
docker compose stop peer-a
docker compose exec -T peer-c java -cp /app/app.jar nl.han.cni.scriptopslag.PeerClient get agent-default
```

Verwachte uitvoer: `MOVE_FORWARD`. Peer C heeft een lokale kopie en vraagt niets op bij de uitgevallen Peer A.

### 5. Stop de omgeving

```powershell
docker compose down
```

> Let op: de scripts staan **alleen in memory**. Stoppen of opnieuw starten van containers wist hun scripts. Dit is een expliciete beperking van deze eerste stap.

## Lokaal compileren (optioneel)

```powershell
mvn clean package
```

Docker bouwt het project ook zelf. Voor de eerste Docker-demo hoef je Maven niet lokaal uit te voeren.

## Projectbestanden

- `compose.yaml`: drie containers en één virtueel Docker-netwerk
- `Dockerfile`: multi-stage Maven-build + kleine Java-runtime
- `src/main/java/.../PeerServer.java`: TCP-server en Full Replication
- `src/main/java/.../PeerClient.java`: CLI voor handmatige tests
- `src/main/java/.../ScriptStore.java`: scriptopslag in memory
- `docs/testplan.md`: aanpak en te meten eigenschappen
- `docs/testresultaten.md`: lege plek voor resultaten en observaties

## Grenzen van dit prototype

De containers communiceren via een echt, maar lokaal **virtueel netwerk** op dezelfde laptop. Dat test netwerkverzoeken en het stoppen van onafhankelijke processen, maar is geen bewijs voor latencies op internet of 50 fysieke computers. Het huidige protocol heeft geen authenticatie, versiebeheer, automatische reparatie, blijvende opslag of garandeerde aflevering. Gebruik dit niet als productiecode.

Na het afronden van alle methoden schrijven we de README om tot een definitieve, reproduceerbare handleiding met exacte stappen, resultaten en beperkingen.
