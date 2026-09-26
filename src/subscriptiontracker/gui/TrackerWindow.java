package subscriptiontracker.gui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.WindowConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import subscriptiontracker.CsvExporter;
import subscriptiontracker.CsvImporter;
import subscriptiontracker.DataFile;
import subscriptiontracker.Display;
import subscriptiontracker.SubscriptionManager;

/**
 * The main window: a sidebar to switch between the four screens, and a
 * status line at the bottom. Every change goes through {@link #changed},
 * which saves straight away and redraws all the screens.
 */
public class TrackerWindow extends JFrame {

    private final DataFile data;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final List<Screen> screens = new ArrayList<>();
    private final JLabel status = Theme.muted(" ");
    private final JLabel saveInfo = Theme.muted(" ");
    private final ButtonGroup nav = new ButtonGroup();

    /** A screen that redraws itself from the current subscriptions. */
    interface Screen {
        void refresh();
    }

    public TrackerWindow(DataFile data) {
        super("Subscription Tracker");
        this.data = data;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 700));
        setSize(1280, 800);
        setLocationRelativeTo(null);

        SubscriptionsPanel subscriptions = new SubscriptionsPanel(this);
        addScreen("subscriptions", subscriptions);
        addScreen("upcoming", new UpcomingPanel(this));
        addScreen("spending", new SpendingPanel(this));
        addScreen("cancelled", new CancelledPanel(this));
        content.setBackground(Theme.CONTENT);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.CONTENT);
        main.add(content, BorderLayout.CENTER);
        main.add(statusBar(), BorderLayout.SOUTH);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(sidebar(), BorderLayout.WEST);
        getContentPane().add(main, BorderLayout.CENTER);

        refreshAll();
    }

    private void addScreen(String name, JPanel screen) {
        content.add(screen, name);
        screens.add((Screen) screen);
    }

    private JPanel sidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Theme.SIDEBAR);
        side.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER),
                Theme.padding(24, 14, 20, 14)));
        side.setPreferredSize(new Dimension(232, 0));

        JLabel app = new JLabel("Subscription Tracker");
        app.putClientProperty(FlatClientProperties.STYLE, "font: bold +1");
        app.setBorder(Theme.padding(0, 10, 20, 0));
        app.setAlignmentX(Component.LEFT_ALIGNMENT);
        side.add(app);

        side.add(navButton("Subscriptions", "subscriptions", true));
        side.add(Box.createVerticalStrut(4));
        side.add(navButton("Upcoming & trials", "upcoming", false));
        side.add(Box.createVerticalStrut(4));
        side.add(navButton("Spending & budget", "spending", false));
        side.add(Box.createVerticalStrut(4));
        side.add(navButton("Cancelled & savings", "cancelled", false));
        side.add(Box.createVerticalGlue());

        side.add(sideAction("Import CSV…", e -> importCsv()));
        side.add(sideAction("Export CSV…", e -> exportCsv()));
        side.add(sideAction("Settings (currency)", e -> currencySettings()));
        return side;
    }

    private JToggleButton navButton(String text, String screen, boolean selected) {
        JToggleButton button = new JToggleButton(text, selected);
        button.putClientProperty(FlatClientProperties.STYLE, "background: #f1f0eb; hoverBackground: #e6e4dc;"
                + " selectedBackground: #245ba8; selectedForeground: #ffffff; pressedBackground: #dcd9cf;"
                + " borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; margin: 10,12,10,12; arc: 10");
        styleSideButton(button);
        button.addActionListener(e -> {
            refreshAll();
            cards.show(content, screen);
        });
        button.addItemListener(e -> button.setFont(button.getFont().deriveFont(
                button.isSelected() ? java.awt.Font.BOLD : java.awt.Font.PLAIN)));
        button.setFont(button.getFont().deriveFont(selected ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
        nav.add(button);
        return button;
    }

    private JButton sideAction(String text, java.awt.event.ActionListener action) {
        JButton button = new JButton(text);
        button.putClientProperty(FlatClientProperties.STYLE, "background: #f1f0eb; foreground: #4f545c;"
                + " hoverBackground: #e6e4dc; pressedBackground: #dcd9cf; borderWidth: 0; focusWidth: 0;"
                + " innerFocusWidth: 0; margin: 8,12,8,12; arc: 10");
        styleSideButton(button);
        button.addActionListener(action);
        return button;
    }

    private static void styleSideButton(AbstractButton button) {
        button.setHorizontalAlignment(JButton.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    }

    private JPanel statusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.CONTENT);
        bar.setBorder(Theme.padding(0, 32, 14, 32));
        bar.add(status, BorderLayout.WEST);
        bar.add(saveInfo, BorderLayout.EAST);
        return bar;
    }

    SubscriptionManager manager() {
        return data.manager();
    }

    /**
     * Saves the subscriptions after a change and redraws every screen. If
     * saving fails the change stays on screen and the reason is shown.
     */
    void changed(String message) {
        try {
            data.save();
            setStatus(message);
        } catch (IOException e) {
            setStatus("Not saved.");
            JOptionPane.showMessageDialog(this, "Your change couldn't be saved:\n" + e.getMessage(),
                    "Not saved", JOptionPane.ERROR_MESSAGE);
        }
        refreshAll();
    }

    void setStatus(String message) {
        status.setText(message == null || message.isEmpty() ? " " : message);
    }

    void refreshAll() {
        for (Screen screen : screens) {
            screen.refresh();
        }
        if (data.isUnreadable()) {
            saveInfo.setText("Not saving: " + data.file().getFileName() + " couldn't be read");
            saveInfo.setForeground(Theme.DANGER);
        } else {
            saveInfo.setText("Saved automatically to " + data.file().getFileName());
            saveInfo.setForeground(Theme.MUTED);
        }
    }

    /** Tells the user about anything that went wrong while loading, once the window is showing. */
    public void showLoadProblems(DataFile.LoadResult result) {
        if (result.readError() != null) {
            JOptionPane.showMessageDialog(this, "Couldn't read " + data.file() + ":\n" + result.readError()
                    + "\n\nTo keep it safe, nothing will be saved until the app is restarted with a file it can read.",
                    "Couldn't read your subscriptions", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (result.skippedLines() > 0) {
            String where = result.unreadableCopy() != null
                    ? "The file as it was has been copied to " + result.unreadableCopy() + ", so those lines aren't lost."
                    : "Couldn't copy the file: " + result.copyError();
            JOptionPane.showMessageDialog(this, "Skipped " + result.skippedLines() + " line(s) of "
                    + data.file().getFileName() + " that couldn't be read.\n" + where,
                    "Some lines were skipped", JOptionPane.WARNING_MESSAGE);
        }
        if (result.saveError() != null) {
            JOptionPane.showMessageDialog(this, "Couldn't save " + data.file() + ":\n" + result.saveError(),
                    "Not saved", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Sidebar actions ----

    private void importCsv() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Import subscriptions from a CSV file");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        CsvImporter.Result result;
        try {
            result = CsvImporter.importFile(chooser.getSelectedFile().toPath(), manager(), LocalDate.now());
        } catch (IOException | RuntimeException e) {
            JOptionPane.showMessageDialog(this, "Couldn't import " + chooser.getSelectedFile().getName() + ":\n"
                    + e.getMessage(), "Import failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String summary = "Imported " + result.imported() + " subscription(s).";
        if (result.imported() > 0) {
            changed(summary);
        }
        StringBuilder message = new StringBuilder(summary);
        List<String> problems = result.problems();
        if (!problems.isEmpty()) {
            message.append("\n\nSkipped ").append(problems.size()).append(" row(s):");
            problems.stream().limit(10).forEach(p -> message.append("\n  ").append(p));
            if (problems.size() > 10) {
                message.append("\n  ...and ").append(problems.size() - 10).append(" more.");
            }
        }
        JOptionPane.showMessageDialog(this, message.toString(), "Import",
                problems.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private void exportCsv() {
        if (manager().isEmpty()) {
            JOptionPane.showMessageDialog(this, "You have no subscriptions to export.");
            return;
        }
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setDialogTitle("Export subscriptions to a CSV file");
        chooser.setSelectedFile(new File("subscriptions.csv"));
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase().endsWith(".csv")) {
            file = file.resolveSibling(file.getFileName() + ".csv");
        }
        if (Files.exists(file) && JOptionPane.showConfirmDialog(this, file.getFileName() + " already exists. Replace it?",
                "Export", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            CsvExporter.export(manager().getAll(), file);
            setStatus("Exported " + manager().getAll().size() + " subscription(s) to " + file.toAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Couldn't export:\n" + e.getMessage(), "Export failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void currencySettings() {
        String current = manager().getCurrencySymbol();
        while (true) {
            Object input = JOptionPane.showInputDialog(this,
                    "Currency symbol shown on every amount, e.g. R or $.\nLeave it empty for no symbol.",
                    "Currency symbol", JOptionPane.PLAIN_MESSAGE, null, null, current);
            if (input == null) {
                return;
            }
            String symbol = input.toString().strip();
            if (symbol.isEmpty() || Display.isValidCurrencySymbol(symbol)) {
                manager().setCurrencySymbol(symbol);
                Display.setCurrency(symbol);
                changed(symbol.isEmpty() ? "Currency symbol removed." : "Amounts now show " + symbol + ".");
                return;
            }
            JOptionPane.showMessageDialog(this, "Please use up to 5 letters or symbols, e.g. R or $.",
                    "Currency symbol", JOptionPane.WARNING_MESSAGE);
            current = symbol;
        }
    }
}
