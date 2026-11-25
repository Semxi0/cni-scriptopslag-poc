# Testplan — Scriptopslag in P2P

## Doel

Onderzoeken hoe vier opslagmethoden zich gedragen wanneer scripts binnen een netwerk van peers worden opgeslagen, opgehaald, gewijzigd en wanneer een peer uitvalt. De resultaten helpen bij een latere ADR; er is **nog geen voorkeursmethode gekozen**.

## Methoden

1. Full Replication (hele scripts op alle peers)
2. Data Fragmentation **zonder Redundancy** (één exemplaar van ieder deel; basisvariant)
3. Partial Replication (hele scripts op een beperkt aantal peers, bijvoorbeeld Replication Factor 3)
4. Erasure Coding (voorbeeld: Reed-Solomon 3+2)

Data Fragmentation zonder Redundancy wordt gebruikt als basisvariant om gevolgen van fragmentatie zonder extra bescherming zichtbaar te maken. Het is vooraf duidelijk dat het verlies van een uniek scriptdeel tot onbeschikbaarheid kan leiden.

## Testomgeving

- Eén Windows-laptop met Docker Desktop
- Aparte Java 21-processen in Docker-containers op een Docker bridge network
- Communicatie via TCP-sockets (alleen in deze PoC)
- Per run dezelfde hardware, scriptinhoud en vergelijkbare belasting
- **Beperking:** virtueel lokaal netwerk, geen vergelijking met internetverbindingen

## Testvragen (nog niet allemaal geïmplementeerd)

| ID | Scenario | Wat vastleggen? |
|---|---|---|
| T1 | Script Distribution | Tijd, totaal verstuurde bytes en aantal kopieën/delen |
| T2 | Script Retrieval | Tijd tot volledig script beschikbaar is |
| T3 | Peer Failure | Kan script na stoppen van één peer nog worden opgehaald? |
| T4 | Script Updates | Krijgen relevante peers de nieuwe versie? |
| T5 | Runtime NPC-script | Kan nieuw script tijdens runtime worden verdeeld/opgehaald? |
| T6 | Storage Usage | Totaal en per peer opgeslagen bytes, incl. Redundancy |
| T7 | Data Integrity | Is opgehaald script byte-voor-byte hetzelfde als origineel? |
| T8 | Scalability | Gedrag bij bijvoorbeeld 3, 5, 10 en 25 peers; 50 als haalbaar |

Test T7 kan uitgevoerd worden door bytes direct te vergelijken. Een SHA-256-checksum kan als hulpmiddel worden gebruikt, maar is **geen onderdeel van de literatuurvergelijking** en nog geen besluit.

## Voorgestelde testdata

- Klein leesbaar voorbeeldscript voor handmatige demo
- Gegenereerde tekstbestanden (bijvoorbeeld 1 KB, 10 KB en 100 KB), gelijke inhoud voor alle methoden
- Aanvullend testscript dat aangepast wordt tijdens runtime

De definitieve groottes leggen we vast vóór de metingen. De genoemde aantallen zijn **testinstellingen**, geen schattingen van echte game-scripts.

## Reproduceerbaarheid

- Noteer Docker-, Java-versie en relevante hardware
- Leg de exacte commando's, instellingen en testdata vast
- Herhaal metingen onder gelijke omstandigheden (bijvoorbeeld vijf runs)
- Leg ruwe resultaten vast; bereken daarna gemiddelde en spreiding
- Beschrijf storingen en mislukte runs, niet alleen geslaagde tests

## Status en volgorde

1. Docker-netwerk en Full Replication opzetten (starter)
2. Testen met echte TCP-berichten en peeruitval
3. Partial Replication toevoegen
4. Data Fragmentation toevoegen
5. Erasure Coding toevoegen
6. Meetpunten automatiseren en resultaten verzamelen
7. Onderzoeksdocument aanvullen en ADR opstellen

**Nog niet claimen:** de eis van drie seconden voor volledige agentovername is hiermee niet bewezen. Deze PoC meet scriptbeschikbaarheid, niet de volledige agentovername of Peer Election.
