package subscriptiontracker.gui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.border.Border;

/**
 * The window's colours, fonts and common components, matching the design:
 * a warm light-grey sidebar, white rounded cards and one blue accent.
 */
final class Theme {

    static final Color ACCENT = new Color(0x245BA8);
    static final Color TEXT = new Color(0x1C1F23);
    static final Color MUTED = new Color(0x5B6068);
    static final Color CONTENT = new Color(0xFAFAF8);
    static final Color SIDEBAR = new Color(0xF1F0EB);
    static final Color SIDEBAR_HOVER = new Color(0xE6E4DC);
    static final Color BORDER = new Color(0xE3E1DA);
    static final Color LINE = new Color(0xEFEDE7);
    static final Color TRACK = new Color(0xEFECE4);
    static final Color SOFT_ACCENT = new Color(0xE8EFF9);
    static final Color WARN_BG = new Color(0xFFF4E0);
    static final Color WARN_BORDER = new Color(0xEFC98A);
    static final Color WARN_TEXT = new Color(0x6B4200);
    static final Color WARN_BAR = new Color(0xC07A12);
    static final Color DANGER = new Color(0xA3261A);
    static final Color SAVED = new Color(0x1F6B3F);

    /** Dates as "1 Oct 2026". */
    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private Theme() {
    }

    static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    /** Text made safe to put inside a Swing HTML label. */
    static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** A price change described with an arrow, e.g. "R 169.00 → R 199.00 (+R 30.00, +18%)". */
    static String describe(subscriptiontracker.PriceChange change) {
        return subscriptiontracker.Display.describe(change).replace(" -> ", " → ");
    }

    static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold +10");
        label.setForeground(TEXT);
        return label;
    }

    static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold +3");
        label.setForeground(TEXT);
        return label;
    }

    static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.putClientProperty(FlatClientProperties.STYLE, "font: -1");
        label.setForeground(MUTED);
        return label;
    }

    static JButton primary(String text) {
        JButton button = new JButton(text);
        button.putClientProperty(FlatClientProperties.STYLE, "background: #245ba8; foreground: #ffffff;"
                + " hoverBackground: #1f4f93; pressedBackground: #1a4580; borderWidth: 0; focusWidth: 0;"
                + " default.background: #245ba8; default.foreground: #ffffff; default.hoverBackground: #1f4f93;"
                + " default.pressedBackground: #1a4580; default.borderWidth: 0;"
                + " font: bold; margin: 8,18,8,18");
        return button;
    }

    static JButton secondary(String text) {
        JButton button = new JButton(text);
        button.putClientProperty(FlatClientProperties.STYLE, "margin: 7,14,7,14");
        return button;
    }

    static JButton danger(String text) {
        JButton button = new JButton(text);
        button.putClientProperty(FlatClientProperties.STYLE,
                "foreground: #a3261a; borderColor: #e3b4ae; margin: 7,14,7,14");
        return button;
    }

    static Border padding(int top, int left, int bottom, int right) {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }

    static JProgressBar bar() {
        JProgressBar bar = new JProgressBar(0, 100);
        bar.putClientProperty(FlatClientProperties.STYLE, "arc: 8");
        bar.setBackground(TRACK);
        bar.setForeground(ACCENT);
        bar.setBorderPainted(false);
        return bar;
    }

    static void styleTable(JTable table) {
        table.setRowHeight(40);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(LINE);
        table.setFillsViewportHeight(true);
        table.setBackground(Color.WHITE);
        table.getTableHeader().setReorderingAllowed(false);
        javax.swing.table.TableCellRenderer header = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((t, value, selected, focused, row, column) -> {
            java.awt.Component c = header.getTableCellRendererComponent(t, value, selected, focused, row, column);
            if (c instanceof JLabel label) {
                boolean amount = java.math.BigDecimal.class.equals(t.getColumnClass(column));
                label.setHorizontalAlignment(amount ? JLabel.RIGHT : JLabel.LEFT);
                label.setForeground(MUTED);
                label.setBorder(amount ? padding(8, 8, 8, 14) : padding(8, 14, 8, 8));
            }
            return c;
        });
    }

    /**
     * A panel with a rounded white (or other) background and a thin border,
     * used for cards and the tables' frames.
     */
    static class RoundedPanel extends JPanel {

        private final Color fill;
        private final Color line;
        private final int arc;

        RoundedPanel(Color fill, Color line, int arc) {
            this.fill = fill;
            this.line = line;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.setColor(fill);
            g2.fill(shape);
            if (line != null) {
                g2.setColor(line);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(shape);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A table in a scroll pane inside a white rounded frame. */
    static RoundedPanel framed(JTable table) {
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.getViewport().setBackground(Color.WHITE);
        RoundedPanel frame = new RoundedPanel(Color.WHITE, BORDER, 14);
        frame.setLayout(new java.awt.BorderLayout());
        frame.setBorder(padding(4, 4, 4, 4));
        frame.add(scroll);
        return frame;
    }

    /** A white card with rounded corners and padding inside. */
    static RoundedPanel card() {
        RoundedPanel card = new RoundedPanel(Color.WHITE, BORDER, 14);
        card.setBorder(padding(16, 18, 16, 18));
        return card;
    }
}
