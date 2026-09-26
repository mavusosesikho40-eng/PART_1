package subscriptiontracker.gui;

import static subscriptiontracker.Display.money;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.Display;
import subscriptiontracker.Subscription;
import subscriptiontracker.SubscriptionManager;

/**
 * The "Upcoming & trials" screen: every payment due in the chosen number of
 * days, and the free trials with when each one ends.
 */
class UpcomingPanel extends JPanel implements TrackerWindow.Screen {

    private static final int[] RANGES = {7, 14, 30, 90};

    /** One payment on one day. A weekly subscription has several in a month. */
    record Payment(LocalDate date, Subscription subscription) {
    }

    private final TrackerWindow window;
    private final JComboBox<String> range = new JComboBox<>(new String[] {
        "Next 7 days", "Next 14 days", "Next 30 days", "Next 90 days"});
    private final SummaryCard dueCard = new SummaryCard("Due");
    private final SummaryCard trialCard = new SummaryCard("Free trials");
    private final ListTableModel<Payment> payments = new ListTableModel<Payment>()
            .column("Date", LocalDate.class, Payment::date)
            .column("Name", String.class, p -> Display.displayName(p.subscription()))
            .column("Category", String.class, p -> p.subscription().getCategory())
            .column("Cycle", BillingCycle.class, p -> p.subscription().getCycle())
            .column("Amount", BigDecimal.class, p -> p.subscription().getCost());
    private final ListTableModel<Subscription> trials = new ListTableModel<Subscription>()
            .column("Name", String.class, Subscription::getName)
            .column("Trial ends", LocalDate.class, Subscription::getNextPayment)
            .column("Then costs", BigDecimal.class, Subscription::getCost)
            .column("Cycle", BillingCycle.class, Subscription::getCycle);
    private final JTable paymentTable = new JTable(payments);
    private final JTable trialTable = new JTable(trials);
    private final JLabel paymentsHeading = Theme.heading("Payments");
    private final JLabel noTrials = Theme.muted("No free trials. Tick \"This is a free trial\" when adding one to be reminded before it ends.");
    private final JButton keep = Theme.secondary("Keep it (I'll pay)");
    private final JButton cancel = Theme.danger("Cancel trial");

    UpcomingPanel(TrackerWindow window) {
        this.window = window;
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.CONTENT);
        setBorder(Theme.padding(24, 32, 12, 32));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(header());
        top.add(Box.createVerticalStrut(16));
        JPanel cards = new JPanel(new GridLayout(1, 2, 16, 0));
        cards.setOpaque(false);
        cards.add(dueCard);
        cards.add(trialCard);
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(cards);
        add(top, BorderLayout.NORTH);

        JPanel tables = new JPanel(new GridLayout(2, 1, 0, 16));
        tables.setOpaque(false);
        tables.add(paymentsSection());
        tables.add(trialsSection());
        add(tables, BorderLayout.CENTER);
    }

    private JComponent header() {
        Box row = Box.createHorizontalBox();
        row.add(Theme.title("Upcoming & trials"));
        row.add(Box.createHorizontalGlue());
        JLabel show = Theme.muted("Show");
        show.setLabelFor(range);
        row.add(show);
        row.add(Box.createHorizontalStrut(8));
        range.setSelectedIndex(2);
        range.setMaximumSize(range.getPreferredSize());
        range.addActionListener(e -> refresh());
        row.add(range);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JComponent paymentsSection() {
        Theme.styleTable(paymentTable);
        paymentTable.setRowSelectionAllowed(false);
        paymentTable.getColumnModel().getColumn(0).setCellRenderer(Renderers.dateDue());
        paymentTable.getColumnModel().getColumn(1).setCellRenderer(Renderers.text());
        paymentTable.getColumnModel().getColumn(2).setCellRenderer(Renderers.text());
        paymentTable.getColumnModel().getColumn(3).setCellRenderer(Renderers.cycle());
        paymentTable.getColumnModel().getColumn(4).setCellRenderer(Renderers.money());
        int[] widths = {220, 240, 160, 110, 120};
        for (int i = 0; i < widths.length; i++) {
            paymentTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JPanel section = new JPanel(new BorderLayout(0, 10));
        section.setOpaque(false);
        section.add(paymentsHeading, BorderLayout.NORTH);
        section.add(Theme.framed(paymentTable), BorderLayout.CENTER);
        return section;
    }

    private JComponent trialsSection() {
        Theme.styleTable(trialTable);
        trialTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trialTable.getColumnModel().getColumn(0).setCellRenderer(Renderers.text());
        trialTable.getColumnModel().getColumn(1).setCellRenderer(Renderers.dateDue());
        trialTable.getColumnModel().getColumn(2).setCellRenderer(Renderers.money());
        trialTable.getColumnModel().getColumn(3).setCellRenderer(Renderers.cycle());
        trialTable.getSelectionModel().addListSelectionListener(e -> updateButtons());

        keep.setToolTipText("You're keeping it, so it's no longer a trial: no more reminders");
        keep.addActionListener(e -> keepSelected());
        cancel.addActionListener(e -> cancelSelected());

        Box heading = Box.createHorizontalBox();
        heading.add(Theme.heading("Free trials"));
        heading.add(Box.createHorizontalStrut(12));
        heading.add(noTrials);
        heading.add(Box.createHorizontalGlue());
        heading.add(keep);
        heading.add(Box.createHorizontalStrut(8));
        heading.add(cancel);

        JPanel section = new JPanel(new BorderLayout(0, 10));
        section.setOpaque(false);
        section.add(heading, BorderLayout.NORTH);
        section.add(Theme.framed(trialTable), BorderLayout.CENTER);
        return section;
    }

    /**
     * Every payment from today up to and including today + days, soonest
     * first, counting each repeat of a weekly or monthly subscription.
     */
    static List<Payment> paymentsWithin(SubscriptionManager manager, LocalDate today, int days) {
        LocalDate end = today.plusDays(days);
        List<Payment> list = new ArrayList<>();
        for (Subscription s : manager.getAll()) {
            for (LocalDate d = s.getNextPayment(); !d.isAfter(end); d = s.getCycle().next(d, s.getBillingDay())) {
                if (!d.isBefore(today)) {
                    list.add(new Payment(d, s));
                }
            }
        }
        list.sort(Comparator.comparing(Payment::date)
                .thenComparing(p -> p.subscription().getName(), String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Override
    public void refresh() {
        SubscriptionManager manager = window.manager();
        LocalDate today = LocalDate.now();
        int days = RANGES[Math.max(0, range.getSelectedIndex())];

        List<Payment> due = paymentsWithin(manager, today, days);
        payments.setRows(due);
        BigDecimal total = due.stream().map(p -> p.subscription().getCost()).reduce(BigDecimal.ZERO, BigDecimal::add);
        dueCard.setValue(money(total));
        dueCard.setDetail(due.isEmpty() ? "Nothing due in the next " + days + " days"
                : due.size() + " payment" + (due.size() == 1 ? "" : "s") + " in the next " + days + " days");
        paymentsHeading.setText("Payments in the next " + days + " days");

        Subscription keepSelection = selectedTrial();
        List<Subscription> trialList = manager.getFreeTrials().stream()
                .sorted(Comparator.comparing(Subscription::getNextPayment)).toList();
        trials.setRows(trialList);
        if (keepSelection != null) {
            int index = trialList.indexOf(keepSelection);
            if (index >= 0) {
                trialTable.getSelectionModel().setSelectionInterval(index, index);
            }
        }
        noTrials.setVisible(trialList.isEmpty());
        if (trialList.isEmpty()) {
            trialCard.setValue("None");
            trialCard.setDetail("No free trials to watch");
        } else {
            Subscription next = trialList.get(0);
            boolean soon = !next.getNextPayment().isAfter(today.plusDays(7));
            trialCard.setValue(String.valueOf(trialList.size()));
            trialCard.setDetail("Next to end: " + next.getName() + " " + Display.dueIn(next.getNextPayment())
                    + ", then " + money(next.getCost()) + " " + next.getCycle().per(),
                    soon ? Theme.WARN_TEXT : Theme.MUTED);
        }
        updateButtons();
    }

    private Subscription selectedTrial() {
        int row = trialTable.getSelectedRow();
        return row < 0 ? null : trials.getItem(trialTable.convertRowIndexToModel(row));
    }

    private void updateButtons() {
        boolean any = selectedTrial() != null;
        keep.setEnabled(any);
        cancel.setEnabled(any);
    }

    private void keepSelected() {
        Subscription sub = selectedTrial();
        if (sub != null) {
            sub.setFreeTrial(false);
            window.changed("\"" + sub.getName() + "\" is no longer a trial. First payment: "
                    + Theme.DATE.format(sub.getNextPayment()) + ".");
        }
    }

    private void cancelSelected() {
        Subscription sub = selectedTrial();
        if (sub == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(window, "Cancel the \"" + sub.getName() + "\" free trial?\n\n"
                + "Remember to cancel it with the provider too, before " + Theme.DATE.format(sub.getNextPayment()) + ".",
                "Cancel trial", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            sub.cancel(LocalDate.now());
            window.changed("Cancelled the \"" + sub.getName() + "\" free trial.");
        }
    }
}
