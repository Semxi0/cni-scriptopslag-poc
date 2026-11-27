package nl.han.cni.scriptopslag;

import java.util.Arrays;

/** Bewaart de inhoud en versie van een script. */
public record ScriptData(int version, byte[] content) {
    public ScriptData {
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        if (content == null) throw new IllegalArgumentException("content may not be null");
        content = Arrays.copyOf(content, content.length);
    }
    @Override public byte[] content() { return Arrays.copyOf(content, content.length); }
    public int size() { return content.length; }
}
