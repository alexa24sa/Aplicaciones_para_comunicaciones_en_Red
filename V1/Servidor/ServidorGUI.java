package Servidor;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ServidorGUI extends JFrame {
    private JList<String> listaArchivos;
    private DefaultListModel<String> modelo;

    public ServidorGUI() {
        modelo = new DefaultListModel<>();
        listaArchivos = new JList<>(modelo);
        JScrollPane scroll = new JScrollPane(listaArchivos);
        this.add(scroll, BorderLayout.CENTER);
        this.setTitle("Vista de Archivos del Servidor");
        this.setSize(400, 400);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        actualizarLista();
    }

    // Método para actualizar la lista de archivos en el directorio del servidor
    public void actualizarLista() {
        File directorio = new File("." + System.getProperty("file.separator") + "serverP1");
        File[] archivos = directorio.listFiles();
        modelo.clear();
        if (archivos != null) {
            for (File f : archivos) {
                modelo.addElement(f.getName());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ServidorGUI gui = new ServidorGUI();
            gui.setVisible(true);
        });
    }
}
