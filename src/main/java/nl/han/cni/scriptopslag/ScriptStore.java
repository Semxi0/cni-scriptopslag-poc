
package nl.han.cni.scriptopslag;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptStore {

    // Bestaande opslag voor volledige scripts (Full Replication).
    private final Map<String, ScriptData> scripts = new ConcurrentHashMap<>();

    // Nieuwe opslag voor losse scriptdelen (Data Fragmentation).
    private final Map<String, byte[]> fragments = new ConcurrentHashMap<>();

    public boolean put(String name, ScriptData data) {
        validateName(name);

        final boolean[] accepted = {false};

        scripts.compute(name, (key, current) -> {
            if (current != null && current.version() > data.version()) {
                return current;
            }

            accepted[0] = true;
            return data;
        });

        return accepted[0];
    }

    public ScriptData get(String name) {
        return scripts.get(name);
    }

    public Map<String, ScriptData> snapshot() {
        return new TreeMap<>(scripts);
    }

    // Sla één uniek scriptdeel op.
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

        return fragment == null
                ? null
                : Arrays.copyOf(fragment, fragment.length);
    }

    // Totale grootte van volledige scripts en fragments.
    public long storedPayloadBytes() {
        long scriptBytes = scripts.values()
                .stream()
                .mapToLong(ScriptData::size)
                .sum();

        long fragmentBytes = fragments.values()
                .stream()
                .mapToLong(fragment -> fragment.length)
                .sum();

        return scriptBytes + fragmentBytes;
    }

    private String fragmentKey(String name, int version, int index) {
        return name + ":" + version + ":" + index;
    }

    private void validateName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,80}")) {
            throw new IllegalArgumentException("Ongeldige scriptnaam");
        }
    }
}
