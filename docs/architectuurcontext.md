# Architectuurcontext en afbakening

Bijgewerkt op 25 november 2026. Dit is context voor het opslagonderzoek, geen nieuwe ADR.

- ADR-11 **Accepted**: UDP broadcast voor lobbydiscovery op LAN, handmatig adres als fallback. De PoC gebruikt vaste Docker DNS-hostnames; het is geen UDP-broadcast-test.
- ADR-20 **Accepted**: ChaCha20-Poly1305 voor netwerkbeveiliging in het spel. In deze PoC nog niet toegepast; de onderzoeker moet dit expliciet meewegen bij performancemeting en bij eventuele integratie.
- ADR-17 **Draft**: Protocol Buffers als voorstel voor algemene netwerkberichten. De PoC gebruikt nu een eigen regels/TCP/Base64-protocol, geen Protobuf.
- ADR-10 **Draft**: host-authoritative met event log is een voorgestelde Source of Truth van de **spelwereld**. Dit schrijft niet automatisch Full Replication voor voor **scripts**; dit zijn twee verschillende soorten gegevens.
- Onderzoek *Hoe wordt het script opgeslagen?*: lokale brontekst als `.script`, mogelijk een AST via Protocol Buffers. Voor de opslagstrategie is een `byte[]`-payload geschikt, zodat het uiteindelijke formaat later kan wisselen.

**Bewust buiten scope:** AST genereren, interpreter, exacte `.proto`-schema's, game-state-eventlog, discovery, productieauthenticatie, scriptpersistentie op schijf. Het uiteindelijke opslagontwerp moet later integreren met die onderdelen.
