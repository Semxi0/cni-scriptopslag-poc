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

/** Onderzoeksprototype: Full Replication over TCP. Geen productiebeveiliging of persistente opslag. */
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
            System.out.printf("[%s] TCP server on port %d; peers=%s%n", id, port, peers);
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
            if (line == null || line.length() > 2_000_000) { out.println("ERROR|invalid request size"); return; }
            String[] parts = line.split("\\|", 5);
            switch (parts[0]) {
                case "PING" -> out.println("PONG|" + id);
                case "LIST" -> out.println("LIST|" + String.join(",", store.snapshot().keySet()));
                case "STATS" -> out.println("STATS|scripts=" + store.snapshot().size() + "|payload-bytes=" + store.storedPayloadBytes());
                case "GET" -> {
                    if (parts.length != 2 || !validName(parts[1])) { out.println("ERROR|GET requires valid name"); break; }
                    ScriptData data = store.get(parts[1]);
                    out.println(data == null ? "NOT_FOUND" : "VALUE|" + data.version() + "|" + encode(data.content()));
                }
                case "PUT", "REPL" -> {
                    if (parts.length != 4 || !validName(parts[1])) { out.println("ERROR|PUT/REPL requires name, version, bytes"); break; }
                    int version = Integer.parseInt(parts[2]);
                    byte[] bytes = decode(parts[3]);
                    ScriptData data = new ScriptData(version, bytes);
                    boolean accepted = store.put(parts[1], data);
                    if (!accepted) { out.println("ERROR|stale version"); break; }
                    System.out.printf("[%s] %s script=%s version=%d bytes=%d%n", id, parts[0], parts[1], version, bytes.length);
                    if (parts[0].equals("PUT")) {
                        int copies = replicate(parts[1], data);
                        out.println("OK|stored-locally|replicated-to=" + copies + "/" + peers.size());
                    } else out.println("OK|replica-stored");
                }

                case "FPUT" -> {
                    if (parts.length != 5 || !validName(parts[1])) {
                        out.println("ERROR|invalid fragment");
                        break;
                    }

                    int version = Integer.parseInt(parts[2]);
                    int index = Integer.parseInt(parts[3]);
                    byte[] content = decode(parts[4]);

                    store.putFragment(parts[1], version, index, content);

                    out.println("OK|fragment-stored|index=" + index);
                }

                case "FGET" -> {
                    if (parts.length != 4 || !validName(parts[1])) {
                        out.println("ERROR|invalid fragment request");
                        break;
                    }

                    int version = Integer.parseInt(parts[2]);
                    int index = Integer.parseInt(parts[3]);

                    byte[] fragment = store.getFragment(parts[1], version, index);

                    if (fragment == null) {
                        out.println("NOT_FOUND");
                    } else {
                        out.println("FRAGMENT|" + index + "|" + encode(fragment));
                    }
                }

                default -> out.println("ERROR|unknown command");
            }
        } catch (Exception ex) {
            System.err.printf("[%s] request error: %s%n", id, ex.getMessage());
        }
    }

    private static boolean validName(String name) { return name.matches("[A-Za-z0-9_.-]{1,80}"); }

    private int replicate(String name, ScriptData data) {
        int count = 0;
        for (String peer : peers) {
            String[] hostPort = peer.split(":", 2);
            try {
                String response = request(hostPort[0], Integer.parseInt(hostPort[1]),
                        "REPL|" + name + "|" + data.version() + "|" + encode(data.content()));
                if (response.startsWith("OK|")) count++;
            } catch (Exception ex) {
                System.err.printf("[%s] replication to %s failed: %s%n", id, peer, ex.getMessage());
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
                if (response == null) throw new IOException("empty response");
                return response;
            }
        }
    }

    static String encode(byte[] content) { return Base64.getEncoder().encodeToString(content); }
    static byte[] decode(String encoded) { return Base64.getDecoder().decode(encoded); }
}
