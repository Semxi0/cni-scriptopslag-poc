
package nl.han.cni.scriptopslag;

import java.util.ArrayList;
import java.util.List;

public final class PartialReplication {

    private PartialReplication() {
    }

    // Kies op welke peers het script wordt opgeslagen.
    public static List<String> selectPeers(
            String scriptName,
            List<String> peers,
            int replicationFactor
    ) {
        if (scriptName == null || scriptName.isBlank()) {
            throw new IllegalArgumentException("Scriptnaam ontbreekt");
        }

        if (peers == null || peers.isEmpty()) {
            throw new IllegalArgumentException("Geen peers beschikbaar");
        }

        if (replicationFactor < 1 || replicationFactor > peers.size()) {
            throw new IllegalArgumentException("Ongeldige replication factor");
        }

        List<String> selectedPeers = new ArrayList<>();

        // Kies een vaste startpositie op basis van de scriptnaam.
        int startIndex = Math.floorMod(scriptName.hashCode(), peers.size());

        // Selecteer het gewenste aantal peers.
        for (int i = 0; i < replicationFactor; i++) {
            int peerIndex = (startIndex + i) % peers.size();
            selectedPeers.add(peers.get(peerIndex));
        }

        return selectedPeers;
    }
}
