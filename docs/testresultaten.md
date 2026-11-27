# Testresultaten — CNI-scriptopslag-PoC

## 1. Testomgeving

| Onderdeel | Configuratie |
|---|---|
| Testperiode | 25–27 november 2026 |
| Computer | Windows 11, Intel Core i7, 16 GB RAM |
| Docker / Docker Compose | 29.9.3 / v5.7.0 |
| Java / buildtool | Java 21 / Maven |
| Peers | A–C voor Full Replication, Data Fragmentation en Partial Replication; A–E voor Erasure Coding |
| Communicatie | TCP via een virtueel Docker-netwerk; eigen berichtformaat met Base64 |
| Opslag | In-memory (inhoud verdwijnt wanneer het Java-proces stopt) |
| Functionele testscripts | `agent-default`, `partial-test`, `erasure-test`; versies 1, 2 en 3 afhankelijk van de test |
| Meetbestand | `benchmark-test`, versie 1, inhoud `MOVE_FORWARD` (12 bytes) |

Iedere peer draait als afzonderlijk Java-proces in een Docker-container. Alle containers draaien op **één fysieke computer**. Protocol Buffers en netwerkversleuteling zijn niet gebruikt.

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

**Conclusie:** Een vooraf verspreid script bleef beschikbaar na uitval van Peer A. Bijwerken van actieve peers werkte ook. Dit ondersteunt **Availability** (beschikbaarheid) en **Fault Tolerance** (omgaan met uitval), waaronder QAR-10. Het automatisch inhalen van gemiste updates is niet getest.

## 3. Data Fragmentation (zonder Redundancy)

**Werking:** Het script wordt verdeeld in **unieke fragmenten**, één per peer. **Alle fragmenten zijn nodig** om het script te reconstrueren; er zijn geen reservekopieën.

| Test | Uitvoering en werkelijk resultaat | Status |
|---|---|---|
| DF01 — Handmatig opslaan/ophalen | A gaf `MOVE`, B gaf `_FOR` en C gaf `WARD` terug via TCP. | Geslaagd |
| DF02 — Automatisch verdelen | `MOVE_FORWARD` met `fsplit` over A, B en C verdeeld. | Geslaagd |
| DF03 — Reconstrueren | `fjoin` herstelde `MOVE_FORWARD` uit de drie fragmenten. | Geslaagd |
| DF04 — Peeruitval | B gestopt; reconstructie mislukte doordat fragment 1 niet bereikbaar was. | Geslaagd (verwachte mislukking) |
| DF05 — Herstart en databehoud | Het oorspronkelijke fragment na herstart is niet afzonderlijk opgevraagd. | Niet getest |

**Conclusie:** Zonder redundancy lukt reconstructie alleen als alle fragmenten beschikbaar zijn. De uitval van één peer met een uniek fragment maakte het script onbeschikbaar.

## 4. Partial Replication (Replication Factor 2)

**Werking:** Het volledige script wordt op **twee geselecteerde peers** opgeslagen in plaats van op alle drie. Bij uitval van één peer met een kopie blijft de andere kopie beschikbaar.

| Test | Uitvoering en werkelijk resultaat | Status |
|---|---|---|
| PR01 — Verspreiden | `partial-test` (`ATTACK_ENEMY`) opgeslagen op A en C: `2/2` kopieën. | Geslaagd |
| PR02 — Opslag controleren | A en C hadden het script; B gaf `NOT_FOUND`. | Geslaagd |
| PR03 — Eén kopie valt uit | A gestopt; ophalen via B als client leverde het script vanaf C. | Geslaagd |
| PR04 — Beide kopieën vallen uit | Ook C gestopt; ophalen via B gaf `Script niet beschikbaar.` | Geslaagd (verwachte mislukking) |

**Conclusie:** Met twee kopieën werd de uitval van één peer met een kopie opgevangen. Na uitval van beide peers met een kopie was het script onbeschikbaar. Automatisch opnieuw aanmaken van kopieën is niet getest.

## 5. Erasure Coding (Reed-Solomon 3+2)

**Werking:** Het script wordt verdeeld in **vijf shards**: drie datashards en twee herstelshards. Met **minimaal drie willekeurige shards** is het script te reconstrueren.

| Test | Uitvoering en werkelijk resultaat | Status |
|---|---|---|
| EC01 — Verspreiden | `erasure-test` (`MOVE_FORWARD`) over A t/m E verdeeld: `5/5` shards opgeslagen. | Geslaagd |
| EC02 — Vijf beschikbaar | Bij 5/5 shards werd `MOVE_FORWARD` hersteld. | Geslaagd |
| EC03 — Eén uitval | B gestopt; bij 4/5 shards werd `MOVE_FORWARD` hersteld. | Geslaagd |
| EC04 — Twee uitvallen | Ook C gestopt; bij 3/5 shards werd `MOVE_FORWARD` hersteld. | Geslaagd |
| EC05 — Drie uitvallen | Ook D gestopt; bij 2/5 shards mislukte reconstructie zoals verwacht. | Geslaagd (verwachte mislukking) |

**Conclusie:** In deze configuratie bleef het script beschikbaar bij maximaal twee ontbrekende shards. Met minder dan drie shards mislukte het herstel. De Reed-Solomon-berekeningen zijn een **eigen PoC-implementatie**, niet overgenomen uit de Backblaze-library.

## 6. Aanvullende JUnit-tests

Uitgevoerd met `mvn clean test`: **8 tests geslaagd** (`BUILD SUCCESS`).

| Onderdeel | Gecontroleerd | Resultaat |
|---|---|---|
| Data Fragmentation (4 tests) | Splitsen en samenvoegen, byteverdeling, ontbrekend fragment en ongeldig aantal fragmenten. | Geslaagd |
| Erasure Coding (4 tests) | Herstel met 5 shards; alle 10 combinaties van 2 ontbrekende shards; fout bij 3 ontbrekende shards; herstel van binaire gegevens. | Geslaagd |

De JUnit-tests controleren de algoritmen lokaal. De Docker-tests controleren daarnaast het versturen, opslaan en ophalen via TCP.

## 7. Vergelijkende metingen

Alle metingen zijn uitgevoerd met **`MOVE_FORWARD` (12 bytes)**. Voor iedere opslagmethode is met een schone in-memory opslag begonnen.

### 7.1 Uitvoeringstijd en opslaggebruik

De uitvoeringstijd is gemeten met `System.nanoTime()` rond het Java-clientcommando (`time put`, `time get`, enzovoort). Dit meet de clientbewerking **inclusief TCP-communicatie en verwerking**, maar grotendeels zonder de opstarttijd van Java en Docker Compose. Er is per bewerking één opwarmmeting uitgevoerd en daarna zijn **vijf metingen gemiddeld**. Bij distributie werd dezelfde scriptversie herhaaldelijk opgeslagen.

| Opslagmethode | Peers | Distributie gemiddeld | Ophalen gemiddeld | Payloadopslag totaal |
|---|---:|---:|---:|---:|
| Full Replication | 3 | **46,670 ms** | **23,501 ms** | 36 bytes |
| Data Fragmentation | 3 | 62,459 ms | 62,007 ms | **12 bytes** |
| Partial Replication (RF=2) | 3 | 66,338 ms | 36,754 ms | 24 bytes |
| Erasure Coding (3+2) | 5 | 71,141 ms | 63,425 ms | 30 bytes |

De opslagwaarden komen uit `STATS` (`payload-bytes`) per peer, bij elkaar opgeteld. Ze omvatten **alleen opgeslagen script- en shardbytes**, niet de Java-objecten, metadata of het totale containergeheugen. Erasure Coding bewaart in deze implementatie ook vier bytes voor de oorspronkelijke scriptlengte en vult shards aan tot gelijke grootte.

**Observatie:** Full Replication had in deze meetronde de kortste gemiddelde distributie- en ophaaltijd. Data Fragmentation had de laagste payloadopslag. De resultaten zijn **verkennend**, niet representatief voor prestaties op een echt LAN of met grotere scripts.

### 7.2 Netwerkverkeer tijdens distributie

Het netwerkverkeer is één keer per methode gemeten met `docker stats --no-stream`, vóór en na het verspreiden van het script. Onderstaande waarden zijn de **benaderde verschillen** in netwerk-I/O van de deelnemende peers, inclusief TCP-overhead en mogelijk achtergrondverkeer.

| Opslagmethode | Deelnemende peers | Extra ontvangen | Extra verzonden |
|---|---|---:|---:|
| Full Replication | A–C | ≈ 2.030 B | ≈ 1.868 B |
| Data Fragmentation | A–C | ≈ 1.890 B | ≈ 1.728 B |
| Partial Replication (RF=2) | A–C | ≈ 980 B | ≈ 901 B |
| Erasure Coding (3+2) | A–E | ≈ 4.100 B | ≈ 3.452 B |

**Interpretatie:** Partial Replication registreerde in deze meetronde het minste verkeer en Erasure Coding het meeste. De verschillen zijn **geen zuivere meting van uitsluitend scriptverkeer**: Docker-statistieken bevatten overhead en achtergrondverkeer en zijn afgerond. Zo werd ook extra verkeer geregistreerd op niet-deelnemende peers. Bovendien gebruikt Erasure Coding vijf peers, tegenover drie bij de andere methoden. Op basis van deze eenmalige metingen kan geen betrouwbare algemene rangorde voor netwerkgebruik worden vastgesteld.

**Niet gemeten:** netwerkverkeer tijdens ophalen/reconstrueren. De tabel hierboven betreft uitsluitend **distributie**.

### 7.3 Grenzen van de metingen

- Er is slechts **één klein script van 12 bytes** gebruikt. Grotere bestanden en andere scriptinhoud kunnen andere verhoudingen opleveren.
- Bij de tijdmetingen is dezelfde scriptversie meerdere keren geschreven; dit is geen benchmark van uitsluitend nieuwe scripts of gelijktijdige belasting.
- De netwerkmetingen zijn per methode **éénmalig** en bevatten overhead en achtergrondverkeer.
- Erasure Coding gebruikte vijf peers; de overige methoden gebruikten drie. De gebruikte configuraties zijn dus niet volledig gelijk.
- Er is geen afzonderlijke SHA-256-controle uitgevoerd. JUnit heeft wel de gelijkheid van originele en herstelde bytes gecontroleerd, maar detectie van beschadigde netwerkgegevens is niet onderzocht.
- De resultaten zijn nog niet gekoppeld aan een vastgelegde Git-commit. Voor exacte reproductie moet de gebruikte projectversie worden genoteerd.

## 8. Voorlopige eindanalyse

| Methode | Beschikbaarheid bij uitval (geteste configuratie) | Afweging op basis van deze PoC |
|---|---|---|
| Full Replication (3 kopieën) | Beschikbaar na uitval van één geteste peer. | Snelle toegang tot een volledige kopie; hogere payloadopslag. |
| Data Fragmentation (3 unieke delen) | Niet beschikbaar na uitval van één peer met een uniek deel. | Laagste payloadopslag, maar zonder redundantie kwetsbaar. |
| Partial Replication (2 kopieën) | Eén peer met kopie mag uitvallen; beide niet. | Minder opslag dan Full Replication, met beperkte redundantie. |
| Erasure Coding (3+2) | Herstel met 3/5 shards; niet met 2/5. | Verdraagt twee ontbrekende shards, maar vereist reconstructie en meerdere peers. |

**Voorlopige conclusie:** Full Replication lijkt voor deze PoC aantrekkelijk door de eenvoudige beschikbaarheid van volledige scripts en de relatief korte gemeten uitvoeringstijden. Erasure Coding biedt eveneens fouttolerantie en kan bij grotere scripts opslag besparen, maar is complexer. Dit is **geen definitieve keuze**: de verschillen zijn onderzocht met kleine testgegevens, ongelijke peeraantallen en één fysieke computer. De architectuurkeuze wordt onderbouwd met deze resultaten, het literatuuronderzoek en de relevante Quality Attribute Requirements in de **Architecture Decision Record (ADR)**.

## 9. Grenzen van de PoC

- **Eén fysieke computer:** de Docker-peers communiceren via een virtueel netwerk, niet over meerdere fysieke LAN-apparaten.
- **Geen persistentie:** scripts en shards staan in het geheugen en verdwijnen als hun Java-proces stopt.
- **Geen automatisch herstel na peeruitval:** kopieën of shards worden niet opnieuw verdeeld; gemiste scriptupdates worden niet automatisch ingehaald.
- **Beperkte schaal:** maximaal vijf peers, geen test met 50 spelers of grote scripts.
- **Geen volledige game-integratie:** uitvoering van Agents/NPC's, agentovername binnen drie seconden (QAR-14), Source of Truth en Host Migration zijn niet getest.
- **Geen productieprotocol of beveiliging:** UDP Peer Discovery, Protocol Buffers (ADR-17, Draft) en ChaCha20-Poly1305 (ADR-20, Accepted) zijn niet geïmplementeerd.

De uitkomsten beschrijven **functioneel gedrag en verkennende metingen binnen deze testomgeving**, niet de gegarandeerde prestaties, schaalbaarheid of beveiliging van de uiteindelijke game.
