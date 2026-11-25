package nl.han.cni.scriptopslag;

/** Kleine CLI om commando's via TCP naar een peer te sturen. */
public final class PeerClient {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            usage();
            System.exit(2);
        }
        String op = args[0].toLowerCase();
        String request;
        switch (op) {
            case "ping" -> request = "PING";
            case "list" -> request = "LIST";
            case "get" -> {
                if (args.length != 2) { usage(); return; }
                request = "GET|" + validateName(args[1]);
            }
            case "put" -> {
                if (args.length != 3) { usage(); return; }
                request = "PUT|" + validateName(args[1]) + "|" + PeerServer.encode(args[2]);
            }
            default -> { usage(); return; }
        }
        String reply = PeerServer.request("127.0.0.1", Integer.parseInt(System.getenv().getOrDefault("PORT", "7000")), request);
        if (reply.startsWith("VALUE|")) {
            System.out.println(PeerServer.decode(reply.substring(6)));
        } else {
            System.out.println(reply);
        }
        if (reply.equals("NOT_FOUND") || reply.startsWith("ERROR|")) System.exit(1);
    }

    private static String validateName(String name) {
        if (!name.matches("[A-Za-z0-9_.-]{1,80}")) {
            throw new IllegalArgumentException("scriptnaam: alleen A-Z, 0-9, _, . of -");
        }
        return name;
    }

    private static void usage() {
        System.out.println("Gebruik: PeerClient ping | list | get <naam> | put <naam> <scripttekst>");
    }
}
