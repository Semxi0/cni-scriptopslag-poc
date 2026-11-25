# Testplan — Verdeling en beschikbaarheid van Agent/NPC-scripts

## 1. Doel en onderzoeksvraag

Onderzoeken hoe vier opslagmethoden scriptgegevens in een P2P-netwerk verdelen en beschikbaar houden wanneer een peer wegvalt. De opslagmethode wordt pas na vergelijking en tests in een eigen ADR gekozen; vooraf geen winnaar aanwijzen. *Welke peer het script uitvoert* en *welke peer de game-state beheert* zijn aparte vraagstukken.

**Onderzochte methoden:**

1. Full Replication — alle peers volledige scriptpayload.
2. Data Fragmentation **zonder Redundancy** — ieder deel staat één keer op een peer; baseline die verwacht kwetsbaar is bij uitval van een uniek deel.
3. Partial Replication — volledig script op subset van peers, bijvoorbeeld Replication Factor 3.
4. Erasure Coding — bijvoorbeeld Reed-Solomon 3+2: 3 datadelen + 2 Parity Blocks, te reconstrueren uit 3 van 5 geldige shards.

## 2. Context en architecturale randvoorwaarden

| Document | Status | Invloed op deze PoC |
|---|---|---|
| Onderzoek *Hoe wordt het script opgeslagen?* | onderzoek | `.script` als plain-text lokale bron; voorstel om AST te versturen |
| ADR-17 berichtformaat | **Draft** | Protocol Buffers als voorstel; huidige testprotocol is **niet** Protocol Buffers |
| ADR-20 Network Security | **Accepted** | ChaCha20-Poly1305; starter implementeert geen encryptie, vermeld effect op metingen |
| ADR-11 Peer Discovery | **Accepted** | UDP broadcast voor LAN discovery; Docker gebruikt vaste DNS-hostnamen voor beperkte scope |
| ADR-10 Source of Truth | **Draft** | Host-authoritative + event log voorgesteld voor game-state; **geen** game-state replicatie in dit onderzoek |
| QAR-10 | eis | script beschikbaar bij peeruitval |
| QAR-14 | eis | agent takeover binnen 3 sec; **niet volledig toetsbaar** via uitsluitend scriptherstel |
| QAR-05 | eis | tot 50 spelers; lokaal simuleren van aantallen is geen prestatiebewijs voor 50 computers |

**Vergelijkingsobject:** dezelfde **payloadbytes** voor alle vier de methoden. De starter gebruikt tekst als testpayload, later ook gegenereerde bytes (bijv. 1, 10, 100 KB). Een AST in Protocol Buffers kan pas eerlijk worden gemeten wanneer er een afgesproken `.proto`-schema en serializer zijn. Extra overhead van Protocol Buffers, encryptie, headers en discovery worden in de starter niet gemeten.

## 3. Testomgeving

- Windows 11, Docker Desktop, Java 21 in losse Linux-containers, één Docker bridge network.
- TCP-sockets tussen processen; geen HTTP/REST en geen centrale server. Beperking: TCP staat los van de projectbrede discoverykeuze.
- Eén laptop: Docker-verkeer is wel echt netwerk-I/O maar niet representatief voor verspreide fysieke machines.
- Bij benchmark: identieke payloads, versies, scenario's, hardware en methoden. Mislukte runs vastleggen.

## 4. Testscenario's (nog niet allemaal geïmplementeerd)

| ID | Test | Verwachte te onderzoeken gegevens |
|---|---|---|
| T01 | Distributie | Correcte kopieën/shards, distributietijd, verzonden bytes |
| T02 | Ophalen | Beschikbaarheid en latency tot volledige payload |
| T03 | Uitval van één peer | Is payload nog beschikbaar na daadwerkelijk stoppen van een container? |
| T04 | Uitval van twee peers | Wat gebeurt er bij verschillende combinaties van uitgevallen peers? |
| T05 | Script Update | Versienummer en inhoud consistent bij relevante online peers? |
| T06 | Runtime NPC | Nieuw script tijdens runtime verdelen zonder herstart van de sessie |
| T07 | Data Integrity | Na ophalen exact dezelfde bytes als origineel, optioneel SHA-256 |
| T08 | Storage Usage | Per peer en totaal opgeslagen payloadbytes, exclusief overhead apart rapporteren |
| T09 | Schaalbaarheid | Begin met 3/5; later 10/25 en alleen indien haalbaar 50 Docker-processen |
| T10 | Ongewijzigde scripts | Hoeveel data wordt opnieuw verzonden bij herhaalde distributie? |

**NB:** de code in deze starter ondersteunt alleen handmatige controles T01/T02/T03, een deel van T05 en eenvoudige T08/T09-observaties. De overige methoden, metingen en automations worden later toegevoegd. Geen conclusies trekken over niet-uitgevoerde tests.

## 5. Metingen

- **Availability**: wel/niet op te halen met ontbrekende peers.
- **Data Integrity**: byte-voor-byte gelijk (SHA-256 optioneel en expliciet benoemd als testinstrument, niet als onderdeel van de literatuurkeuze).
- **Storage Usage**: payloadbytes per peer, eventueel afzonderlijk metadata-overhead.
- **Performance**: distribute/retrieve duur en variatie, bijvoorbeeld 20 herhalingen per scenario na warmup.
- **Network Traffic**: meet echte verzonden/ontvangen bytes **pas wanneer counters of capture zijn geïmplementeerd**; aantallen kopieën zijn geen directe meting van wire traffic. Base64 vergroot het testverkeer in deze starter en maakt absolute vergelijkingen met protobuf ongeldig.
- **Implementation Complexity**: beschrijf objectief dependencies, componenten en foutafhandeling; claim geen numerieke complexiteitsmeting zonder methode.

## 6. Reproduceerbaarheid en volgorde

1. Versies, hardware en exact getest commit-ID noteren.
2. Met `docker compose up -d --build` drie peers starten.
3. Handmatige Full Replication- en failure-test uitvoeren; waarnemingen noteren in `testresultaten.md`.
4. Eenzelfde opslaginterface en reproduceerbaar testharnas maken voor de overige methoden.
5. Voor Erasure Coding 3+2 minimaal vijf afzonderlijke peers inzetten als iedere shard op een andere peer staat.
6. Tests automatiseren, gelijke invoer en uitvalscenario's toepassen en resultaten opnemen in onderzoeksdocument en ADR.

**Scopegrens:** geen Agent-executie, parser, game-state-autoriteit, host election, volledig beveiligde berichten of hervatten van een opgeslagen sessie. Geen 3s-QAR14-conformiteit claimen op basis van alleen deze scriptopslagmetingen.
