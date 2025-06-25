import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class AccionCliente {
    private static final String HOST = "localhost";
    private static final int PUERTO = 1234;

    public static void enviarArchivo(File archivo, String rutaRelServidor) throws IOException {
        try (Socket socket = new Socket(HOST, PUERTO);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             FileInputStream fis = new FileInputStream(archivo)) {
            dos.writeByte(0x01);
            dos.writeUTF(rutaRelServidor);
            dos.writeInt((int) archivo.length());
            byte[] buf = new byte[4096];
            int leido;
            while ((leido = fis.read(buf)) != -1) dos.write(buf, 0, leido);
            dos.flush();
        }
    }

    public static void enviarCarpeta(File carpeta, String rutaRelServidor) throws IOException {
        byte[] zip = ZipUtils.zipDirectory(carpeta);
        try (Socket socket = new Socket(HOST, PUERTO);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeByte(0x02);
            dos.writeUTF(rutaRelServidor);
            dos.writeInt(zip.length);
            dos.write(zip);
            dos.flush();
        }
    }

    public static void borrarArchivo(String rutaRelServidor) throws IOException {
        try (Socket socket = new Socket(HOST, PUERTO);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeByte(0x03);
            dos.writeUTF(rutaRelServidor);
            dos.flush();
        }
    }

    public static void borrarCarpeta(String rutaRelServidor) throws IOException {
        try (Socket socket = new Socket(HOST, PUERTO);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeByte(0x04);
            dos.writeUTF(rutaRelServidor);
            dos.flush();
        }
    }

    public static void crearCarpeta(String rutaRelServidor) throws IOException {
        try (Socket socket = new Socket(HOST, PUERTO);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            dos.writeByte(0x05);
            dos.writeUTF(rutaRelServidor);
            dos.flush();
        }
    }

    public static void descargarArchivo(String rutaRelServidor, File destino) throws IOException {
        try (Socket socket = new Socket(HOST, PUERTO);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            dos.writeByte(0x06);
            dos.writeUTF(rutaRelServidor);
            int tam = dis.readInt();
            if (tam < 0) throw new IOException("El archivo no existe en el servidor");
            byte[] datos = new byte[tam];
            dis.readFully(datos);
            try (FileOutputStream fos = new FileOutputStream(destino)) {
                fos.write(datos);
            }
        }
    }


    public static void descargarCarpeta(String rutaRelServidor, File destino) throws IOException {
        try (Socket socket = new Socket(HOST, PUERTO);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            dos.writeByte(0x07);
            dos.writeUTF(rutaRelServidor);
            int tam = dis.readInt();
            if (tam < 0) throw new IOException("La carpeta no existe en el servidor");
            byte[] datosZip = new byte[tam];
            dis.readFully(datosZip);
            ZipUtils.unzip(datosZip, destino);
        }
    }

    // Listado remoto para mostrar en la GUI
    public static ArrayList<FileInfo> listarRemoto(String rutaRelServidor) throws IOException {
        ArrayList<FileInfo> lista = new ArrayList<>();
        try (Socket socket = new Socket(HOST, PUERTO);
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
             DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            dos.writeByte(0x08);
            dos.writeUTF(rutaRelServidor);
            int n = dis.readInt();
            if (n < 0) return lista; // Error
            for (int i = 0; i < n; i++) {
                String nombre = dis.readUTF();
                boolean esDir = dis.readBoolean();
                long tam = dis.readLong();
                lista.add(new FileInfo(nombre, esDir, tam));
            }
        }
        return lista;
    }


    // Clase auxiliar para listado remoto
    public static class FileInfo {
        public String name;
        public boolean isDir;
        public long size;
        public FileInfo(String name, boolean isDir, long size) {
            this.name = name;
            this.isDir = isDir;
            this.size = size;
        }
    }
}