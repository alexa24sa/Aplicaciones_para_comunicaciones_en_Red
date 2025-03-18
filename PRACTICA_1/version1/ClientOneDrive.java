import java.io.*;
import java.net.*;
import java.util.Scanner;
import javax.swing.*;

/**
 * Cliente estilo OneDrive/FTP que se conecta al ServerOneDrive.
 * 
 * Usa un socket de control para enviar comandos (LIST, MKDIR, PUT, GET, EXIT).
 * Cuando recibe la respuesta "PORT <puerto>", abre un socket de datos a dicho puerto
 * para transferir el archivo. 
 * 
 * Incluye el uso de JFileChooser para que el usuario seleccione el archivo a subir.
 * Opcionalmente, se puede habilitar un JFileChooser para seleccionar la carpeta
 * donde descargar (GET).
 */
public class ClientOneDrive {
    private static final String SERVER_HOST = "127.0.0.1"; // Cambia a la IP del servidor si no es localhost
    private static final int SERVER_CONTROL_PORT = 2121;
    
    // Carpeta local "base" por si deseas guardar/leer archivos sin JFC
    private static final String CLIENT_BASE_DIR = "carpeta_local";

    public static void main(String[] args) {
        // Inicializar el contexto gráfico de Swing
        SwingUtilities.invokeLater(() -> {
            // Crear una ventana invisible para inicializar el contexto gráfico
            JFrame frame = new JFrame();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(false); // No mostrar la ventana
        });
        // Crear la carpeta local si no existe
        File localDir = new File(CLIENT_BASE_DIR);
        if (!localDir.exists()) {
            localDir.mkdirs();
        }
        
        try (Socket controlSocket = new Socket(SERVER_HOST, SERVER_CONTROL_PORT);
             DataInputStream dis = new DataInputStream(controlSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(controlSocket.getOutputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("[Cliente] Conectado al servidor en " + SERVER_HOST + ":" + SERVER_CONTROL_PORT);
            System.out.println("[Cliente] Carpeta local base: " + localDir.getAbsolutePath());

            while (true) {
                System.out.print("\nIngrese comando (LIST, MKDIR, PUT, GET, EXIT): ");
                String input = scanner.nextLine();
                if (input == null || input.isEmpty()) {
                    continue;
                }

                // Separamos el comando
                String[] parts = input.split(" ");
                String command = parts[0].toUpperCase();

                // Enviamos el comando al servidor
                dos.writeUTF(input);
                dos.flush();

                if (command.equals("PUT")) {
//                    // El usuario quiere subir un archivo
//                    // 1) Abrimos un JFileChooser para seleccionar el archivo local a subir
//                    JFileChooser jf = new JFileChooser();
//                    int r = jf.showOpenDialog(null);
//                    if (r != JFileChooser.APPROVE_OPTION) {
//                        System.out.println("[Cliente] Subida cancelada. No se seleccionó archivo.");
//                        // Podríamos enviar un comando extra de "CANCEL" al servidor, pero en este ejemplo lo omitimos.
//                        // Simplemente leemos la respuesta inmediata del servidor (que quedará "desfasada").
//                        // Este ejemplo asume que las cosas están bien si no subimos nada.
//                        // En un proyecto real, habría que manejar este caso con mayor cuidado.
//                        String skipResp = dis.readUTF();
//                        System.out.println("[Servidor] " + skipResp);
//                        continue;
//                    }
                    File fileToSend = new File(CLIENT_BASE_DIR + "/" + parts[1]); // jf.getSelectedFile();

                    // 2) Leemos la respuesta del servidor para PUT (normalmente "PORT <puerto>")
                    String serverResp = dis.readUTF();
                    if (serverResp.startsWith("PORT")) {
                        int dataPort = Integer.parseInt(serverResp.split(" ")[1]);
                        // 3) Realizamos la subida por el socket de datos
                        uploadFile(dataPort, fileToSend);
                        // 4) Leemos la confirmación final del servidor
                        String finalResp = dis.readUTF();
                        System.out.println("[Servidor] " + finalResp);
                    } else {
                        System.out.println("[Servidor] " + serverResp);
                    }

                } else if (command.equals("GET")) {
                    // El usuario quiere descargar un archivo
                    // (parts[1] sería el nombre del archivo remoto)
                    if (parts.length < 2) {
                        System.out.println("[Cliente] Uso: GET <nombreArchivoRemoto>");
                        // Consumimos la respuesta del servidor y lo imprimimos
                        String serverResp = dis.readUTF();
                        System.out.println("[Servidor] " + serverResp);
                        continue;
                    }

                    // 1) Esperamos la respuesta del servidor (PORT o ERROR)
                    String serverResp = dis.readUTF();
                    if (serverResp.startsWith("PORT")) {
                        int dataPort = Integer.parseInt(serverResp.split(" ")[1]);

                        // 2) Preguntamos al usuario dónde guardar el archivo (JFileChooser de directorios)
                        String remoteFileName = parts[1]; // el nombre del archivo remoto
                        // OJO: si quieres forzar que el archivo se guarde con el mismo nombre remotamente,
                        // podrías simplemente guardarlo en CLIENT_BASE_DIR con ese nombre sin preguntar.
                        // A continuación se muestra un JFileChooser de directorio.
                        
                        JFileChooser jfc = new JFileChooser();
                        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                        int opc = jfc.showSaveDialog(null);
                        File outFolder;
                        if (opc == JFileChooser.APPROVE_OPTION) {
                            outFolder = jfc.getSelectedFile();
                        } else {
                            // Si el usuario no elige nada, usamos la carpeta base local
                            outFolder = new File(CLIENT_BASE_DIR);
                        }
                        // Creamos la ruta destino final
                        File outFile = new File(outFolder, remoteFileName);

                        // 3) Descargamos el archivo del servidor
                        downloadFile(dataPort, outFile);

                        // 4) Leemos la confirmación final
                        String finalResp = dis.readUTF();
                        System.out.println("[Servidor] " + finalResp);
                    } else {
                        System.out.println("[Servidor] " + serverResp);
                    }

                } else if (command.equals("EXIT")) {
                    // Cerramos la conexión
                    String finalResp = dis.readUTF();
                    System.out.println("[Servidor] " + finalResp);
                    break;

                } else {
                    // Para otros comandos (LIST, MKDIR, etc.), solo imprimimos la respuesta
                    String serverResp = dis.readUTF();
                    System.out.println("[Servidor] " + serverResp);
                }
            }

            System.out.println("[Cliente] Finalizado.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sube un archivo al servidor a través de un socket de datos (puerto dataPort).
     * @param dataPort  Puerto abierto por el servidor para la transferencia
     * @param file      Archivo local que se desea subir
     */
    private static void uploadFile(int dataPort, File file) {
        if (!file.exists()) {
            System.out.println("[Cliente] Error: El archivo local no existe: " + file.getAbsolutePath());
            return;
        }

        long fileSize = file.length();
        System.out.println("[Cliente] Subiendo archivo: " + file.getName() + " (" + fileSize + " bytes)");

        try (Socket dataSocket = new Socket(SERVER_HOST, dataPort);
             DataOutputStream dosData = new DataOutputStream(dataSocket.getOutputStream());
             FileInputStream fis = new FileInputStream(file)) {

            // Enviamos primero el tamaño del archivo
            dosData.writeLong(fileSize);

            byte[] buf = new byte[4096];
            long enviados = 0;
            int n;
            while ((n = fis.read(buf)) != -1) {
                dosData.write(buf, 0, n);
                dosData.flush();
                enviados += n;
                int pct = (int)((enviados * 100) / fileSize);
                System.out.print("\rProgreso: " + pct + "%");
            }
            System.out.println("\n[Cliente] Archivo enviado correctamente.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Descarga un archivo desde el servidor y lo guarda en outFile.
     * @param dataPort  Puerto abierto por el servidor para la transferencia
     * @param outFile   Ruta local donde se guardará
     */
    private static void downloadFile(int dataPort, File outFile) {
        try (Socket dataSocket = new Socket(SERVER_HOST, dataPort);
             DataInputStream disData = new DataInputStream(dataSocket.getInputStream());
             FileOutputStream fos = new FileOutputStream(outFile)) {

            // Leemos el tamaño del archivo
            long fileSize = disData.readLong();
            System.out.println("[Cliente] Descargando archivo en: " + outFile.getAbsolutePath() 
                               + " (" + fileSize + " bytes)");
            
            byte[] buf = new byte[4096];
            long recibidos = 0;
            int n;
            while (recibidos < fileSize) {
                n = disData.read(buf);
                if (n == -1) break;
                fos.write(buf, 0, n);
                recibidos += n;
                int pct = (int)((recibidos * 100) / fileSize);
                System.out.print("\rProgreso: " + pct + "%");
            }
            System.out.println("\n[Cliente] Archivo descargado correctamente.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
