package nl.han.cni.scriptopslag;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory opslag; thread-safe voor gelijktijdige TCP-connecties. */
public final class ScriptStore {
    private final Map<String, String> scripts = new ConcurrentHashMap<>();

    public void put(String name, String text) {
        scripts.put(name, text);
    }

    public String get(String name) {
        return scripts.get(name);
    }

    public Map<String, String> snapshot() {
        return new TreeMap<>(scripts);
    }
}
