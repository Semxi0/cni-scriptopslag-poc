
package nl.han.cni.scriptopslag;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataFragmentationTest {

    @Test
    void splitAndJoinShouldRestoreOriginalScript() {
        byte[] original = "MOVE_FORWARD"
                .getBytes(StandardCharsets.UTF_8);

        List<byte[]> parts = DataFragmentation.split(original, 3);
        byte[] restored = DataFragmentation.join(parts);

        assertEquals(3, parts.size());
        assertArrayEquals(original, restored);
    }

    @Test
    void splitShouldDistributeBytesAcrossThreeParts() {
        byte[] original = "MOVE_FORWARD"
                .getBytes(StandardCharsets.UTF_8);

        List<byte[]> parts = DataFragmentation.split(original, 3);

        assertArrayEquals("MOVE".getBytes(StandardCharsets.UTF_8), parts.get(0));
        assertArrayEquals("_FOR".getBytes(StandardCharsets.UTF_8), parts.get(1));
        assertArrayEquals("WARD".getBytes(StandardCharsets.UTF_8), parts.get(2));
    }

    @Test
    void joinShouldFailWhenPartIsMissing() {
        byte[] original = "MOVE_FORWARD"
                .getBytes(StandardCharsets.UTF_8);

        List<byte[]> parts = DataFragmentation.split(original, 3);

        // Simuleer dat het tweede deel ontbreekt.
        parts.set(1, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> DataFragmentation.join(parts)
        );
    }

    @Test
    void splitShouldRejectInvalidNumberOfParts() {
        byte[] original = "MOVE_FORWARD"
                .getBytes(StandardCharsets.UTF_8);

        assertThrows(
                IllegalArgumentException.class,
                () -> DataFragmentation.split(original, 0)
        );
    }
}
