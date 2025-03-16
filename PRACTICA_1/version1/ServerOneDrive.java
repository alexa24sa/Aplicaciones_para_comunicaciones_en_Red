import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servidor estilo OneDrive/FTP que escucha en un puerto de control (2121).
 * 
 * Para cada conexión de cliente, se lanza un hilo (ClientHandler) que procesa
 * comandos como LIST, MKDIR, PUT, GET, etc. Cuando es necesario transferir datos,
 * el servidor abre un socket en un puerto libre (modo pasivo) y se lo comunica
 * al cliente.
 */
public class ServerOneDrive {
    private static final int CONTROL_PORT = 2121;               // Puerto para comandos
    private static final String SERVER_BASE_DIR = "carpeta_remota"; // Carpeta base en el servidor

    public static void main(String[] args) {
        // Asegurar que la carpeta base exista
        File baseDir = new File(SERVER_BASE_DIR);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        System.out.println("[Servidor] Carpeta base: " + baseDir.getAbsolutePath());

        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(CONTROL_PORT)) {
            System.out.println("[Servidor] Escuchando en puerto " + CONTROL_PORT + "...");

            while (true) {
                // Esperamos conexiones de clientes
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Servidor] Cliente conectado desde: " + clientSocket.getRemoteSocketAddress());
                // Hilo que atenderá a este cliente
                pool.execute(new ClientHandler(clientSocket, SERVER_BASE_DIR));
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.shutdown();
        }
    }
}

/**
 * Clase que maneja los comandos enviados por un cliente en un hilo independiente.
 */
class ClientHandler implements Runnable {
    private Socket controlSocket;    // Socket para comandos
    private String baseDir;         // Carpeta base en el servidor

    public ClientHandler(Socket controlSocket, String baseDir) {
        this.controlSocket = controlSocket;
        this.baseDir = baseDir;
    }

    @Override
    public void run() {
        try (DataInputStream dis = new DataInputStream(controlSocket.getInputStream());
             DataOutputStream dos = new DataOutputStream(controlSocket.getOutputStream())) {

            while (true) {
                String line;
                try {
                    // Esperamos un comando (en UTF) desde el cliente
                    line = dis.readUTF();
                } catch (EOFException e) {
                    // El cliente cerró la conexión
                    break;
                }

                if (line == null || line.isEmpty()) {
                    break;
                }

                System.out.println("[Servidor] Comando recibido: " + line);
                String[] parts = line.split(" ");
                String command = parts[0].toUpperCase();

                switch (command) {
                    case "LIST":
                        listCommand(parts, dos);
                        break;
                    case "MKDIR":
                        mkdirCommand(parts, dos);
                        break;
                    case "PUT":
                        putCommand(parts, dos);
                        break;
                    case "GET":
                        getCommand(parts, dos);
                        break;
                    case "EXIT":
                        dos.writeUTF("OK: Conexión finalizada.");
                        dos.flush();
                        return;
                    default:
                        dos.writeUTF("ERROR: Comando no reconocido.");
                        dos.flush();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                controlSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("[Servidor] Cliente desconectado: " + controlSocket.getRemoteSocketAddress());
        }
    }

    /**
     * LIST [subcarpeta] -> Lista contenido de la carpeta base o subcarpeta.
     */
    private void listCommand(String[] parts, DataOutputStream dos) throws IOException {
        String subdir = (parts.length > 1) ? parts[1] : "";
        File dir = new File(baseDir, subdir);

        if (!dir.exists() || !dir.isDirectory()) {
            dos.writeUTF("ERROR: No existe la carpeta o no es un directorio.");
            dos.flush();
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            dos.writeUTF("ERROR: No se pudo listar el contenido.");
            dos.flush();
            return;
        }

        StringBuilder sb = new StringBuilder("LISTA DE ARCHIVOS:\n");
        for (File f : files) {
            String type = f.isDirectory() ? "[DIR ] " : "[FILE] ";
            sb.append(type).append(f.getName()).append("\n");
        }

        dos.writeUTF(sb.toString());
        dos.flush();
    }

    /**
     * MKDIR <nombreCarpeta> -> Crea una carpeta en baseDir.
     */
    private void mkdirCommand(String[] parts, DataOutputStream dos) throws IOException {
        if (parts.length < 2) {
            dos.writeUTF("ERROR: Sintaxis: MKDIR <carpeta>");
            dos.flush();
            return;
        }

        String dirName = parts[1];
        File newDir = new File(baseDir, dirName);
        if (newDir.exists()) {
            dos.writeUTF("ERROR: Ya existe esa carpeta/archivo.");
            dos.flush();
            return;
        }

        if (newDir.mkdir()) {
            dos.writeUTF("OK: Carpeta creada.");
        } else {
            dos.writeUTF("ERROR: No se pudo crear la carpeta.");
        }
        dos.flush();
    }

    /**
     * PUT <archivoDestino> -> El cliente quiere subir un archivo.
     * El servidor abre un ServerSocket en un puerto libre y lo comunica al cliente (PORT).
     */
    private void putCommand(String[] parts, DataOutputStream dos) throws IOException {
        if (parts.length < 2) {
            dos.writeUTF("ERROR: Sintaxis: PUT <nombreArchivoDestino>");
            dos.flush();
            return;
        }

        String filename = parts[1];
        ServerSocket dataServer = new ServerSocket(0);
        int dataPort = dataServer.getLocalPort();

        // Enviamos "PORT <puerto>"
        dos.writeUTF("PORT " + dataPort);
        dos.flush();

        // Aceptamos la conexión de datos
        Socket dataSocket = dataServer.accept();
        DataInputStream dataDIS = new DataInputStream(dataSocket.getInputStream());

        // El primer dato que envía el cliente es el tamaño del archivo
        long fileSize = dataDIS.readLong();

        // Creamos el archivo en baseDir
        File outFile = new File(baseDir, filename);
        FileOutputStream fos = new FileOutputStream(outFile);

        long recibidos = 0;
        int n;
        byte[] buf = new byte[4096];

        while (recibidos < fileSize) {
            n = dataDIS.read(buf);
            if (n == -1) {
                break;
            }
            fos.write(buf, 0, n);
            fos.flush();
            recibidos += n;
        }

        fos.close();
        dataDIS.close();
        dataSocket.close();
        dataServer.close();

        dos.writeUTF("OK: Archivo recibido (" + filename + ") Tamaño=" + fileSize + " bytes");
        dos.flush();
    }

    /**
     * GET <archivo> -> El cliente quiere descargar un archivo.
     * El servidor abre un ServerSocket (puerto libre) y lo comunica al cliente para la transferencia.
     */
    private void getCommand(String[] parts, DataOutputStream dos) throws IOException {
        if (parts.length < 2) {
            dos.writeUTF("ERROR: Sintaxis: GET <archivo>");
            dos.flush();
            return;
        }

        String filename = parts[1];
        File inFile = new File(baseDir, filename);
        if (!inFile.exists() || !inFile.isFile()) {
            dos.writeUTF("ERROR: Archivo no existe en el servidor.");
            dos.flush();
            return;
        }

        // Abrimos ServerSocket "pasivo"
        ServerSocket dataServer = new ServerSocket(0);
        int dataPort = dataServer.getLocalPort();

        dos.writeUTF("PORT " + dataPort);
        dos.flush();

        // Conexión de datos
        Socket dataSocket = dataServer.accept();
        DataOutputStream dataDOS = new DataOutputStream(dataSocket.getOutputStream());

        long fileSize = inFile.length();
        dataDOS.writeLong(fileSize);

        FileInputStream fis = new FileInputStream(inFile);
        byte[] buf = new byte[4096];
        int n;
        long enviados = 0;

        while ((n = fis.read(buf)) != -1) {
            dataDOS.write(buf, 0, n);
            dataDOS.flush();
            enviados += n;
        }

        fis.close();
        dataDOS.close();
        dataSocket.close();
        dataServer.close();

        dos.writeUTF("OK: Archivo enviado (" + filename + ") Tamaño=" + fileSize + " bytes");
        dos.flush();
    }
}
