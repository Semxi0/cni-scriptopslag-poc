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

/**
 * Onderzoeksprototype: Full Replication over echte TCP-socketverbindingen.
 * Dit is geen productieprotocol: geen auth, retries, versies of duurzame opslag.
 */
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
        String id = System.getenv().getOrDefault("PEER_ID", "peer-local");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "7000"));
        String configured = System.getenv().getOrDefault("PEERS", "");
        List<String> peers = new ArrayList<>();
        if (!configured.isBlank()) {
            for (String peer : configured.split(",")) {
                if (!peer.isBlank()) peers.add(peer.trim());
            }
        }
        new PeerServer(id, port, peers).run();
    }

    private void run() throws IOException {
        try (ServerSocket server = new ServerSocket(port);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.printf("[%s] TCP server op poort %d; peers=%s%n", id, port, peers);
            while (true) {
                Socket socket = server.accept();
                executor.submit(() -> handle(socket));
            }
        }
    }

    private void handle(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            socket.setSoTimeout(5000);
            String line = in.readLine();
            if (line == null) return;
            String[] parts = line.split("\\|", 3);
            String command = parts[0];
            switch (command) {
                case "PING" -> out.println("PONG|" + id);
                case "LIST" -> out.println("LIST|" + String.join(",", store.snapshot().keySet()));
                case "GET" -> {
                    if (parts.length != 2) { out.println("ERROR|GET vereist naam"); break; }
                    String script = store.get(parts[1]);
                    out.println(script == null ? "NOT_FOUND" : "VALUE|" + encode(script));
                }
                case "PUT", "REPL" -> {
                    if (parts.length != 3) { out.println("ERROR|PUT/REPL vereist naam en inhoud"); break; }
                    String script = decode(parts[2]);
                    store.put(parts[1], script);
                    System.out.printf("[%s] %s script=%s bytes=%d%n", id, command, parts[1], script.getBytes(StandardCharsets.UTF_8).length);
                    if (command.equals("PUT")) {
                        int success = replicate(parts[1], script);
                        out.println("OK|stored-locally|replicated-to=" + success + "/" + peers.size());
                    } else {
                        out.println("OK|replica-stored");
                    }
                }
                default -> out.println("ERROR|onbekend commando");
            }
        } catch (Exception e) {
            System.err.printf("[%s] TCP fout: %s%n", id, e.getMessage());
        }
    }

    private int replicate(String name, String text) {
        int count = 0;
        for (String peer : peers) {
            String[] hostPort = peer.split(":", 2);
            try {
                String response = request(hostPort[0], Integer.parseInt(hostPort[1]),
                        "REPL|" + name + "|" + encode(text));
                if (response.startsWith("OK|")) count++;
            } catch (Exception ex) {
                System.err.printf("[%s] replication naar %s mislukt: %s%n", id, peer, ex.getMessage());
            }
        }
        return count;
    }

    static String request(String host, int port, String line) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 1500);
            socket.setSoTimeout(3000);
            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                out.println(line);
                String response = in.readLine();
                if (response == null) throw new IOException("lege response");
                return response;
            }
        }
    }

    static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    static String decode(String encoded) {
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
