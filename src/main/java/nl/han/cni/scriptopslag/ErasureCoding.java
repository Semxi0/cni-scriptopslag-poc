
package nl.han.cni.scriptopslag;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class ErasureCoding {

    public static final int DATA_SHARDS = 3;
    public static final int PARITY_SHARDS = 2;
    public static final int TOTAL_SHARDS = 5;

    // Drie originele delen en twee Reed-Solomon-herstelstukken.
    private static final int[][] MATRIX = {
            {1, 0, 0},
            {0, 1, 0},
            {0, 0, 1},
            {1, 1, 1},
            {1, 2, 4}
    };

    private ErasureCoding() {
    }

    public static byte[][] encode(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data ontbreekt");
        }

        // De eerste vier bytes bewaren de oorspronkelijke lengte.
        byte[] payload = ByteBuffer.allocate(data.length + 4)
                .putInt(data.length)
                .put(data)
                .array();

        int shardSize = (payload.length + DATA_SHARDS - 1)
                / DATA_SHARDS;

        byte[][] shards = new byte[TOTAL_SHARDS][shardSize];

        for (int i = 0; i < payload.length; i++) {
            shards[i / shardSize][i % shardSize] = payload[i];
        }

        // Bereken twee parity shards.
        for (int i = DATA_SHARDS; i < TOTAL_SHARDS; i++) {
            for (int b = 0; b < shardSize; b++) {
                int value = 0;

                for (int d = 0; d < DATA_SHARDS; d++) {
                    value ^= multiply(MATRIX[i][d],
                            shards[d][b] & 0xFF);
                }

                shards[i][b] = (byte) value;
            }
        }

        return shards;
    }

    public static byte[] decode(byte[][] shards) {
        if (shards == null || shards.length != TOTAL_SHARDS) {
            throw new IllegalArgumentException("Er zijn vijf posities nodig");
        }

        int[] available = new int[DATA_SHARDS];
        int count = 0;
        int shardSize = -1;

        for (int i = 0; i < TOTAL_SHARDS; i++) {
            if (shards[i] != null) {
                if (shardSize == -1) {
                    shardSize = shards[i].length;
                } else if (shards[i].length != shardSize) {
                    throw new IllegalArgumentException(
                            "Shardgroottes verschillen");
                }

                if (count < DATA_SHARDS) {
                    available[count] = i;
                }
                count++;
            }
        }

        if (count < DATA_SHARDS) {
            throw new IllegalArgumentException(
                    "Onvoldoende shards: minimaal drie nodig");
        }

        // Bepaal hoe de originele data uit drie shards te herstellen is.
        int[][] matrix = new int[DATA_SHARDS][DATA_SHARDS];

        for (int i = 0; i < DATA_SHARDS; i++) {
            matrix[i] = MATRIX[available[i]].clone();
        }

        int[][] inverse = invert(matrix);
        byte[][] restored = new byte[DATA_SHARDS][shardSize];

        for (int d = 0; d < DATA_SHARDS; d++) {
            for (int b = 0; b < shardSize; b++) {
                int value = 0;

                for (int j = 0; j < DATA_SHARDS; j++) {
                    value ^= multiply(inverse[d][j],
                            shards[available[j]][b] & 0xFF);
                }

                restored[d][b] = (byte) value;
            }
        }

        byte[] payload = new byte[DATA_SHARDS * shardSize];

        for (int i = 0; i < DATA_SHARDS; i++) {
            System.arraycopy(restored[i], 0, payload,
                    i * shardSize, shardSize);
        }

        int originalLength = ByteBuffer.wrap(payload).getInt();

        if (originalLength < 0 || originalLength > payload.length - 4) {
            throw new IllegalArgumentException(
                    "Ongeldige oorspronkelijke lengte");
        }

        return Arrays.copyOfRange(payload, 4, 4 + originalLength);
    }

    private static int[][] invert(int[][] input) {
        int n = input.length;
        int[][] a = new int[n][2 * n];

        for (int i = 0; i < n; i++) {
            System.arraycopy(input[i], 0, a[i], 0, n);
            a[i][n + i] = 1;
        }

        for (int col = 0; col < n; col++) {
            int pivot = col;

            while (pivot < n && a[pivot][col] == 0) {
                pivot++;
            }

            if (pivot == n) {
                throw new IllegalArgumentException(
                        "Shards kunnen niet worden hersteld");
            }

            int[] temp = a[col];
            a[col] = a[pivot];
            a[pivot] = temp;

            int inversePivot = divide(1, a[col][col]);

            for (int j = 0; j < 2 * n; j++) {
                a[col][j] = multiply(a[col][j], inversePivot);
            }

            for (int row = 0; row < n; row++) {
                if (row == col) continue;

                int factor = a[row][col];

                for (int j = 0; j < 2 * n; j++) {
                    a[row][j] ^= multiply(factor, a[col][j]);
                }
            }
        }

        int[][] inverse = new int[n][n];

        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], n, inverse[i], 0, n);
        }

        return inverse;
    }

    // Vermenigvuldigen in GF(256), met primitieve polynoom 0x11D.
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
