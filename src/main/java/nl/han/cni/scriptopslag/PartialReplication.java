
package nl.han.cni.scriptopslag;

import java.util.ArrayList;
import java.util.List;

public final class PartialReplication {

    private PartialReplication() {
    }

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

        List<String> selected = new ArrayList<>();

        // Bepaal een vaste startpositie op basis van de scriptnaam.
        int start = Math.floorMod(scriptName.hashCode(), peers.size());

        // Selecteer precies het gevraagde aantal peers.
        for (int i = 0; i < replicationFactor; i++) {
            int index = (start + i) % peers.size();
            selected.add(peers.get(index));
        }

        return selected;
    }
}
