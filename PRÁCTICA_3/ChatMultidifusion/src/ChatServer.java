import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 12345;
    private static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Integer> clientFilePorts = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Servidor de chat iniciado en el puerto " + PORT);
        while (true) {
            Socket socket = serverSocket.accept();
            new ClientHandler(socket).start();
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private String userName;
        private PrintWriter out;
        private BufferedReader in;
        private int fileReceivePort;

        public ClientHandler(Socket socket) throws IOException {
            this.socket = socket;
            this.out = new PrintWriter(socket.getOutputStream(), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        public void run() {
            try {
                // Recibe el nombre de usuario y puerto de archivos
                String firstLine = in.readLine();
                if (firstLine == null) return;
                String[] firstParts = firstLine.split("\\|");
                this.userName = firstParts[0];
                if (firstParts.length > 1) {
                    this.fileReceivePort = Integer.parseInt(firstParts[1]);
                } else {
                    this.fileReceivePort = -1;
                }
                if (userName == null || userName.trim().isEmpty() || clients.containsKey(userName)) {
                    out.println("ERROR|Nombre de usuario inválido o ya en uso");
                    socket.close();
                    return;
                }
                clients.put(userName, this);
                clientFilePorts.put(userName, fileReceivePort);
                sendUserListUpdate();
                broadcast("INFO|Servidor: " + userName + " se ha conectado.", null);
                System.out.println("Servidor --- " + userName + " se ha conectado.");

                String line;
                while ((line = in.readLine()) != null) {
                    // tipo|destinatario(s)|mensaje
                    String[] parts = line.split("\\|", 5);
                    String type = parts[0];
                    String recipients = parts.length > 1 ? parts[1] : "";
                    String message = parts.length > 2 ? parts[2] : "";
                    String fileLen = parts.length > 3 ? parts[3] : "";
                    String filePort = parts.length > 4 ? parts[4] : "";

                    if (type.equals("ALL")) {
                        broadcast("MSG|Todos|" + userName + ": " + message, null);
                        System.out.println(userName + " --- todos envía mensaje: " + message);
                    } else if (type.equals("PRIVATE")) {
                        sendToUser(recipients, "MSG|Privado de " + userName + "|" + message);
                        System.out.println(userName + " --- " + recipients + " envía mensaje privado: " + message);
                        if (!recipients.equals(userName)) {
                            sendToUser(userName, "MSG|Privado para " + recipients + "|" + message);
                        }
                    } else if (type.equals("GROUP")) {
                        for (String rec : recipients.split(",")) {
                            sendToUser(rec.trim(), "MSG|Grupo de " + userName + "|" + message);
                            System.out.println(userName + " --- grupo " + rec.trim() + " envía mensaje grupal: " + message);
                        }
                    } else if (type.equals("STICKER_ALL")) {
                        broadcast("STICKER|ALL|" + userName + "||" + message, null);
                        System.out.println(userName + " --- todos envía sticker: " + message);
                    } else if (type.equals("STICKER_PRIVATE")) {
                        sendToUser(recipients, "STICKER|PRIVATE|" + userName + "|" + recipients + "|" + message);
                        System.out.println(userName + " --- " + recipients + " envía sticker privado: " + message);
                        if (!recipients.equals(userName)) {
                            sendToUser(userName, "STICKER|PRIVATE|" + userName + "|" + recipients + "|" + message);
                        }
                    } else if (type.equals("STICKER_GROUP")) {
                        for (String rec : recipients.split(",")) {
                            sendToUser(rec.trim(), "STICKER|GROUP|" + userName + "|" + recipients + "|" + message);
                            System.out.println(userName + " --- grupo " + rec.trim() + " envía sticker grupal: " + message);
                        }
                    } else if (type.startsWith("FILE")) {
                        // Protocolo: FILE_TIPO|recipients|fileName|fileSize|filePort
                        String fileName = message;
                        long fileSize = Long.parseLong(fileLen);
                        int senderFilePort = Integer.parseInt(filePort);
                        String host = socket.getInetAddress().getHostAddress();

                        if (type.equals("FILE_ALL")) {
                            for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                                if (!entry.getKey().equals(userName)) {
                                    int recvPort = clientFilePorts.getOrDefault(entry.getKey(), -1);
                                    // Anuncia el archivo
                                    entry.getValue().out.println("FILE|ALL|" + userName + "||" + fileName + "|" + fileSize + "|" + host + "|" + senderFilePort);
                                    System.out.println(userName + " --- archivo enviado a " + entry.getKey() + " (" + fileName + ")");
                                }
                            }
                        } else if (type.equals("FILE_PRIVATE")) {
                            int recvPort = clientFilePorts.getOrDefault(recipients, -1);
                            sendToUser(recipients, "FILE|PRIVATE|" + userName + "|" + recipients + "|" + fileName + "|" + fileSize + "|" + host + "|" + senderFilePort);
                            System.out.println(userName + " --- archivo privado enviado a " + recipients + " (" + fileName + ")");
                            // Notifica también al emisor
                            if (!recipients.equals(userName)) {
                                sendToUser(userName, "FILE|PRIVATE|" + userName + "|" + recipients + "|" + fileName + "|" + fileSize + "|" + host + "|" + senderFilePort);
                            }
                        } else if (type.equals("FILE_GROUP")) {
                            for (String rec : recipients.split(",")) {
                                sendToUser(rec.trim(), "FILE|GROUP|" + userName + "|" + recipients + "|" + fileName + "|" + fileSize + "|" + host + "|" + senderFilePort);
                                System.out.println(userName + " --- archivo grupal enviado a " + rec.trim() + " (" + fileName + ")");
                            }
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Error con " + userName + ": " + e.getMessage());
            } finally {
                try {
                    if (userName != null) {
                        clients.remove(userName);
                        clientFilePorts.remove(userName);
                        broadcast("INFO|Servidor: " + userName + " se ha desconectado.", null);
                        sendUserListUpdate();
                        System.out.println("Servidor --- " + userName + " se ha desconectado.");
                    }
                    socket.close();
                } catch (IOException e) {}
            }
        }

        void sendToUser(String user, String msg) {
            ClientHandler client = clients.get(user);
            if (client != null) {
                client.out.println(msg);
            }
        }

        void broadcast(String msg, String excludeUser) {
            for (Map.Entry<String, ClientHandler> entry : clients.entrySet()) {
                if (excludeUser == null || !entry.getKey().equals(excludeUser)) {
                    entry.getValue().out.println(msg);
                }
            }
        }

        void sendUserListUpdate() {
            String userList = String.join(",", clients.keySet());
            for (ClientHandler client : clients.values()) {
                client.out.println("USERS|" + userList);
            }
        }
    }
}