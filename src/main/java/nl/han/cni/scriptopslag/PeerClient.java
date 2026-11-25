package nl.han.cni.scriptopslag;

import java.nio.charset.StandardCharsets;

/** CLI testclient die via TCP praat met de peer-server in dezelfde container. */
public final class PeerClient {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) { usage(); System.exit(2); }
        String request;
        switch (args[0].toLowerCase()) {
            case "ping" -> request = "PING";
            case "list" -> request = "LIST";
            case "stats" -> request = "STATS";
            case "get" -> {
                if (args.length != 2) { usage(); return; }
                request = "GET|" + validName(args[1]);
            }
            case "put" -> {
                if (args.length != 4) { usage(); return; }
                int version = Integer.parseInt(args[2]);
                request = "PUT|" + validName(args[1]) + "|" + version + "|" +
                        PeerServer.encode(args[3].getBytes(StandardCharsets.UTF_8));
            }
            default -> { usage(); return; }
        }
        String reply = PeerServer.request("127.0.0.1", Integer.parseInt(System.getenv().getOrDefault("PORT", "7000")), request);
        if (reply.startsWith("VALUE|")) {
            String[] parts = reply.split("\\|", 3);
            System.out.println("version=" + parts[1]);
            System.out.println("content=" + new String(PeerServer.decode(parts[2]), StandardCharsets.UTF_8));
        } else System.out.println(reply);
        if (reply.equals("NOT_FOUND") || reply.startsWith("ERROR|")) System.exit(1);
    }

    private static String validName(String name) {
        if (!name.matches("[A-Za-z0-9_.-]{1,80}")) throw new IllegalArgumentException("invalid script name");
        return name;
    }

    private static void usage() {
        System.out.println("PeerClient ping | list | stats | get <name> | put <name> <version> <text>");
    }
}
