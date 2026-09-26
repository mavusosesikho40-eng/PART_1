package subscriptiontracker.gui;

import static subscriptiontracker.Display.money;
import static subscriptiontracker.Display.signedMoney;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import subscriptiontracker.CsvImporter;
import subscriptiontracker.SubscriptionManager;
import subscriptiontracker.SubscriptionManager.PriceChangeEntry;

/**
 * The "Spending & budget" screen: totals, the monthly budget, spending per
 * category, and the history of price changes.
 */
class SpendingPanel extends JPanel implements TrackerWindow.Screen {

    private final TrackerWindow window;
    private final SummaryCard total = new SummaryCard("Per month");
    private final SummaryCard budget;
    private final SummaryCard rises = new SummaryCard("Price changes, last 12 months");
    private final JPanel categories = new JPanel(new GridBagLayout());
    private final ListTableModel<PriceChangeEntry> history = new ListTableModel<PriceChangeEntry>()
            .column("Date", LocalDate.class, e -> e.change().date())
            .column("Name", String.class, e -> e.subscription().getName())
            .column("Change", String.class, e -> Theme.describe(e.change()));
    private final JTable historyTable = new JTable(history);
    private final JLabel noHistory = Theme.muted("No price changes yet. Edit a subscription's cost and the change is kept here.");

    SpendingPanel(TrackerWindow window) {
        this.window = window;
        JButton change = new JButton("Change");
        change.putClientProperty(com.formdev.flatlaf.FlatClientProperties.STYLE,
                "foreground: #245ba8; borderWidth: 0; focusWidth: 0; background: #ffffff; margin: 0,6,0,6");
        change.addActionListener(e -> changeBudget());
        budget = new SummaryCard("Monthly budget", change);

        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.CONTENT);
        setBorder(Theme.padding(24, 32, 12, 32));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = Theme.title("Spending & budget");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(16));
        JPanel cards = new JPanel(new GridLayout(1, 3, 16, 0));
        cards.setOpaque(false);
        cards.add(total);
        cards.add(budget);
        cards.add(rises);
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(cards);
        add(top, BorderLayout.NORTH);

        JPanel lower = new JPanel(new GridBagLayout());
        lower.setOpaque(false);
        for (JComponent section : List.of(categorySection(), historySection())) {
            // Equal preferred sizes, so the width is shared out by the weights alone.
            section.setPreferredSize(new java.awt.Dimension(100, 100));
            boolean first = lower.getComponentCount() == 0;
            lower.add(section, share(first ? 0 : 1, first ? 0.4 : 0.6, first ? 16 : 0));
        }
        add(lower, BorderLayout.CENTER);
    }

    private JComponent categorySection() {
        categories.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(categories, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        Theme.RoundedPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 14));
        card.add(Theme.heading("Per month by category"), BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JComponent historySection() {
        Theme.styleTable(historyTable);
        historyTable.setRowSelectionAllowed(false);
        historyTable.getColumnModel().getColumn(0).setCellRenderer(Renderers.date());
        historyTable.getColumnModel().getColumn(1).setCellRenderer(Renderers.text());
        historyTable.getColumnModel().getColumn(2).setCellRenderer(Renderers.text());
        int[] widths = {110, 120, 380};
        for (int i = 0; i < widths.length; i++) {
            historyTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = Theme.heading("Price history");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        noHistory.setAlignmentX(Component.LEFT_ALIGNMENT);
        heading.add(title);
        heading.add(noHistory);

        JPanel section = new JPanel(new BorderLayout(0, 10));
        section.setOpaque(false);
        section.add(heading, BorderLayout.NORTH);
        section.add(Theme.framed(historyTable), BorderLayout.CENTER);
        return section;
    }

    @Override
    public void refresh() {
        SubscriptionManager manager = window.manager();
        BigDecimal monthly = manager.getMonthlyTotal();
        total.setValue(money(monthly));
        total.setDetail(money(manager.getYearlyTotal()) + " a year");

        if (manager.getMonthlyBudget().isEmpty()) {
            budget.setValue("Not set", Theme.MUTED);
            budget.hideBar();
            budget.setDetail("Set one to be warned when you spend too much");
        } else {
            BigDecimal limit = manager.getMonthlyBudget().get();
            int percent = monthly.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.DOWN).intValue();
            BigDecimal left = limit.subtract(monthly);
            Color color = switch (manager.getBudgetStatus()) {
                case OVER -> Theme.DANGER;
                case NEAR -> Theme.WARN_BAR;
                default -> Theme.ACCENT;
            };
            budget.setValue(money(limit));
            budget.showBar(percent, color);
            budget.setDetail(percent + "% used · " + (left.signum() >= 0 ? money(left) + " left"
                    : money(left.negate()) + " over"), color == Theme.ACCENT ? Theme.MUTED : color);
        }

        BigDecimal change = manager.getMonthlyPriceChangeSince(LocalDate.now().minusYears(1));
        rises.setValue(signedMoney(change) + " a month",
                change.signum() > 0 ? Theme.DANGER : change.signum() < 0 ? Theme.SAVED : Theme.TEXT);
        rises.setDetail(change.signum() == 0 ? "No change to what you pay"
                : signedMoney(change.multiply(BigDecimal.valueOf(12))) + " a year");

        fillCategories(manager.getMonthlyByCategory(), monthly);

        List<PriceChangeEntry> entries = manager.getPriceChanges();
        history.setRows(entries);
        noHistory.setVisible(entries.isEmpty());
    }

    private void fillCategories(Map<String, BigDecimal> byCategory, BigDecimal monthly) {
        categories.removeAll();
        List<Map.Entry<String, BigDecimal>> sorted = new ArrayList<>(byCategory.entrySet());
        sorted.sort(Map.Entry.<String, BigDecimal>comparingByValue().reversed());
        int row = 0;
        for (Map.Entry<String, BigDecimal> e : sorted) {
            int percent = monthly.signum() == 0 ? 0
                    : e.getValue().multiply(BigDecimal.valueOf(100)).divide(monthly, 0, RoundingMode.HALF_UP).intValue();
            JLabel name = new JLabel(e.getKey());
            name.setForeground(Theme.TEXT);
            JProgressBar bar = Theme.bar();
            bar.setValue(percent);
            bar.setPreferredSize(new java.awt.Dimension(100, 8));
            JLabel amount = new JLabel(money(e.getValue()) + "  ·  " + percent + "%");
            amount.setForeground(Theme.MUTED);
            amount.setHorizontalAlignment(JLabel.RIGHT);

            categories.add(name, at(0, row, 0, GridBagConstraints.NONE));
            categories.add(bar, at(1, row, 1, GridBagConstraints.HORIZONTAL));
            categories.add(amount, at(2, row, 0, GridBagConstraints.NONE));
            row++;
        }
        if (sorted.isEmpty()) {
            categories.add(Theme.muted("Add subscriptions to see where your money goes."), at(0, 0, 1, GridBagConstraints.NONE));
        }
        categories.revalidate();
        categories.repaint();
    }

    /** A column of the lower half taking the given share of the width. */
    private static GridBagConstraints share(int x, double weight, int gap) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.weightx = weight;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, gap);
        return c;
    }

    private static GridBagConstraints at(int x, int y, double weight, int fill) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.weightx = weight;
        c.fill = fill;
        c.anchor = x == 2 ? GridBagConstraints.EAST : GridBagConstraints.WEST;
        c.insets = new Insets(7, x == 0 ? 0 : 14, 7, 0);
        return c;
    }

    private void changeBudget() {
        SubscriptionManager manager = window.manager();
        String current = manager.getMonthlyBudget().map(BigDecimal::toPlainString).orElse("");
        while (true) {
            Object input = JOptionPane.showInputDialog(window,
                    "Monthly budget for subscriptions.\nLeave it empty (or 0) to remove it.",
                    "Monthly budget", JOptionPane.PLAIN_MESSAGE, null, null, current);
            if (input == null) {
                return;
            }
            String text = input.toString().strip();
            BigDecimal amount;
            try {
                amount = text.isEmpty() ? BigDecimal.ZERO : CsvImporter.parseAmount(text);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(window, "Please enter an amount, e.g. 500.", "Monthly budget",
                        JOptionPane.WARNING_MESSAGE);
                current = text;
                continue;
            }
            manager.setMonthlyBudget(amount);
            window.changed(manager.getMonthlyBudget().isEmpty() ? "Monthly budget removed."
                    : "Monthly budget set to " + money(amount) + ".");
            return;
        }
    }
}
