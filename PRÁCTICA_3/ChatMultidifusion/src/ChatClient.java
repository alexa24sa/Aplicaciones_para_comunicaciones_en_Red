import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Objects;

public class ChatClient {
    // Componentes de la interfaz gráfica
    private JFrame frame;
    private JTextPane chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton stickerButton;
    private JButton fileButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private JComboBox<String> modeCombo;
    private String userName;
    // Streams y sockets para comunicación
    private PrintWriter out;
    private BufferedReader in;
    private Socket socket;
    private ServerSocket fileReceiveSocket; // Para recibir archivos

    public ChatClient() {
        buildGUI(); // Construye la interfaz gráfica
    }

    // Construye la interfaz gráfica del cliente de chat
    private void buildGUI() {
        frame = new JFrame("Chat Messenger");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 540);
        frame.setLocationRelativeTo(null);

        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
        JScrollPane scrollPane = new JScrollPane(chatArea);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        sendButton = new JButton("Enviar");

        // Botón de sticker
        stickerButton = new JButton("😊");
        stickerButton.setMargin(new Insets(2, 6, 2, 6));
        stickerButton.setToolTipText("Enviar sticker");
        stickerButton.addActionListener(e -> showStickerPanel());

        // Botón de archivo
        fileButton = new JButton("📎");
        fileButton.setMargin(new Insets(2, 6, 2, 6));
        fileButton.setToolTipText("Enviar archivo o foto");
        fileButton.addActionListener(e -> sendFileDialog());

        // Combo para seleccionar modo de envío (Todos, Privado, Grupo)
        modeCombo = new JComboBox<>(new String[]{"Todos", "Privado", "Grupo"});

        // Lista de usuarios conectados
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setCellRenderer(new UserListRenderer());
        userList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        userList.setVisibleRowCount(10);
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(140, 0));
        JLabel userLabel = new JLabel("Usuarios conectados:");

        // Paneles para organizar la interfaz
        JPanel southPanel = new JPanel(new BorderLayout(5, 5));
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.add(modeCombo, BorderLayout.WEST);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 2));
        iconPanel.add(stickerButton);
        iconPanel.add(fileButton);

        southPanel.add(inputPanel, BorderLayout.CENTER);
        southPanel.add(iconPanel, BorderLayout.EAST);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(userLabel, BorderLayout.NORTH);
        rightPanel.add(userScroll, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout(10, 10));
        frame.add(scrollPane, BorderLayout.CENTER);
        frame.add(southPanel, BorderLayout.SOUTH);
        frame.add(rightPanel, BorderLayout.EAST);

        // Listeners para enviar mensajes con botón o Enter
        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());

        frame.setVisible(true);
    }

    // Muestra el panel para elegir y enviar un sticker
    private void showStickerPanel() {
        JDialog stickerDialog = new JDialog(frame, "Elige un sticker", false);
        // Panel de stickers en formato de cuadrícula
        JPanel stickersPanel = new JPanel(new GridLayout(0, 5, 6, 6));
        File stickerFolder = new File("stickers");
        if (!stickerFolder.exists() || !stickerFolder.isDirectory()) {
            JOptionPane.showMessageDialog(frame, "La carpeta 'stickers' no existe.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        File[] stickerFiles = stickerFolder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".png") ||
                        name.toLowerCase().endsWith(".jpg") ||
                        name.toLowerCase().endsWith(".jpeg") ||
                        name.toLowerCase().endsWith(".gif")
        );
        if (stickerFiles == null || stickerFiles.length == 0) {
            JOptionPane.showMessageDialog(frame, "No hay stickers en la carpeta.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Agrega cada sticker como botón
        for (File sticker : stickerFiles) {
            ImageIcon icon = new ImageIcon(sticker.getPath());
            Image img = icon.getImage().getScaledInstance(48, 48, Image.SCALE_SMOOTH);
            JButton btn = new JButton(new ImageIcon(img));
            btn.setPreferredSize(new Dimension(52, 52));
            btn.setBorder(BorderFactory.createEmptyBorder());
            btn.setContentAreaFilled(false);
            btn.setToolTipText(sticker.getName());
            btn.addActionListener(ev -> {
                sendSticker(sticker.getName());
                stickerDialog.dispose();
            });
            stickersPanel.add(btn);
        }
        stickerDialog.add(stickersPanel);
        stickerDialog.pack();
        stickerDialog.setLocationRelativeTo(frame);
        stickerDialog.setVisible(true);
    }

    // Envía el sticker seleccionado según el modo (Todos, Privado, Grupo)
    private void sendSticker(String stickerName) {
        String mode = (String) modeCombo.getSelectedItem();
        List<String> selectedUsers = userList.getSelectedValuesList();
        if (mode.equals("Todos")) {
            out.println("STICKER_ALL||" + stickerName);
            System.out.println(userName + " --- todos envía sticker (" + stickerName + ")");
        } else if (mode.equals("Privado")) {
            if (selectedUsers.size() != 1) {
                JOptionPane.showMessageDialog(frame, "Selecciona exactamente un usuario para sticker privado.", "Advertencia", JOptionPane.WARNING_MESSAGE);
                return;
            }
            out.println("STICKER_PRIVATE|" + selectedUsers.get(0) + "|" + stickerName);
            System.out.println(userName + " --- " + selectedUsers.get(0) + " envía sticker (" + stickerName + ")");
        } else if (mode.equals("Grupo")) {
            if (selectedUsers.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Selecciona al menos un usuario para sticker grupal.", "Advertencia", JOptionPane.WARNING_MESSAGE);
                return;
            }
            out.println("STICKER_GROUP|" + String.join(",", selectedUsers) + "|" + stickerName);
            System.out.println(userName + " --- grupo " + selectedUsers + " envía sticker (" + stickerName + ")");
        }
    }

    // Abre un diálogo para seleccionar y enviar un archivo
    private void sendFileDialog() {
        JFileChooser chooser = new JFileChooser();
        int res = chooser.showOpenDialog(frame);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            sendFile(file);
        }
    }

    // Envía el archivo seleccionado a los destinatarios según el modo
    private void sendFile(File file) {
        String mode = (String) modeCombo.getSelectedItem();
        List<String> selectedUsers = userList.getSelectedValuesList();
        try {
            ServerSocket tempServer = new ServerSocket(0); // Puerto aleatorio
            int filePort = tempServer.getLocalPort();
            // DECLARA expectedReceivers como final (así Java lo permite en la lambda)
            final int expectedReceivers;
            if (mode.equals("Todos")) {
                out.println("FILE_ALL||" + file.getName() + "|" + file.length() + "|" + filePort);
                expectedReceivers = userListModel.getSize() - 1; // todos menos tú
                System.out.println(userName + " --- todos envía (" + file.getName() + ")");
            } else if (mode.equals("Privado")) {
                if (selectedUsers.size() != 1) {
                    JOptionPane.showMessageDialog(frame, "Selecciona exactamente un usuario para archivo privado.", "Advertencia", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                out.println("FILE_PRIVATE|" + selectedUsers.get(0) + "|" + file.getName() + "|" + file.length() + "|" + filePort);
                expectedReceivers = 1;
                System.out.println(userName + " --- " + selectedUsers.get(0) + " envía (" + file.getName() + ")");
            } else if (mode.equals("Grupo")) {
                if (selectedUsers.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Selecciona al menos un usuario para archivo grupal.", "Advertencia", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                out.println("FILE_GROUP|" + String.join(",", selectedUsers) + "|" + file.getName() + "|" + file.length() + "|" + filePort);
                expectedReceivers = selectedUsers.size();
                System.out.println(userName + " --- grupo " + selectedUsers + " envía (" + file.getName() + ")");
            } else {
                expectedReceivers = 1; // fallback
            }
            // Usa expectedReceivers dentro del hilo/lambda, ya es final
            new Thread(() -> {
                try {
                    for (int i = 0; i < expectedReceivers; i++) {
                        Socket clientSocket = tempServer.accept();
                        System.out.println("Archivo siendo enviado a un cliente...");
                        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                        BufferedOutputStream bos = new BufferedOutputStream(clientSocket.getOutputStream());
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = bis.read(buffer)) > 0) {
                            bos.write(buffer, 0, count);
                        }
                        bos.flush();
                        bis.close();
                        bos.close();
                        clientSocket.close();
                        System.out.println("Archivo enviado a cliente.");
                    }
                    tempServer.close();
                } catch (Exception ex) {
                    System.out.println("Error enviando archivo: " + ex.getMessage());
                    showMessage("[Error enviando archivo]\n", Color.RED, true);
                }
            }).start();
            showMessage("[Archivo enviado: " + file.getName() + "]\n", new Color(60, 100, 200), true);
        } catch (IOException e) {
            showMessage("[Error preparando envío de archivo]\n", Color.RED, true);
        }
    }

    // Inicia la conexión con el servidor y los hilos de recepción de mensajes y archivos
    private void start(String serverHost, int serverPort) throws Exception {
        userName = JOptionPane.showInputDialog(frame, "Ingresa tu nombre de usuario:", "Usuario", JOptionPane.PLAIN_MESSAGE);
        if (userName == null || userName.trim().isEmpty()) {
            System.exit(0);
        }
        socket = new Socket(serverHost, serverPort);
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Inicia el socket para recibir archivos
        fileReceiveSocket = new ServerSocket(0); // Puerto aleatorio
        int fileReceivePort = fileReceiveSocket.getLocalPort();
        out.println(userName + "|" + fileReceivePort); // Enviar usuario+puerto para recibir archivos

        // Hilo para recibir mensajes del servidor
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    String finalLine = line;
                    SwingUtilities.invokeLater(() -> processServerMessage(finalLine));
                }
            } catch (IOException e) {
                showMessage("Conexión cerrada.\n", Color.RED, true);
                frame.dispose();
            }
        }).start();

        // Hilo para recibir archivos entrantes
        new Thread(() -> {
            while (!fileReceiveSocket.isClosed()) {
                try {
                    Socket senderSocket = fileReceiveSocket.accept();
                    DataInputStream dis = new DataInputStream(senderSocket.getInputStream());
                    String fileName = dis.readUTF();
                    long fileSize = dis.readLong();
                    File saveTo = new File("descargas", fileName);
                    saveTo.getParentFile().mkdirs();
                    FileOutputStream fos = new FileOutputStream(saveTo);
                    byte[] buffer = new byte[4096];
                    long bytesReceived = 0;
                    int read;
                    while (bytesReceived < fileSize && (read = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize-bytesReceived))) > 0) {
                        fos.write(buffer, 0, read);
                        bytesReceived += read;
                    }
                    fos.close();
                    dis.close();
                    senderSocket.close();
                    showReceivedFile(saveTo);
                    System.out.println(userName + " --- recibe archivo: " + fileName);
                } catch (Exception ex) {
                    System.out.println(userName + " --- error recibiendo archivo");
                    showMessage("[Error recibiendo archivo]\n", Color.RED, true);
                }
            }
        }).start();
    }

    // Procesa los mensajes recibidos del servidor y los muestra en el chat
    private void processServerMessage(String line) {
        if (line.startsWith("ERROR|")) {
            JOptionPane.showMessageDialog(frame, line.substring(6), "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        } else if (line.startsWith("USERS|")) {
            // Actualiza la lista de usuarios conectados
            String[] users = line.substring(6).split(",");
            userListModel.clear();
            for (String u : users) if (!u.isEmpty()) userListModel.addElement(u);
        } else if (line.startsWith("INFO|")) {
            // Mensaje informativo
            showMessage(line.substring(5) + "\n", new Color(0, 102, 204), true);
            System.out.println("Servidor --- " + line.substring(5));
        } else if (line.startsWith("MSG|")) {
            // Mensaje de chat (público, privado o grupal)
            String[] parts = line.split("\\|", 3);
            String tag = parts[1];
            String msg = parts[2];
            Color color = tag.contains("Privado") ? new Color(102, 0, 153) : (tag.contains("Grupo") ? new Color(0, 153, 51) : Color.BLACK);
            showMessage("[" + tag + "] " + msg + "\n", color, false);
            System.out.println("Mensaje recibido: [" + tag + "] " + msg);
        } else if (line.startsWith("STICKER|")) {
            // Mensaje de sticker recibido
            String[] parts = line.split("\\|", 5);
            String tipo = parts[1];
            String remitente = parts[2];
            String recipients = parts[3];
            String stickerName = parts[4];
            showStickerMessage(tipo, remitente, recipients, stickerName);
            System.out.println(userName + " --- recibe sticker de " + remitente + " (" + stickerName + ")");
        } else if (line.startsWith("FILE|")) {
            // Protocolo: FILE|tipo|remitente|recipients|fileName|fileSize|senderHost|senderPort
            String[] parts = line.split("\\|", 8);
            String tipo = parts[1];
            String remitente = parts[2];
            String recipients = parts[3];
            String fileName = parts[4];
            long fileSize = Long.parseLong(parts[5]);
            String senderHost = parts[6];
            int senderPort = Integer.parseInt(parts[7]);
            receiveFileFrom(remitente, recipients, fileName, fileSize, senderHost, senderPort, tipo);
        }
    }

    // Muestra un mensaje en el área de chat con formato de color y negritas
    private void showMessage(String msg, Color color, boolean bold) {
        StyledDocument doc = chatArea.getStyledDocument();
        Style style = chatArea.addStyle("Style", null);
        StyleConstants.setForeground(style, color);
        StyleConstants.setBold(style, bold);
        try {
            doc.insertString(doc.getLength(), msg, style);
            chatArea.setCaretPosition(doc.getLength());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    // Muestra un sticker recibido en el área de chat
    private void showStickerMessage(String tipo, String remitente, String recipients, String stickerName) {
        try {
            StyledDocument doc = chatArea.getStyledDocument();
            Style style = chatArea.addStyle("sticker", null);
            StyleConstants.setAlignment(style, StyleConstants.ALIGN_LEFT);

            String tag;
            if (tipo.equals("ALL")) tag = "Todos";
            else if (tipo.equals("PRIVATE")) tag = "Privado";
            else if (tipo.equals("GROUP")) tag = "Grupo";
            else tag = "Sticker";

            String msg = "[" + tag + "] " + remitente + (tipo.equals("PRIVATE") ? " ➡ " + recipients : tipo.equals("GROUP") ? " ➡ " + recipients : "") + ":\n";
            doc.insertString(doc.getLength(), msg, style);

            // Inserta la imagen del sticker
            ImageIcon icon = new ImageIcon("stickers/" + stickerName);
            Image img = icon.getImage().getScaledInstance(72, 72, Image.SCALE_SMOOTH);
            chatArea.setCaretPosition(doc.getLength());
            chatArea.insertIcon(new ImageIcon(img));
            doc.insertString(doc.getLength(), "\n", style);
            chatArea.setCaretPosition(doc.getLength());
        } catch (Exception ex) {
            showMessage("[Sticker no encontrado]\n", Color.RED, true);
        }
    }

    // Recibe el archivo real desde el emisor y lo guarda en la carpeta descargas
    private void receiveFileFrom(String remitente, String recipients, String fileName, long fileSize, String senderHost, int senderPort, String tipo) {
        new Thread(() -> {
            try {
                Socket socket = new Socket(senderHost, senderPort);
                DataInputStream dis = new DataInputStream(socket.getInputStream());
                File saveTo = new File("descargas", fileName);
                saveTo.getParentFile().mkdirs();
                // Protocolo: el emisor no manda nombre y tamaño, así que el receptor debe saberlo ya
                FileOutputStream fos = new FileOutputStream(saveTo);
                long bytesReceived = 0;
                byte[] buffer = new byte[4096];
                int read;
                while (bytesReceived < fileSize && (read = dis.read(buffer, 0, (int)Math.min(buffer.length, fileSize-bytesReceived))) > 0) {
                    fos.write(buffer, 0, read);
                    bytesReceived += read;
                }
                fos.close();
                dis.close();
                socket.close();
                showReceivedFile(saveTo);
                System.out.println(userName + " --- recibe archivo: " + fileName);
            } catch (Exception ex) {
                System.out.println(userName + " --- error recibiendo archivo: " + fileName);
                showMessage("[Error recibiendo archivo]\n", Color.RED, true);
            }
        }).start();
    }

    // Muestra el archivo recibido en el chat (si es imagen, la despliega; si no, ofrece abrirlo)
    private void showReceivedFile(File file) {
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = chatArea.getStyledDocument();
            String fileName = file.getName().toLowerCase();
            try {
                if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif")) {
                    ImageIcon icon = new ImageIcon(file.getAbsolutePath());
                    Image img = icon.getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH);
                    chatArea.setCaretPosition(doc.getLength());
                    chatArea.insertIcon(new ImageIcon(img));
                    doc.insertString(doc.getLength(), "\n[Imagen recibida: " + file.getName() + "]\n", null);
                } else {
                    doc.insertString(doc.getLength(), "[Archivo recibido: " + file.getName() + "]\n", null);
                }
                chatArea.setCaretPosition(doc.getLength());
            } catch (Exception ex) {
                showMessage("[Error mostrando archivo]\n", Color.RED, true);
            }
        });
    }

    // Envía el mensaje de texto según el modo seleccionado (Todos, Privado, Grupo)
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        String mode = (String) modeCombo.getSelectedItem();
        List<String> selectedUsers = userList.getSelectedValuesList();
        if (mode.equals("Todos")) {
            out.println("ALL||" + text);
            System.out.println(userName + " --- todos envía mensaje: " + text);
        } else if (mode.equals("Privado")) {
            if (selectedUsers.size() != 1) {
                JOptionPane.showMessageDialog(frame, "Selecciona exactamente un usuario para mensaje privado.", "Advertencia", JOptionPane.WARNING_MESSAGE);
                return;
            }
            out.println("PRIVATE|" + selectedUsers.get(0) + "|" + text);
            System.out.println(userName + " --- " + selectedUsers.get(0) + " envía mensaje: " + text);
        } else if (mode.equals("Grupo")) {
            if (selectedUsers.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Selecciona al menos un usuario para mensaje grupal.", "Advertencia", JOptionPane.WARNING_MESSAGE);
                return;
            }
            out.println("GROUP|" + String.join(",", selectedUsers) + "|" + text);
            System.out.println(userName + " --- grupo " + selectedUsers + " envía mensaje: " + text);
        }
        inputField.setText("");
    }

    // Método principal: inicia la aplicación y conecta al servidor
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient();
            try {
                client.start("localhost", 12345);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "No se pudo conectar al servidor.", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
