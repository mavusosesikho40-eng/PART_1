package subscriptiontracker.gui;

import static subscriptiontracker.Display.money;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import subscriptiontracker.Subscription;
import subscriptiontracker.SubscriptionManager;

/**
 * The "Cancelled & savings" screen: what cancelling has saved so far, and
 * the cancelled subscriptions, which can be restored or removed.
 */
class CancelledPanel extends JPanel implements TrackerWindow.Screen {

    private final TrackerWindow window;
    private final SummaryCard savedSoFar = new SummaryCard("Saved so far");
    private final SummaryCard perMonth = new SummaryCard("Saving per month");
    private final SummaryCard perYear = new SummaryCard("Saving per year");
    private final ListTableModel<Subscription> model = new ListTableModel<Subscription>()
            .column("Name", String.class, Subscription::getName)
            .column("Category", String.class, Subscription::getCategory)
            .column("Cancelled on", LocalDate.class, Subscription::getCancelledOn)
            .column("Was per month", BigDecimal.class, Subscription::getMonthlyCost)
            .column("Saved so far", BigDecimal.class, s -> s.getSavedSoFar(LocalDate.now()));
    private final JTable table = new JTable(model);
    private final JLabel empty = Theme.muted("Nothing cancelled yet. Cancel a subscription and what it saves you shows up here.");
    private final JButton restore = Theme.secondary("Restore (signed up again)");
    private final JButton remove = Theme.danger("Remove");

    CancelledPanel(TrackerWindow window) {
        this.window = window;
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.CONTENT);
        setBorder(Theme.padding(24, 32, 12, 32));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel title = Theme.title("Cancelled & savings");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(title);
        top.add(Box.createVerticalStrut(16));
        JPanel cards = new JPanel(new GridLayout(1, 3, 16, 0));
        cards.setOpaque(false);
        cards.add(savedSoFar);
        cards.add(perMonth);
        cards.add(perYear);
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(cards);
        top.add(Box.createVerticalStrut(16));
        top.add(toolbar());
        add(top, BorderLayout.NORTH);

        Theme.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setCellRenderer(Renderers.text());
        table.getColumnModel().getColumn(1).setCellRenderer(Renderers.text());
        table.getColumnModel().getColumn(2).setCellRenderer(Renderers.date());
        table.getColumnModel().getColumn(3).setCellRenderer(Renderers.money());
        table.getColumnModel().getColumn(4).setCellRenderer(Renderers.money());
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        add(Theme.framed(table), BorderLayout.CENTER);
    }

    private JComponent toolbar() {
        Box row = Box.createHorizontalBox();
        row.add(empty);
        row.add(Box.createHorizontalGlue());
        restore.addActionListener(e -> restoreSelected());
        remove.addActionListener(e -> removeSelected());
        row.add(restore);
        row.add(Box.createHorizontalStrut(8));
        row.add(remove);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    @Override
    public void refresh() {
        SubscriptionManager manager = window.manager();
        LocalDate today = LocalDate.now();
        List<Subscription> cancelled = manager.getCancelled();
        model.setRows(cancelled);

        savedSoFar.setValue(money(manager.getSavedSoFar(today)), Theme.SAVED);
        savedSoFar.setDetail("Payments you didn't have to make");
        BigDecimal monthly = manager.getCancelledMonthlyTotal();
        BigDecimal yearly = cancelled.stream().map(Subscription::getYearlyCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        perMonth.setValue(money(monthly));
        perMonth.setDetail(cancelled.size() + " cancelled subscription" + (cancelled.size() == 1 ? "" : "s"));
        perYear.setValue(money(yearly));
        perYear.setDetail("If none of them are restored");
        empty.setVisible(cancelled.isEmpty());
        updateButtons();
    }

    private Subscription selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.getItem(table.convertRowIndexToModel(row));
    }

    private void updateButtons() {
        boolean any = selected() != null;
        restore.setEnabled(any);
        remove.setEnabled(any);
    }

    private void restoreSelected() {
        Subscription sub = selected();
        if (sub == null) {
            return;
        }
        sub.reactivate(LocalDate.now());
        window.changed("Restored \"" + sub.getName() + "\". Next payment: "
                + Theme.DATE.format(sub.getNextPayment()) + ".");
    }

    private void removeSelected() {
        Subscription sub = selected();
        if (sub == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(window, "Remove \"" + sub.getName() + "\" permanently?\n\n"
                + "What it saved will no longer be counted.", "Remove subscription",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            window.manager().remove(sub.getId());
            window.changed("Removed \"" + sub.getName() + "\".");
        }
    }
}
