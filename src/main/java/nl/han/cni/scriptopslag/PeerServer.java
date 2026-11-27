
package nl.han.cni.scriptopslag;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;

/** TCP-server die scripts en scriptdelen opslaat en beschikbaar maakt voor andere peers. */
public final class PeerServer {

    private final String id;
    private final int port;
    private final List<String> peers;
    private final ScriptStore store = new ScriptStore();

    private PeerServer(String id, int port, List<String> peers) {
        this.id = id;
        this.port = port;
        this.peers = peers;
    }

    public static void main(String[] args) throws IOException {
        // Lees de instellingen van de Docker-container.
        String id = System.getenv().getOrDefault("PEER_ID", "peer-local");
        int port = Integer.parseInt(
                System.getenv().getOrDefault("PORT", "7000")
        );

        String configuredPeers = System.getenv().getOrDefault("PEERS", "");
        List<String> peers = new ArrayList<>();

        // Zet de opgegeven peers om naar een lijst.
        if (!configuredPeers.isBlank()) {
            for (String peer : configuredPeers.split(",")) {
                if (!peer.isBlank()) {
                    peers.add(peer.trim());
                }
            }
        }

        new PeerServer(id, port, peers).run();
    }

    // Start de server en wacht op verbindingen van clients.
    private void run() throws IOException {
        try (ServerSocket server = new ServerSocket(port);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            System.out.printf(
                    "[%s] TCP server on port %d; peers=%s%n",
                    id, port, peers
            );

            while (true) {
                Socket socket = server.accept();

                // Iedere verbinding wordt in een aparte thread verwerkt.
                executor.submit(() -> handle(socket));
            }
        }
    }

    // Verwerk een binnengekomen TCP-bericht.
    private void handle(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(
                             socket.getInputStream(),
                             StandardCharsets.UTF_8
                     )
             );
             PrintWriter out = new PrintWriter(
                     socket.getOutputStream(),
                     true,
                     StandardCharsets.UTF_8
             )) {

            socket.setSoTimeout(5000);

            String line = in.readLine();

            if (line == null || line.length() > 2_000_000) {
                out.println("ERROR|invalid request size");
                return;
            }

            // Verdeel het bericht in onderdelen, gescheiden door '|'.
            String[] parts = line.split("\\|", 5);

            switch (parts[0]) {

                case "PING" -> {
                    out.println("PONG|" + id);
                }

                case "LIST" -> {
                    String scriptNames = String.join(
                            ",", store.snapshot().keySet()
                    );

                    out.println("LIST|" + scriptNames);
                }

                case "STATS" -> {
                    int scriptCount = store.snapshot().size();
                    long storedBytes = store.storedPayloadBytes();

                    out.println(
                            "STATS|scripts=" + scriptCount
                                    + "|payload-bytes=" + storedBytes
                    );
                }

                // Haal een volledig script op.
                case "GET" -> {
                    if (parts.length != 2 || !validName(parts[1])) {
                        out.println("ERROR|GET requires valid name");
                        break;
                    }

                    ScriptData data = store.get(parts[1]);

                    if (data == null) {
                        out.println("NOT_FOUND");
                    } else {
                        out.println(
                                "VALUE|" + data.version()
                                        + "|" + encode(data.content())
                        );
                    }
                }

                // PUT verspreidt een script; REPL bewaart alleen een kopie.
                case "PUT", "REPL" -> {
                    if (parts.length != 4 || !validName(parts[1])) {
                        out.println("ERROR|PUT/REPL requires name, version, bytes");
                        break;
                    }

                    String name = parts[1];
                    int version = Integer.parseInt(parts[2]);
                    byte[] content = decode(parts[3]);

                    ScriptData data = new ScriptData(version, content);

                    boolean accepted = store.put(name, data);

                    if (!accepted) {
                        out.println("ERROR|stale version");
                        break;
                    }

                    System.out.printf(
                            "[%s] %s script=%s version=%d bytes=%d%n",
                            id, parts[0], name, version, content.length
                    );

                    if (parts[0].equals("PUT")) {
                        // Verstuur kopieën naar de ingestelde peers.
                        int copies = replicate(name, data);

                        out.println(
                                "OK|stored-locally|replicated-to="
                                        + copies + "/" + peers.size()
                        );
                    } else {
                        out.println("OK|replica-stored");
                    }
                }

                // Sla één fragment of shard op.
                case "FPUT" -> {
                    if (parts.length != 5 || !validName(parts[1])) {
                        out.println("ERROR|invalid fragment");
                        break;
                    }

                    String name = parts[1];
                    int version = Integer.parseInt(parts[2]);
                    int index = Integer.parseInt(parts[3]);
                    byte[] content = decode(parts[4]);

                    store.putFragment(name, version, index, content);

                    out.println("OK|fragment-stored|index=" + index);
                }

                // Haal één fragment of shard op.
                case "FGET" -> {
                    if (parts.length != 4 || !validName(parts[1])) {
                        out.println("ERROR|invalid fragment request");
                        break;
                    }

                    String name = parts[1];
                    int version = Integer.parseInt(parts[2]);
                    int index = Integer.parseInt(parts[3]);

                    byte[] fragment = store.getFragment(
                            name, version, index
                    );

                    if (fragment == null) {
                        out.println("NOT_FOUND");
                    } else {
                        out.println(
                                "FRAGMENT|" + index
                                        + "|" + encode(fragment)
                        );
                    }
                }

                default -> {
                    out.println("ERROR|unknown command");
                }
            }

        } catch (Exception ex) {
            System.err.printf(
                    "[%s] request error: %s%n",
                    id, ex.getMessage()
            );
        }
    }

    // Controleer of een scriptnaam geldige tekens bevat.
    private static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_.-]{1,80}");
    }

    // Verspreid een volledige kopie naar alle ingestelde peers.
    private int replicate(String name, ScriptData data) {
        int successfulCopies = 0;

        for (String peer : peers) {
            String[] hostAndPort = peer.split(":", 2);

            String host = hostAndPort[0];
            int peerPort = Integer.parseInt(hostAndPort[1]);

            String message = "REPL|" + name
                    + "|" + data.version()
                    + "|" + encode(data.content());

            try {
                String response = request(host, peerPort, message);

                if (response.startsWith("OK|")) {
                    successfulCopies++;
                }

            } catch (Exception ex) {
                System.err.printf(
                        "[%s] replication to %s failed: %s%n",
                        id, peer, ex.getMessage()
                );
            }
        }

        return successfulCopies;
    }

    // Stuur een TCP-bericht naar een peer en wacht op het antwoord.
    static String request(String host, int port, String message)
            throws IOException {

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(3000);

            try (PrintWriter out = new PrintWriter(
                    socket.getOutputStream(),
                    true,
                    StandardCharsets.UTF_8
            );
                 BufferedReader in = new BufferedReader(
                         new InputStreamReader(
                                 socket.getInputStream(),
                                 StandardCharsets.UTF_8
                         )
                 )) {

                out.println(message);

                String response = in.readLine();

                if (response == null) {
                    throw new IOException("empty response");
                }

                return response;
            }
        }
    }

    // Zet bytes om naar Base64, zodat ze in een tekstbericht passen.
    static String encode(byte[] content) {
        return Base64.getEncoder().encodeToString(content);
    }

    // Zet Base64 terug naar de oorspronkelijke bytes.
    static byte[] decode(String encoded) {
        return Base64.getDecoder().decode(encoded);
    }
}
