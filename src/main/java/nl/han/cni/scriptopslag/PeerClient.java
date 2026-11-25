
package nl.han.cni.scriptopslag;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PeerClient {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }

        if (args[0].equalsIgnoreCase("pput")) {
            partialPut(args);
            return;
        }

        if (args[0].equalsIgnoreCase("pget")) {
            partialGet(args);
            return;
        }

        if (args[0].equalsIgnoreCase("eput")) {
            erasurePut(args);
            return;
        }

        if (args[0].equalsIgnoreCase("eget")) {
            erasureGet(args);
            return;
        }

        if (args[0].equalsIgnoreCase("fsplit")) {
            splitAndDistribute(args);
            return;
        }

        if (args[0].equalsIgnoreCase("fjoin")) {
            fetchAndJoin(args);
            return;
        }

        String request;
        String host = "127.0.0.1";
        int port = Integer.parseInt(
                System.getenv().getOrDefault("PORT", "7000")
        );

        switch (args[0].toLowerCase()) {

            case "ping" -> request = "PING";

            case "list" -> request = "LIST";

            case "stats" -> request = "STATS";

            case "get" -> {
                if (args.length != 2) {
                    usage();
                    return;
                }

                request = "GET|" + validName(args[1]);
            }

            case "put" -> {
                if (args.length != 4) {
                    usage();
                    return;
                }

                int version = Integer.parseInt(args[2]);

                request = "PUT|" + validName(args[1])
                        + "|" + version
                        + "|" + PeerServer.encode(
                        args[3].getBytes(StandardCharsets.UTF_8)
                );
            }

            // Sla een fragment op bij een specifieke peer.
            case "fput" -> {
                if (args.length != 6) {
                    usage();
                    return;
                }

                host = args[1];
                String name = validName(args[2]);
                int version = Integer.parseInt(args[3]);
                int index = Integer.parseInt(args[4]);

                byte[] content = args[5].getBytes(StandardCharsets.UTF_8);

                request = "FPUT|" + name
                        + "|" + version
                        + "|" + index
                        + "|" + PeerServer.encode(content);
            }

            // Haal een fragment op bij een specifieke peer.
            case "fget" -> {
                if (args.length != 5) {
                    usage();
                    return;
                }

                host = args[1];
                String name = validName(args[2]);
                int version = Integer.parseInt(args[3]);
                int index = Integer.parseInt(args[4]);

                request = "FGET|" + name
                        + "|" + version
                        + "|" + index;
            }

            default -> {
                usage();
                return;
            }
        }

        String reply = PeerServer.request(host, port, request);

        if (reply.startsWith("VALUE|")) {
            String[] parts = reply.split("\\|", 3);

            System.out.println("version=" + parts[1]);
            System.out.println(
                    "content=" + new String(
                            PeerServer.decode(parts[2]),
                            StandardCharsets.UTF_8
                    )
            );

        } else if (reply.startsWith("FRAGMENT|")) {
            String[] parts = reply.split("\\|", 3);

            System.out.println("fragment-index=" + parts[1]);
            System.out.println(
                    "content=" + new String(
                            PeerServer.decode(parts[2]),
                            StandardCharsets.UTF_8
                    )
            );

        } else {
            System.out.println(reply);
        }

        if (reply.equals("NOT_FOUND") || reply.startsWith("ERROR|")) {
            System.exit(1);
        }
    }

    private static String validName(String name) {
        if (!name.matches("[A-Za-z0-9_.-]{1,80}")) {
            throw new IllegalArgumentException("Invalid script name");
        }
        return name;
    }


    private static final String[] FRAGMENT_PEERS = {
            "peer-a", "peer-b", "peer-c"
    };

    private static final int FRAGMENT_PORT = 7000;

    private static void splitAndDistribute(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Gebruik: fsplit <naam> <versie> <tekst>");
            return;
        }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);
        byte[] script = args[3].getBytes(StandardCharsets.UTF_8);

        List<byte[]> fragments =
                DataFragmentation.split(script, FRAGMENT_PEERS.length);

        for (int i = 0; i < fragments.size(); i++) {
            String request = "FPUT|" + name
                    + "|" + version
                    + "|" + i
                    + "|" + PeerServer.encode(fragments.get(i));

            String response = PeerServer.request(
                    FRAGMENT_PEERS[i], FRAGMENT_PORT, request
            );

            if (!response.startsWith("OK|")) {
                throw new IllegalStateException(
                        "Opslaan mislukt bij " + FRAGMENT_PEERS[i]
                                + ": " + response
                );
            }

            System.out.println(
                    FRAGMENT_PEERS[i] + ": fragment " + i + " opgeslagen"
            );
        }

        System.out.println("Alle fragmenten verdeeld.");
    }

    private static void fetchAndJoin(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Gebruik: fjoin <naam> <versie>");
            return;
        }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);

        List<byte[]> fragments = new java.util.ArrayList<>();

        for (int i = 0; i < FRAGMENT_PEERS.length; i++) {
            String request = "FGET|" + name
                    + "|" + version
                    + "|" + i;

            String response;

            try {
                response = PeerServer.request(
                        FRAGMENT_PEERS[i], FRAGMENT_PORT, request
                );
            } catch (java.io.IOException ex) {
                System.err.println(
                        "Reconstructie mislukt: " + FRAGMENT_PEERS[i]
                                + " is niet bereikbaar."
                );
                System.exit(1);
                return;
            }

            if (!response.startsWith("FRAGMENT|" + i + "|")) {
                System.err.println(
                        "Reconstructie mislukt: fragment " + i
                                + " ontbreekt."
                );
                System.exit(1);
                return;
            }

            String encoded = response.split("\\|", 3)[2];
            fragments.add(PeerServer.decode(encoded));
        }

        byte[] restored = DataFragmentation.join(fragments);

        System.out.println(
                "Hersteld script: "
                        + new String(restored, StandardCharsets.UTF_8)
        );
    }


    private static void partialPut(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Gebruik: pput <naam> <versie> <tekst>");
            return;
        }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);
        byte[] content = args[3].getBytes(StandardCharsets.UTF_8);

        List<String> selectedPeers = PartialReplication.selectPeers(
                name,
                List.of(FRAGMENT_PEERS),
                2
        );

        int stored = 0;

        for (String peer : selectedPeers) {
            String request = "REPL|" + name
                    + "|" + version
                    + "|" + PeerServer.encode(content);

            try {
                String response = PeerServer.request(
                        peer, FRAGMENT_PORT, request
                );

                if (response.startsWith("OK|")) {
                    stored++;
                    System.out.println(peer + ": script opgeslagen");
                } else {
                    System.out.println(peer + ": " + response);
                }
            } catch (java.io.IOException ex) {
                System.out.println(peer + ": niet bereikbaar");
            }
        }

        System.out.println(
                "Kopieën opgeslagen: " + stored + "/" + selectedPeers.size()
        );
    }

    private static void partialGet(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Gebruik: pget <naam>");
            return;
        }

        String name = validName(args[1]);

        List<String> selectedPeers = PartialReplication.selectPeers(
                name,
                List.of(FRAGMENT_PEERS),
                2
        );

        for (String peer : selectedPeers) {
            try {
                String response = PeerServer.request(
                        peer, FRAGMENT_PORT, "GET|" + name
                );

                if (response.startsWith("VALUE|")) {
                    String[] parts = response.split("\\|", 3);

                    System.out.println("Opgehaald van: " + peer);
                    System.out.println("version=" + parts[1]);
                    System.out.println(
                            "content=" + new String(
                                    PeerServer.decode(parts[2]),
                                    StandardCharsets.UTF_8
                            )
                    );
                    return;
                }
            } catch (java.io.IOException ex) {
                System.out.println(peer + ": niet bereikbaar");
            }
        }

        System.out.println("Script niet beschikbaar.");
    }


    private static final String[] ERASURE_PEERS = {
            "peer-a", "peer-b", "peer-c", "peer-d", "peer-e"
    };

    private static void erasurePut(String[] args) throws Exception {
        if (args.length != 4) {
            System.out.println("Gebruik: eput <naam> <versie> <tekst>");
            return;
        }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);
        byte[] original = args[3].getBytes(StandardCharsets.UTF_8);

        byte[][] shards = ErasureCoding.encode(original);
        int stored = 0;

        for (int i = 0; i < ERASURE_PEERS.length; i++) {
            String request = "FPUT|" + name + "|" + version
                    + "|" + i + "|" + PeerServer.encode(shards[i]);

            try {
                String response = PeerServer.request(
                        ERASURE_PEERS[i], FRAGMENT_PORT, request
                );

                if (response.startsWith("OK|")) {
                    stored++;
                    System.out.println(
                            ERASURE_PEERS[i] + ": shard " + i + " opgeslagen"
                    );
                }
            } catch (java.io.IOException ex) {
                System.out.println(
                        ERASURE_PEERS[i] + ": niet bereikbaar"
                );
            }
        }

        System.out.println("Shards opgeslagen: " + stored + "/5");
    }

    private static void erasureGet(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Gebruik: eget <naam> <versie>");
            return;
        }

        String name = validName(args[1]);
        int version = Integer.parseInt(args[2]);

        byte[][] shards = new byte[ErasureCoding.TOTAL_SHARDS][];
        int available = 0;

        for (int i = 0; i < ERASURE_PEERS.length; i++) {
            try {
                String response = PeerServer.request(
                        ERASURE_PEERS[i],
                        FRAGMENT_PORT,
                        "FGET|" + name + "|" + version + "|" + i
                );

                if (response.startsWith("FRAGMENT|" + i + "|")) {
                    shards[i] = PeerServer.decode(
                            response.split("\\|", 3)[2]
                    );
                    available++;
                }
            } catch (java.io.IOException ex) {
                System.out.println(
                        ERASURE_PEERS[i] + ": niet bereikbaar"
                );
            }
        }

        if (available < ErasureCoding.DATA_SHARDS) {
            System.out.println(
                    "Reconstructie mislukt: slechts "
                            + available + "/3 benodigde shards beschikbaar."
            );
            return;
        }

        byte[] restored = ErasureCoding.decode(shards);

        System.out.println("Beschikbare shards: " + available + "/5");
        System.out.println(
                "Hersteld script: "
                        + new String(restored, StandardCharsets.UTF_8)
        );
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
                """);
    }
}
