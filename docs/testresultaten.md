# Testresultaten — CNI-scriptopslag-PoC

## 1. Testomgeving

| Onderdeel | Configuratie |
|---|---|
| Testdatum | 25 november 2026 |
| Computer | Windows 11, Intel Core i7, 16 GB RAM |
| Docker / Docker Compose | 29.9.3 / v5.7.0 |
| Java / buildtool | Java 21 / Maven |
| Peers | A–C voor Full Replication, Data Fragmentation en Partial Replication; A–E voor Erasure Coding |
| Communicatie | TCP via een virtueel Docker-netwerk, eigen berichtformaat met Base64 |
| Opslag | In-memory (gegevens verdwijnen bij het stoppen van een container) |
| Testgegevens | `agent-default`, `partial-test`, `erasure-test`; versies 1, 2 en 3 afhankelijk van de test |

Iedere peer is een afzonderlijk Java-proces in een Docker-container. Alle containers draaien op **één fysieke computer**. De PoC gebruikt geen Protocol Buffers of netwerkversleuteling.

## 2. Full Replication

**Werking:** Iedere deelnemende peer bewaart een **volledige kopie** van het script. Daardoor kan een andere peer het script leveren als de oorspronkelijke peer uitvalt.

| Test | Uitvoering en werkelijk resultaat | Status |
|---|---|---|
| FR01 — Peers starten | Peer A, B en C gestart met Docker Compose. | Geslaagd |
| FR02 — Communicatie | Ping naar Peer A gaf `PONG\|peer-a`. | Geslaagd |
| FR03 — Verspreiden | `agent-default` versie 1 (`MOVE_FORWARD`) via A opgeslagen; `replicated-to=2/2`. | Geslaagd |
| FR04 — Ophalen | B gaf versie 1 met `MOVE_FORWARD` terug. | Geslaagd |
| FR05 — Peeruitval | A gestopt; C kon versie 1 nog ophalen. | Geslaagd |
| FR06 — Bijwerken | A opnieuw gestart en versie 2 (`ATTACK_ENEMY`) verstuurd; B en C gaven versie 2 terug. | Geslaagd |

**Conclusie:** Een vooraf succesvol verspreid script bleef beschikbaar na uitval van Peer A. Ook verspreiding van een nieuwe versie naar actieve peers werkte. Dit ondersteunt de onderzochte **Availability** (beschikbaarheid) en **Fault Tolerance** (bestandheid tegen uitval), waaronder QAR-10. Automatisch inhalen van gemiste updates door terugkerende peers is niet getest.

## 3. Data Fragmentation (zonder Redundancy)

**Werking:** Het script wordt opgesplitst in unieke fragmenten. Iedere peer krijgt één fragment; **alle fragmenten zijn nodig** om het script weer samen te voegen. Er bestaan geen reservekopieën.

| Test | Uitvoering en werkelijk resultaat | Status |
|---|---|---|
| DF01 — Handmatig opslaan/ophalen | A gaf `MOVE`, B gaf `_FOR` en C gaf `WARD` terug via TCP. | Geslaagd |
| DF02 — Automatisch verdelen | `MOVE_FORWARD` met `fsplit` over A, B en C verdeeld. | Geslaagd |
| DF03 — Reconstrueren | `fjoin` herstelde `MOVE_FORWARD` uit drie fragmenten. | Geslaagd |
| DF04 — Peeruitval | B gestopt; reconstructie mislukte omdat fragment 1 niet bereikbaar was. | Geslaagd (verwachte mislukking) |
| DF05 — Herstart en databehoud | Aanwezigheid van het oorspronkelijke fragment na herstart niet afzonderlijk gecontroleerd. | Niet getest |

**Conclusie:** Zonder redundantie werkt reconstructie alleen wanneer alle fragmenten beschikbaar zijn. Uitval van één peer met een uniek fragment maakt het script onbeschikbaar.

## 4. Partial Replication (Replication Factor 2)

**Werking:** Het volledige script wordt op **twee geselecteerde peers** bewaard, niet op alle drie. Bij uitval van één van die twee blijft de andere kopie bruikbaar.

| Test | Uitvoering en werkelijk resultaat | Status |
|---|---|---|
| PR01 — Verspreiden | `partial-test` (`ATTACK_ENEMY`) opgeslagen op A en C: `2/2` kopieën. | Geslaagd |
| PR02 — Opslag controleren | A en C hadden het script; B gaf `NOT_FOUND`. | Geslaagd |
| PR03 — Eén kopie valt uit | A gestopt; ophalen via B als client leverde het script vanaf C. | Geslaagd |
| PR04 — Beide kopieën vallen uit | Ook C gestopt; ophalen via B gaf `Script niet beschikbaar.` | Geslaagd (verwachte mislukking) |

**Conclusie:** Met twee kopieën bleef het script beschikbaar bij uitval van één peer met een kopie. Bij uitval van beide peers met een kopie was het script onbeschikbaar. Automatische aanmaak van vervangende kopieën is niet getest.

## 5. Erasure Coding (Reed-Solomon 3+2)

**Werking:** Het script wordt omgezet in **vijf shards**: drie datashards en twee herstelshards. Met **minimaal drie willekeurige beschikbare shards** kan het script worden hersteld.

| Test | Uitvoering en werkelijk resultaat | Status |
|---|---|---|
| EC01 — Verspreiden | `erasure-test` (`MOVE_FORWARD`) over A t/m E verdeeld: `5/5` shards opgeslagen. | Geslaagd |
| EC02 — Vijf beschikbaar | Bij 5/5 shards werd `MOVE_FORWARD` hersteld. | Geslaagd |
| EC03 — Eén uitval | B gestopt; bij 4/5 shards werd `MOVE_FORWARD` hersteld. | Geslaagd |
| EC04 — Twee uitvallen | Ook C gestopt; bij 3/5 shards werd `MOVE_FORWARD` hersteld. | Geslaagd |
| EC05 — Drie uitvallen | Ook D gestopt; bij 2/5 shards mislukte reconstructie zoals verwacht. | Geslaagd (verwachte mislukking) |

**Conclusie:** In deze configuratie bleven scripts beschikbaar na uitval van maximaal twee peers. Met minder dan drie shards was herstel niet mogelijk. De gebruikte Reed-Solomon-berekeningen zijn een **eigen PoC-implementatie**, geen overgenomen Backblaze-library.

## 6. Aanvullende JUnit-tests

Uitgevoerd met `mvn clean test`: **8 tests geslaagd** (`BUILD SUCCESS`).

| Onderdeel | Gecontroleerd | Resultaat |
|---|---|---|
| Data Fragmentation (4 tests) | Opsplitsen en samenvoegen, byteverdeling, ontbrekend fragment en ongeldige hoeveelheid fragmenten. | Geslaagd |
| Erasure Coding (4 tests) | Herstel met 5 shards; alle 10 combinaties van 2 ontbrekende shards; verwachte fout bij 3 ontbrekende shards; herstel van binaire gegevens. | Geslaagd |

De JUnit-tests controleren de algoritmen lokaal. De Docker-tests controleren daarnaast opslag en ophalen via het netwerk.

## 7. Vergelijkende metingen

**Status: nog niet uitgevoerd.** De bovenstaande tests tonen functioneel gedrag aan, maar bevatten geen gemeten uitvoeringstijden of netwerkverkeer.

| Criterium | Wat nog gemeten of beoordeeld moet worden |
|---|---|
| Performance | Tijd voor verspreiden en ophalen/reconstrueren (ms) |
| Storage Usage | Totale opgeslagen payloadbytes per methode |
| Network Traffic | Verstuurde en ontvangen bytes tijdens distributie en ophalen |
| Data Integrity | Vergelijking van originele en herstelde bytes, bijvoorbeeld met SHA-256 |
| Implementation Complexity | Benodigde code, afhankelijkheden en herstelmechanismen beoordelen |

Voor een reproduceerbare vergelijking leggen we later de **Git-commit, testbestandgrootte, configuratie, meetmethode en het aantal herhalingen** vast. De methoden zijn nu niet allemaal met hetzelfde aantal peers getest (drie versus vijf); daarmee moet bij de interpretatie rekening worden gehouden.

## 8. Voorlopige eindanalyse

Alle vier de opslagmethoden zijn **functioneel getest**. De tests laten het volgende zien:

| Methode | Beschikbaarheid bij peeruitval in de geteste situatie |
|---|---|
| Full Replication (3 kopieën) | Script bleef beschikbaar na uitval van één peer. |
| Data Fragmentation (3 unieke delen) | Script kon niet worden hersteld na uitval van één peer met een uniek deel. |
| Partial Replication (2 kopieën) | Eén uitgevallen peer met een kopie werd opgevangen; uitval van beide niet. |
| Erasure Coding (3+2) | Tot twee ontbrekende shards werden opgevangen; drie niet. |

Dit zijn **waarnemingen uit de PoC**, geen definitieve prestatievergelijking. Welke methode het beste past bij de Dungeon Game wordt na de vergelijkende metingen en het literatuuronderzoek onderbouwd in de **Architecture Decision Record (ADR)**.

## 9. Grenzen van de PoC

- **Eén fysieke computer:** Docker-peers communiceren over een virtueel netwerk, niet over meerdere fysieke LAN-apparaten.
- **Geen persistentie:** scripts en shards worden in-memory opgeslagen en kunnen verdwijnen bij containerherstart.
- **Geen automatisch herstel na peeruitval:** ontbrekende kopieën of shards worden niet automatisch opnieuw verdeeld; gemiste scriptupdates worden niet automatisch ingehaald.
- **Beperkte schaal en testdata:** kleine voorbeeldscripts en maximaal vijf peers; geen schaaltest met 50 spelers of grotere scripts.
- **Geen volledige game-integratie:** uitvoering van Agents/NPC's, automatische agentovername binnen drie seconden (QAR-14), Source of Truth en Host Migration zijn niet getest.
- **Geen productieprotocol of beveiliging:** UDP Peer Discovery, Protocol Buffers (ADR-17, Draft) en ChaCha20-Poly1305 (ADR-20, Accepted) zijn niet geïmplementeerd.

De uitkomsten onderbouwen dus het **functionele gedrag van de opslagstrategieën binnen deze testomgeving**, niet de prestaties of beveiliging van de uiteindelijke game.
