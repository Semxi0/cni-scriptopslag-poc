
package nl.han.cni.scriptopslag;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class ErasureCoding {

    // Verdeel het script in 3 datashards en 2 herstelshards.
    public static final int DATA_SHARDS = 3;
    public static final int PARITY_SHARDS = 2;
    public static final int TOTAL_SHARDS = DATA_SHARDS + PARITY_SHARDS;

    // Bepaalt hoe de datashards worden gebruikt om herstelshards te maken.
    private static final int[][] MATRIX = {
            {1, 0, 0},
            {0, 1, 0},
            {0, 0, 1},
            {1, 1, 1},
            {1, 2, 4}
    };

    private ErasureCoding() {
    }

    // Verdeel een script in vijf shards.
    public static byte[][] encode(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data ontbreekt");
        }

        // Bewaar de oorspronkelijke lengte in de eerste 4 bytes.
        byte[] payload = ByteBuffer.allocate(data.length + 4)
                .putInt(data.length)
                .put(data)
                .array();

        // Bereken de grootte van iedere shard, afgerond naar boven.
        int shardSize = (payload.length + DATA_SHARDS - 1) / DATA_SHARDS;

        byte[][] shards = new byte[TOTAL_SHARDS][shardSize];

        // Verdeel de originele gegevens over de eerste drie shards.
        for (int i = 0; i < payload.length; i++) {
            shards[i / shardSize][i % shardSize] = payload[i];
        }

        // Bereken de twee herstelshards met Reed-Solomon.
        for (int shard = DATA_SHARDS; shard < TOTAL_SHARDS; shard++) {
            for (int byteIndex = 0; byteIndex < shardSize; byteIndex++) {
                int value = 0;

                for (int dataShard = 0; dataShard < DATA_SHARDS; dataShard++) {
                    value ^= multiply(
                            MATRIX[shard][dataShard],
                            shards[dataShard][byteIndex] & 0xFF
                    );
                }

                shards[shard][byteIndex] = (byte) value;
            }
        }

        return shards;
    }

    // Herstel het oorspronkelijke script uit minimaal drie shards.
    public static byte[] decode(byte[][] shards) {
        if (shards == null || shards.length != TOTAL_SHARDS) {
            throw new IllegalArgumentException("Er zijn vijf posities nodig");
        }

        int[] availableShards = new int[DATA_SHARDS];
        int availableCount = 0;
        int shardSize = -1;

        // Zoek beschikbare shards en controleer hun grootte.
        for (int i = 0; i < TOTAL_SHARDS; i++) {
            if (shards[i] != null) {
                if (shardSize == -1) {
                    shardSize = shards[i].length;
                } else if (shards[i].length != shardSize) {
                    throw new IllegalArgumentException("Shardgroottes verschillen");
                }

                if (availableCount < DATA_SHARDS) {
                    availableShards[availableCount] = i;
                }

                availableCount++;
            }
        }

        if (availableCount < DATA_SHARDS) {
            throw new IllegalArgumentException(
                    "Onvoldoende shards: minimaal drie nodig"
            );
        }

        // Maak een matrix met de drie geselecteerde shards.
        int[][] selectedMatrix = new int[DATA_SHARDS][DATA_SHARDS];

        for (int i = 0; i < DATA_SHARDS; i++) {
            selectedMatrix[i] = MATRIX[availableShards[i]].clone();
        }

        // De inverse matrix maakt het mogelijk de originele data te herstellen.
        int[][] inverseMatrix = invert(selectedMatrix);
        byte[][] restoredShards = new byte[DATA_SHARDS][shardSize];

        // Bereken de oorspronkelijke drie datashards.
        for (int dataShard = 0; dataShard < DATA_SHARDS; dataShard++) {
            for (int byteIndex = 0; byteIndex < shardSize; byteIndex++) {
                int value = 0;

                for (int i = 0; i < DATA_SHARDS; i++) {
                    value ^= multiply(
                            inverseMatrix[dataShard][i],
                            shards[availableShards[i]][byteIndex] & 0xFF
                    );
                }

                restoredShards[dataShard][byteIndex] = (byte) value;
            }
        }

        // Voeg de herstelde datashards samen.
        byte[] payload = new byte[DATA_SHARDS * shardSize];

        for (int i = 0; i < DATA_SHARDS; i++) {
            System.arraycopy(
                    restoredShards[i], 0,
                    payload, i * shardSize,
                    shardSize
            );
        }

        // Verwijder de extra bytes en geef het oorspronkelijke script terug.
        int originalLength = ByteBuffer.wrap(payload).getInt();

        if (originalLength < 0 || originalLength > payload.length - 4) {
            throw new IllegalArgumentException("Ongeldige oorspronkelijke lengte");
        }

        return Arrays.copyOfRange(payload, 4, 4 + originalLength);
    }

    // Bereken de inverse van een matrix om ontbrekende data te herstellen.
    private static int[][] invert(int[][] input) {
        int size = input.length;
        int[][] matrix = new int[size][2 * size];

        // Plaats de originele matrix naast een identiteitsmatrix.
        for (int i = 0; i < size; i++) {
            System.arraycopy(input[i], 0, matrix[i], 0, size);
            matrix[i][size + i] = 1;
        }

        // Maak de linkerhelft een identiteitsmatrix.
        for (int column = 0; column < size; column++) {
            int pivot = column;

            while (pivot < size && matrix[pivot][column] == 0) {
                pivot++;
            }

            if (pivot == size) {
                throw new IllegalArgumentException(
                        "Shards kunnen niet worden hersteld"
                );
            }

            // Wissel de rijen om als dat nodig is.
            int[] temporaryRow = matrix[column];
            matrix[column] = matrix[pivot];
            matrix[pivot] = temporaryRow;

            // Maak de waarde op de diagonaal gelijk aan 1.
            int inversePivot = divide(1, matrix[column][column]);

            for (int i = 0; i < 2 * size; i++) {
                matrix[column][i] = multiply(
                        matrix[column][i], inversePivot
                );
            }

            // Maak de overige waarden in deze kolom gelijk aan 0.
            for (int row = 0; row < size; row++) {
                if (row == column) {
                    continue;
                }

                int factor = matrix[row][column];

                for (int i = 0; i < 2 * size; i++) {
                    matrix[row][i] ^= multiply(
                            factor, matrix[column][i]
                    );
                }
            }
        }

        // De rechterhelft bevat nu de inverse matrix.
        int[][] inverse = new int[size][size];

        for (int i = 0; i < size; i++) {
            System.arraycopy(matrix[i], size, inverse[i], 0, size);
        }

        return inverse;
    }

    // Vermenigvuldigen met de rekenregels van GF(256).
    private static int multiply(int a, int b) {
        int result = 0;

        while (b != 0) {
            if ((b & 1) != 0) {
                result ^= a;
            }

            a <<= 1;

            if ((a & 0x100) != 0) {
                a ^= 0x11D;
            }

            b >>= 1;
        }

        return result;
    }

    // Deel twee getallen volgens de rekenregels van GF(256).
    private static int divide(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Delen door nul");
        }

        for (int i = 1; i < 256; i++) {
            if (multiply(b, i) == a) {
                return i;
            }
        }

        throw new ArithmeticException("Geen inverse gevonden");
    }
}
