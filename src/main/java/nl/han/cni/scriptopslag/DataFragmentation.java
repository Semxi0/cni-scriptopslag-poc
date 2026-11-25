
package nl.han.cni.scriptopslag;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class DataFragmentation {

    public static List<byte[]> split(byte[] data, int numberOfParts) {
        if (data == null) {
            throw new IllegalArgumentException("Data mag niet null zijn");
        }

        if (numberOfParts <= 0) {
            throw new IllegalArgumentException("Aantal delen moet groter zijn dan 0");
        }

        List<byte[]> parts = new ArrayList<>();

        int baseSize = data.length / numberOfParts;
        int remainder = data.length % numberOfParts;
        int offset = 0;

        for (int i = 0; i < numberOfParts; i++) {
            int partSize = baseSize + (i < remainder ? 1 : 0);

            byte[] part = new byte[partSize];
            System.arraycopy(data, offset, part, 0, partSize);

            parts.add(part);
            offset += partSize;
        }

        return parts;
    }

    public static byte[] join(List<byte[]> parts) {
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("Er moeten delen aanwezig zijn");
        }

        ByteArrayOutputStream result = new ByteArrayOutputStream();

        for (byte[] part : parts) {
            if (part == null) {
                throw new IllegalArgumentException("Een scriptdeel ontbreekt");
            }

            result.writeBytes(part);
        }

        return result.toByteArray();
    }
}
