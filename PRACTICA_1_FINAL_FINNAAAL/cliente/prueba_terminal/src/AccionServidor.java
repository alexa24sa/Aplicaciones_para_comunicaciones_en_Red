import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class AccionServidor {
    private static final int PUERTO = 1234;
    private static final String RUTA_BASE = "C:\\Users\\vale_\\Documents\\ESCUELA\\ESCOM\\6to semestre\\Aplicaciones para Redes\\PRACTICAS\\PRACTICA_1_FINAL_FINNAAAL\\cliente\\prueba_terminal\\servidor\\"; // Cambia por la ruta base real

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PUERTO);
        System.out.println("Servidor listo");

        while (true) {
            Socket socket = serverSocket.accept();
            new Thread(() -> manejarCliente(socket)).start();
        }
    }

    static void manejarCliente(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {
            byte opcode = dis.readByte();
            switch (opcode) {
                case 0x01: // Enviar archivo
                    recibirArchivo(dis);
                    break;
                case 0x02: // Enviar carpeta (zip)
                    recibirCarpeta(dis);
                    break;
                case 0x03: // Borrar archivo
                    borrarArchivo(dis);
                    break;
                case 0x04: // Borrar carpeta
                    borrarCarpeta(dis);
                    break;
                case 0x05: // Crear carpeta
                    crearCarpeta(dis);
                    break;
                case 0x06: // Descargar archivo
                    enviarArchivo(dis, dos);
                    break;
                case 0x07: // Descargar carpeta (como zip)
                    enviarCarpeta(dis, dos);
                    break;
                case 0x08: // Listar archivos/directorios
                    listarContenido(dis, dos);
                    break;
                default:
                    System.out.println("Comando no reconocido: " + opcode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void recibirArchivo(DataInputStream dis) throws IOException {
        String rutaRel = dis.readUTF();
        int tam = dis.readInt();
        byte[] datos = new byte[tam];
        dis.readFully(datos);
        File destino = new File(RUTA_BASE, rutaRel);
        destino.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(destino)) {
            fos.write(datos);
        }
        System.out.println("Archivo recibido: " + destino.getAbsolutePath());
    }

    static void recibirCarpeta(DataInputStream dis) throws IOException {
        String carpetaRel = dis.readUTF();
        int tam = dis.readInt();
        byte[] datosZip = new byte[tam];
        dis.readFully(datosZip);
        File destino = new File(RUTA_BASE, carpetaRel);
        destino.mkdirs();
        ZipUtils.unzip(datosZip, destino);
        System.out.println("Carpeta recibida y descomprimida: " + destino.getAbsolutePath());
    }

    static void borrarArchivo(DataInputStream dis) throws IOException {
        String rutaRel = dis.readUTF();
        File f = new File(RUTA_BASE, rutaRel);
        if (f.isFile() && f.delete()) {
            System.out.println("Archivo borrado: " + f.getAbsolutePath());
        } else if (f.isDirectory()) {
            System.out.println("No se puede borrar carpeta como archivo. Usa la opción correcta.");
        } else {
            System.out.println("No se pudo borrar el archivo: " + f.getAbsolutePath());
        }
    }

    static void borrarCarpeta(DataInputStream dis) throws IOException {
        String rutaRel = dis.readUTF();
        File f = new File(RUTA_BASE, rutaRel);
        if (f.isDirectory()) {
            borrarRecursivo(f);
            System.out.println("Carpeta borrada: " + f.getAbsolutePath());
        }
    }

    static void borrarRecursivo(File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) borrarRecursivo(f);
            else f.delete();
        }
        dir.delete();
    }

    static void crearCarpeta(DataInputStream dis) throws IOException {
        String rutaRel = dis.readUTF();
        File f = new File(RUTA_BASE, rutaRel);
        if (f.mkdirs()) {
            System.out.println("Carpeta creada: " + f.getAbsolutePath());
        }
    }


    static void enviarArchivo(DataInputStream dis, DataOutputStream dos) throws IOException {
        String rutaRel = dis.readUTF();
        File f = new File(RUTA_BASE, rutaRel);
        if (!f.exists() || !f.isFile()) {
            dos.writeInt(-1); // Error: no existe
            return;
        }
        dos.writeInt((int) f.length());
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int leido;
            while ((leido = fis.read(buf)) != -1)
                dos.write(buf, 0, leido);
        }
        System.out.println("Archivo enviado: " + f.getAbsolutePath());
    }

    static void enviarCarpeta(DataInputStream dis, DataOutputStream dos) throws IOException {
        String carpetaRel = dis.readUTF();
        File carpeta = new File(RUTA_BASE, carpetaRel);
        if (!carpeta.exists() || !carpeta.isDirectory()) {
            dos.writeInt(-1); // Error: no existe
            return;
        }
        byte[] zip = ZipUtils.zipDirectory(carpeta);
        dos.writeInt(zip.length);
        dos.write(zip);
        System.out.println("Carpeta enviada (zip): " + carpeta.getAbsolutePath());
    }

    static void listarContenido(DataInputStream dis, DataOutputStream dos) throws IOException {
        String rutaRel = dis.readUTF();
        File dir = new File(RUTA_BASE, rutaRel);
        File[] archivos = dir.listFiles();
        if (archivos == null) {
            dos.writeInt(-1); // Error
            return;
        }
        dos.writeInt(archivos.length);
        for (File f : archivos) {
            dos.writeUTF(f.getName());
            dos.writeBoolean(f.isDirectory());
            dos.writeLong(f.length());
        }
    }

}
