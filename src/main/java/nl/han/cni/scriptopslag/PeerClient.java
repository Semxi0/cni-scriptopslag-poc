package nl.han.cni.scriptopslag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Voert testcommando's uit voor de vier opslagmethoden. */
public final class PeerClient {

    private static final int PORT = 7000;
    private static final String[] PEERS_3 = {"peer-a", "peer-b", "peer-c"};
    private static final String[] PEERS_5 = {"peer-a", "peer-b", "peer-c", "peer-d", "peer-e"};

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }

        // Meet de bewerking binnen Java, zonder de opstarttijd van Java en Docker.
        if (args[0].equalsIgnoreCase("time")) {
            if (args.length < 2) {
                usage();
                return;
            }
            String[] command = Arrays.copyOfRange(args, 1, args.length);
            long start = System.nanoTime();
            main(command);
            double milliseconds = (System.nanoTime() - start) / 1_000_000.0;
            System.out.printf("Uitvoeringstijd: %.3f ms%n", milliseconds);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "ping", "list", "stats", "get", "put", "fput", "fget" -> basicCommand(args);
            case "fsplit" -> splitAndDistribute(args);
            case "fjoin" -> fetchAndJoin(args);
            case "pput" -> partialPut(args);
            case "pget" -> partialGet(args);
            case "eput" -> erasurePut(args);
            case "eget" -> erasureGet(args);
            default -> usage();
        }
    }

    // Rechtstreekse opdrachten voor één peer.
    private static void basicCommand(String[] args) throws IOException {
        String command = args[0].toLowerCase();
        String host = "127.0.0.1";
        String request;

        switch (command) {
            case "ping" -> request = "PING";
            case "list" -> request = "LIST";
            case "stats" -> request = "STATS";
            case "get" -> {
                if (args.length != 2) { usage(); return; }
                request = "GET|" + validName(args[1]);
            }
            case "put" -> {
                if (args.length != 4) { usage(); return; }
                request = "PUT|" + validName(args[1]) + "|" + args[2]
                        + "|" + encodeText(args[3]);
            }
            case "fput" -> {
                if (args.length != 6) { usage(); return; }
                host = args[1];
                request = "FPUT|" + validName(args[2]) + "|" + args[3]
                        + "|" + args[4] + "|" + encodeText(args[5]);
            }
            case "fget" -> {
                if (args.length != 5) { usage(); return; }
                host = args[1];
                request = "FGET|" + validName(args[2]) + "|" + args[3]
                        + "|" + args[4];
            }
            default -> { return; }
        }

        // Dit commando gebruikt dezelfde poortinstelling als de bestaande client.
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7000"));
        String response = PeerServer.request(host, port, request);
        printResponse(response);

        if (response.equals("NOT_FOUND") || response.startsWith("ERROR|")) {
            System.exit(1);
        }
    }

    // Print de inhoud van een volledig script of één fragment.
    private static void printResponse(String response) {
        if (response.startsWith("VALUE|")) {
            String[] parts = response.split("\\|", 3);
            System.out.println("version=" + parts[1]);
            System.out.println("content=" + decodeText(parts[2]));
        } else if (response.startsWith("FRAGMENT|")) {
            String[] parts = response.split("\\|", 3);
            System.out.println("fragment-index=" + parts[1]);
            System.out.println("content=" + decodeText(parts[2]));
        } else {
            System.out.println(response);
        }
    }

    // Data Fragmentation: verdeel het script over drie peers.
    private static void splitAndDistribute(String[] args) throws IOException {
        if (args.length != 4) { usage(); return; }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);
        List<byte[]> fragments = DataFragmentation.split(
                args[3].getBytes(StandardCharsets.UTF_8), PEERS_3.length);

        for (int i = 0; i < PEERS_3.length; i++) {
            String response = sendFragment(PEERS_3[i], name, version, i, fragments.get(i));
            if (!response.startsWith("OK|")) {
                throw new IllegalStateException("Opslaan mislukt bij " + PEERS_3[i] + ": " + response);
            }
            System.out.println(PEERS_3[i] + ": fragment " + i + " opgeslagen");
        }
        System.out.println("Alle fragmenten verdeeld.");
    }

    // Data Fragmentation: haal alle drie fragmenten op en voeg ze samen.
    private static void fetchAndJoin(String[] args) throws IOException {
        if (args.length != 3) { usage(); return; }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);
        List<byte[]> fragments = new ArrayList<>();

        for (int i = 0; i < PEERS_3.length; i++) {
            String response;
            try {
                response = getFragment(PEERS_3[i], name, version, i);
            } catch (IOException ex) {
                System.err.println("Reconstructie mislukt: " + PEERS_3[i] + " is niet bereikbaar.");
                System.exit(1);
                return;
            }

            if (!response.startsWith("FRAGMENT|" + i + "|")) {
                System.err.println("Reconstructie mislukt: fragment " + i + " ontbreekt.");
                System.exit(1);
                return;
            }
            fragments.add(decodeFragment(response));
        }

        System.out.println("Hersteld script: " + new String(
                DataFragmentation.join(fragments), StandardCharsets.UTF_8));
    }

    // Partial Replication: sla een volledige kopie op bij twee gekozen peers.
    private static void partialPut(String[] args) throws IOException {
        if (args.length != 4) { usage(); return; }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);
        List<String> selected = PartialReplication.selectPeers(name, List.of(PEERS_3), 2);
        String request = "REPL|" + name + "|" + version + "|" + encodeText(args[3]);
        int stored = 0;

        for (String peer : selected) {
            try {
                String response = send(peer, request);
                if (response.startsWith("OK|")) {
                    stored++;
                    System.out.println(peer + ": script opgeslagen");
                } else {
                    System.out.println(peer + ": " + response);
                }
            } catch (IOException ex) {
                System.out.println(peer + ": niet bereikbaar");
            }
        }
        System.out.println("Kopieën opgeslagen: " + stored + "/" + selected.size());
    }

    // Partial Replication: probeer de geselecteerde peers totdat een kopie is gevonden.
    private static void partialGet(String[] args) throws IOException {
        if (args.length != 2) { usage(); return; }

        String name = validName(args[1]);
        List<String> selected = PartialReplication.selectPeers(name, List.of(PEERS_3), 2);

        for (String peer : selected) {
            try {
                String response = send(peer, "GET|" + name);
                if (response.startsWith("VALUE|")) {
                    System.out.println("Opgehaald van: " + peer);
                    printResponse(response);
                    return;
                }
            } catch (IOException ex) {
                System.out.println(peer + ": niet bereikbaar");
            }
        }
        System.out.println("Script niet beschikbaar.");
    }

    // Erasure Coding: maak vijf shards en stuur één shard naar iedere peer.
    private static void erasurePut(String[] args) throws IOException {
        if (args.length != 4) { usage(); return; }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);
        byte[][] shards = ErasureCoding.encode(args[3].getBytes(StandardCharsets.UTF_8));
        int stored = 0;

        for (int i = 0; i < PEERS_5.length; i++) {
            try {
                String response = sendFragment(PEERS_5[i], name, version, i, shards[i]);
                if (response.startsWith("OK|")) {
                    stored++;
                    System.out.println(PEERS_5[i] + ": shard " + i + " opgeslagen");
                }
            } catch (IOException ex) {
                System.out.println(PEERS_5[i] + ": niet bereikbaar");
            }
        }
        System.out.println("Shards opgeslagen: " + stored + "/5");
    }

    // Erasure Coding: haal beschikbare shards op en herstel het script.
    private static void erasureGet(String[] args) throws IOException {
        if (args.length != 3) { usage(); return; }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);
        byte[][] shards = new byte[ErasureCoding.TOTAL_SHARDS][];
        int available = 0;

        for (int i = 0; i < PEERS_5.length; i++) {
            try {
                String response = getFragment(PEERS_5[i], name, version, i);
                if (response.startsWith("FRAGMENT|" + i + "|")) {
                    shards[i] = decodeFragment(response);
                    available++;
                }
            } catch (IOException ex) {
                System.out.println(PEERS_5[i] + ": niet bereikbaar");
            }
        }

        if (available < ErasureCoding.DATA_SHARDS) {
            System.out.println("Reconstructie mislukt: slechts " + available
                    + "/3 benodigde shards beschikbaar.");
            return;
        }

        byte[] restored = ErasureCoding.decode(shards);
        System.out.println("Beschikbare shards: " + available + "/5");
        System.out.println("Hersteld script: " + new String(restored, StandardCharsets.UTF_8));
    }

    // Deze drie helpers voorkomen dat ieder opslagcommando TCP-berichten opnieuw opbouwt.
    private static String send(String peer, String request) throws IOException {
        return PeerServer.request(peer, PORT, request);
    }

    private static String sendFragment(String peer, String name, int version,
                                       int index, byte[] bytes) throws IOException {
        return send(peer, "FPUT|" + name + "|" + version + "|" + index
                + "|" + PeerServer.encode(bytes));
    }

    private static String getFragment(String peer, String name, int version,
                                      int index) throws IOException {
        return send(peer, "FGET|" + name + "|" + version + "|" + index);
    }

    private static byte[] decodeFragment(String response) {
        return PeerServer.decode(response.split("\\|", 3)[2]);
    }

    private static String encodeText(String text) {
        return PeerServer.encode(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeText(String encoded) {
        return new String(PeerServer.decode(encoded), StandardCharsets.UTF_8);
    }

    private static String validName(String name) {
        if (!name.matches("[A-Za-z0-9_.-]{1,80}")) {
            throw new IllegalArgumentException("Ongeldige scriptnaam");
        }
        return name;
    }

    private static void usage() {
        System.out.println("""
                Commands:
                  ping
                  list
                  stats
                  get <name>
                  put <name> <version> <text>
                  fput <host> <name> <version> <index> <text>
                  fget <host> <name> <version> <index>
                  fsplit <naam> <versie> <tekst>
                  fjoin <naam> <versie>
                  pput <naam> <versie> <tekst>
                  pget <naam>
                  eput <naam> <versie> <tekst>
                  eget <naam> <versie>
                  time <commando> <argumenten>
                """);
    }
}
