
package nl.han.cni.scriptopslag;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ErasureCodingTest {

    private final byte[] original =
            "MOVE_FORWARD;ATTACK_ENEMY;COLLECT_ITEM"
                    .getBytes(StandardCharsets.UTF_8);

    @Test
    void restoreWithAllFiveShards() {
        // Arrange
        byte[][] shards = ErasureCoding.encode(original);

        // Act
        byte[] restored = ErasureCoding.decode(shards);

        // Assert
        assertEquals(5, shards.length);
        assertArrayEquals(original, restored);
    }

    @Test
    void restoreWithAnyTwoMissingShards() {
        // Test alle 10 combinaties van 2 ontbrekende shards.
        for (int first = 0; first < 5; first++) {
            for (int second = first + 1; second < 5; second++) {

                // Arrange
                byte[][] shards = ErasureCoding.encode(original);
                shards[first] = null;
                shards[second] = null;

                // Act
                byte[] restored = ErasureCoding.decode(shards);

                // Assert
                assertArrayEquals(
                        original,
                        restored,
                        "Herstel mislukt bij ontbrekende shards "
                                + first + " en " + second
                );
            }
        }
    }

    @Test
    void restoreWithThreeMissingShardsShouldFail() {
        // Arrange
        byte[][] shards = ErasureCoding.encode(original);

        shards[0] = null;
        shards[1] = null;
        shards[2] = null;

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> ErasureCoding.decode(shards)
        );
    }

    @Test
    void restoredScriptShouldMatchOriginalBytes() {
        // Arrange
        byte[] binaryData = new byte[1000];

        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte) i;
        }

        byte[][] shards = ErasureCoding.encode(binaryData);

        shards[1] = null;
        shards[4] = null;

        // Act
        byte[] restored = ErasureCoding.decode(shards);

        // Assert
        assertArrayEquals(binaryData, restored);
    }
}
