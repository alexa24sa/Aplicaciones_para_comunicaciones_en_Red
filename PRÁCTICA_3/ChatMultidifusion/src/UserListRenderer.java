import javax.swing.*;
import java.awt.*;

// Renderer para mostrar bolita verde junto al nombre de usuario conectado
public class UserListRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean isSelected, boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index,
                isSelected, cellHasFocus);
        label.setIcon(new GreenDotIcon());
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setBorder(BorderFactory.createEmptyBorder(3, 5, 3, 5));
        return label;
    }

    static class GreenDotIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(new Color(0, 200, 0));
            g.fillOval(x, y + 5, 12, 12);
            g.setColor(Color.DARK_GRAY);
            g.drawOval(x, y + 5, 12, 12);
        }
        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 22; }
    }
}