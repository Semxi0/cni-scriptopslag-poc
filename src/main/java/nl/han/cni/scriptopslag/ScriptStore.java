package nl.han.cni.scriptopslag;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory opslag: bewaart alleen de hoogste bekende versie van een script. */
public final class ScriptStore {
    private final Map<String, ScriptData> scripts = new ConcurrentHashMap<>();

    /** @return true als deze versie is opgeslagen; false als een nieuwere versie bestaat. */
    public boolean put(String name, ScriptData data) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,80}"))
            throw new IllegalArgumentException("ongeldige scriptnaam");
        final boolean[] accepted = {false};
        scripts.compute(name, (key, current) -> {
            if (current != null && current.version() > data.version()) return current;
            accepted[0] = true;
            return data;
        });
        return accepted[0];
    }

    public ScriptData get(String name) { return scripts.get(name); }
    public Map<String, ScriptData> snapshot() { return new TreeMap<>(scripts); }
    public long storedPayloadBytes() { return scripts.values().stream().mapToLong(ScriptData::size).sum(); }
}
