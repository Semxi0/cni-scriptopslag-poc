# Testresultaten — CNI-scriptopslag-PoC

> Vul alleen resultaten in die echt op jouw laptop zijn gemeten. Deze starter bevat geen meetresultaten.

## Omgeving

- Datum / tijd:
- Git commit-ID:
- Windows-versie / CPU / geheugen:
- `docker version`:
- `docker compose version`:
- Java-runtime in container:
- Aantal draaiende peers:
- Testpayload (naam, versie, grootte):
- Transport: TCP, lokaal Docker bridge; geen Protocol Buffers of ChaCha20-Poly1305 in starter

## Handmatige startercontrole — Full Replication

| Scenario | Verwachting | Werkelijk waargenomen | Geslaagd? |
|---|---|---|---|
| 3 containers gestart | peer-a/b/c Up | Nog niet getest | — |
| Ping naar A | `PONG\|peer-a` | Nog niet getest | — |
| Script v1 op A opslaan | `replicated-to=2/2` | Nog niet getest | — |
| v1 bij B ophalen | zelfde versie en inhoud | Nog niet getest | — |
| A stoppen, bij C ophalen | v1 nog beschikbaar | Nog niet getest | — |
| A herstarten, update v2 via B | C kan v2 ophalen | Nog niet getest | — |

## Latere metingen

| Run | Methode | Peers | Payloadbytes | Peeruitval | Distributietijd ms | Ophalen ms | Beschikbaar? | Integrity? | Opslagbytes | Gemeten netwerkbytes | Opmerking |
|---|---|---|---|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — | — | — | — | — |

## Analyse / beperkingen

- Geen prestatieclaims zonder reproduceerbare metingen.
- Docker op één machine ≠ meerdere fysieke pc's.
- De starter gebruikt Base64 en geen Protocol Buffers of ChaCha20-Poly1305.
- In-memory scriptopslag is niet persistent na procesherstart.
- Game-state Source of Truth en volledige Agent-takeover vallen buiten deze metingen.
