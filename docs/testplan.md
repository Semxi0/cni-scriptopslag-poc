# Testplan — CNI-scriptopslag-PoC

## 1. Doel

Onderzoeken hoe Agent- en NPC-scripts in een peer-to-peer-netwerk kunnen worden verdeeld en beschikbaar blijven wanneer een peer uitvalt. De PoC vergelijkt vier opslagmethoden. De uiteindelijke keuze wordt onderbouwd in een aparte ADR; welke peer scripts uitvoert, valt buiten dit onderzoek.

| Methode | Verdeling | Wat onderzoeken we bij uitval? |
|---|---|---|
| **Full Replication** | Volledig script op alle 3 deelnemende peers | Is het script nog beschikbaar als een peer uitvalt? |
| **Data Fragmentation (zonder redundancy)** | Script in 3 unieke delen op 3 peers | Mislukt reconstructie als een uniek deel ontbreekt? |
| **Partial Replication (RF=2)** | Volledig script op 2 van 3 peers | Blijft één kopie voldoende; wat als beide wegvallen? |
| **Erasure Coding (3+2)** | 3 datashards en 2 herstelshards op 5 peers | Is herstel mogelijk met minimaal 3 van de 5 shards? |

## 2. Testaanpak

- **Functioneel:** script verdelen, ophalen of reconstrueren en vervolgens één of meer Docker-peers stoppen. Verwachte mislukking bij te weinig kopieën of shards telt als een geslaagde test.
- **JUnit:** lokaal controleren of Data Fragmentation correct splitst en samenvoegt en of Erasure Coding ontbrekende shards kan herstellen.
- **Opslaggebruik:** met `STATS` de opgeslagen script- en fragmentpayloadbytes per peer optellen, exclusief Java-objecten en overige overhead.
- **Uitvoeringstijd:** met het `time`-commando de volledige Java-clientbewerking meten, inclusief TCP-verzoeken. Per methode één opwarmmeting en vijf meetruns voor **verdelen** en **ophalen**; gemiddelde van de vijf meetruns rapporteren.
- **Netwerkverkeer:** met `docker stats` het verschil in ontvangen en verzonden bytes vóór en na **één distributie** meten. Dit is indicatief, omdat TCP-overhead en achtergrondverkeer meetellen; ophalen is niet op deze manier gemeten.

De vergelijkende metingen gebruiken steeds `MOVE_FORWARD` (**12 bytes**) en beginnen met lege in-memory opslag. Waar mogelijk worden dezelfde omstandigheden aangehouden; Full Replication, Data Fragmentation en Partial Replication gebruiken 3 peers, Erasure Coding 5.

## 3. Reproduceerbaarheid

De **README** bevat de vereisten en exacte Maven- en Docker-commando's. `testresultaten.md` bevat de werkelijk uitgevoerde tests, meetwaarden en conclusies. Noteer voor definitieve reproduceerbaarheid ook de gebruikte Git-commit. De Docker-peers zijn afzonderlijke Java-processen op **één fysieke computer** en geen onafhankelijke LAN-apparaten.

## 4. Grenzen

- De resultaten gelden voor kleine testgegevens en maximaal 5 Docker-peers; schaalbaarheid naar 50 spelers en grotere scripts is niet getest.
- De opslag is in-memory. Automatische herverdeling na uitval, het inhalen van gemiste scriptupdates en persistentie zijn niet getest.
- De PoC onderzoekt geen uitvoering van Agents/NPC's, volledige agentovername binnen 3 seconden (QAR-14), game-state Source of Truth of Host Migration.
- De PoC gebruikt TCP met een eigen Base64-berichtformaat; UDP Peer Discovery, Protocol Buffers en ChaCha20-Poly1305 zijn niet geïmplementeerd.
- De tijd- en netwerkmetingen zijn verkennend: geen prestaties van fysieke P2P-netwerken of de uiteindelijke game claimen. Gegevensintegriteit is functioneel gecontroleerd door inhoud/bytes te vergelijken; er is geen afzonderlijke SHA-256-controle uitgevoerd.
