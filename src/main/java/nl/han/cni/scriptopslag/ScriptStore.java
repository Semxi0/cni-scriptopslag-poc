
package nl.han.cni.scriptopslag;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptStore {

    // Opslag voor volledige scripts.
    private final Map<String, ScriptData> scripts = new ConcurrentHashMap<>();

    // Opslag voor losse scriptdelen.
    private final Map<String, byte[]> fragments = new ConcurrentHashMap<>();

    // Sla een script op, tenzij er al een nieuwere versie bestaat.
    public synchronized boolean put(String name, ScriptData data) {
        validateName(name);

        ScriptData current = scripts.get(name);

        if (current != null && current.version() > data.version()) {
            return false;
        }

        scripts.put(name, data);
        return true;
    }

    // Haal een volledig script op.
    public ScriptData get(String name) {
        return scripts.get(name);
    }

    // Geef een overzicht van alle volledige scripts.
    public Map<String, ScriptData> snapshot() {
        return new TreeMap<>(scripts);
    }

    // Sla één scriptdeel op.
    public void putFragment(String name, int version,
                            int index, byte[] content) {

        validateName(name);

        if (version < 1 || index < 0 || content == null) {
            throw new IllegalArgumentException("Ongeldige fragmentgegevens");
        }

        String key = fragmentKey(name, version, index);

        fragments.put(key, Arrays.copyOf(content, content.length));
    }

    // Haal één scriptdeel op.
    public byte[] getFragment(String name, int version, int index) {
        String key = fragmentKey(name, version, index);
        byte[] fragment = fragments.get(key);

        if (fragment == null) {
            return null;
        }

        return Arrays.copyOf(fragment, fragment.length);
    }

    // Bereken hoeveel bytes alle opgeslagen scripts en fragmenten gebruiken.
    public long storedPayloadBytes() {
        long total = 0;

        for (ScriptData script : scripts.values()) {
            total += script.size();
        }

        for (byte[] fragment : fragments.values()) {
            total += fragment.length;
        }

        return total;
    }

    // Maak een unieke sleutel voor ieder scriptdeel.
    private String fragmentKey(String name, int version, int index) {
        return name + ":" + version + ":" + index;
    }

    // Controleer of de scriptnaam geldig is.
    private void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,80}")) {
            throw new IllegalArgumentException("Ongeldige scriptnaam");
        }
    }
}
