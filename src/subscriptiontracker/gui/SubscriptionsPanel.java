package subscriptiontracker.gui;

import static subscriptiontracker.Display.money;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import com.formdev.flatlaf.FlatClientProperties;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.Display;
import subscriptiontracker.PriceChange;
import subscriptiontracker.Subscription;
import subscriptiontracker.SubscriptionManager;

/**
 * The "Subscriptions" screen: free-trial reminders, summary cards, search,
 * a billing-cycle filter, the table, and buttons to add, edit, cancel and
 * remove.
 */
class SubscriptionsPanel extends JPanel implements TrackerWindow.Screen {

    private final TrackerWindow window;
    private final SubscriptionTableModel model = new SubscriptionTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<SubscriptionTableModel> sorter = new TableRowSorter<>(model);
    private final JTextField search = new JTextField(22);
    private final JComboBox<String> cycleFilter = new JComboBox<>(
            new String[] {"All cycles", "Weekly", "Monthly", "Quarterly", "Yearly"});
    private final JButton edit = Theme.secondary("Edit");
    private final JButton cancel = Theme.secondary("Cancel subscription");
    private final JButton remove = Theme.danger("Remove");
    private final JPanel trialBanner = new Theme.RoundedPanel(Theme.WARN_BG, Theme.WARN_BORDER, 12);
    private final JLabel trialText = new JLabel();
    private final SummaryCard perMonth = new SummaryCard("Per month");
    private final SummaryCard dueSoon = new SummaryCard("Due in the next 7 days");
    private final SummaryCard perYear = new SummaryCard("Per year");
    private final JLabel count = Theme.muted(" ");

    /** Free trials whose reminder was dismissed this session. */
    private final Set<Integer> dismissedTrials = new HashSet<>();
    private Subscription remindedTrial;

    SubscriptionsPanel(TrackerWindow window) {
        this.window = window;
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.CONTENT);
        setBorder(Theme.padding(24, 32, 12, 32));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(header());
        top.add(Box.createVerticalStrut(16));
        top.add(trialBanner());
        top.add(cards());
        top.add(Box.createVerticalStrut(16));
        top.add(toolbar());
        add(top, BorderLayout.NORTH);

        add(tableFrame(), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(count, BorderLayout.WEST);
        add(bottom, BorderLayout.SOUTH);
    }

    private JComponent header() {
        Box row = Box.createHorizontalBox();
        row.add(Theme.title("Subscriptions"));
        row.add(Box.createHorizontalGlue());
        JButton add = Theme.primary("+  Add subscription");
        add.addActionListener(e -> addSubscription());
        row.add(add);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JComponent trialBanner() {
        trialBanner.setLayout(new BoxLayout(trialBanner, BoxLayout.X_AXIS));
        trialBanner.setBorder(Theme.padding(10, 16, 10, 12));
        trialBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        trialText.setForeground(Theme.WARN_TEXT);
        trialBanner.add(trialText);
        trialBanner.add(Box.createHorizontalGlue());
        JButton cancelTrial = new JButton("Cancel it");
        cancelTrial.putClientProperty(FlatClientProperties.STYLE,
                "foreground: #6b4200; borderColor: #c98a2e; margin: 5,12,5,12");
        cancelTrial.addActionListener(e -> cancel(remindedTrial));
        JButton dismiss = new JButton("Dismiss");
        dismiss.putClientProperty(FlatClientProperties.STYLE,
                "foreground: #6b4200; background: #fff4e0; borderWidth: 0; focusWidth: 0; margin: 5,12,5,12");
        dismiss.addActionListener(e -> {
            dismissedTrials.add(remindedTrial.getId());
            refresh();
        });
        trialBanner.add(cancelTrial);
        trialBanner.add(Box.createHorizontalStrut(6));
        trialBanner.add(dismiss);

        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setBorder(Theme.padding(0, 0, 16, 0));
        holder.add(trialBanner);
        holder.setAlignmentX(Component.LEFT_ALIGNMENT);
        return holder;
    }

    private JComponent cards() {
        JPanel row = new JPanel(new GridLayout(1, 3, 16, 0));
        row.setOpaque(false);
        row.add(perMonth);
        row.add(dueSoon);
        row.add(perYear);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JComponent toolbar() {
        Box row = Box.createHorizontalBox();
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Search name or category");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        search.putClientProperty(FlatClientProperties.STYLE, "margin: 4,8,4,8");
        search.setMaximumSize(search.getPreferredSize());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });
        cycleFilter.setMaximumSize(cycleFilter.getPreferredSize());
        cycleFilter.addActionListener(e -> applyFilter());
        row.add(search);
        row.add(Box.createHorizontalStrut(10));
        JLabel billing = Theme.muted("Billing");
        billing.setLabelFor(cycleFilter);
        row.add(billing);
        row.add(Box.createHorizontalStrut(6));
        row.add(cycleFilter);
        row.add(Box.createHorizontalGlue());
        edit.addActionListener(e -> editSelected());
        cancel.addActionListener(e -> cancel(selected()));
        remove.addActionListener(e -> removeSelected());
        row.add(edit);
        row.add(Box.createHorizontalStrut(8));
        row.add(cancel);
        row.add(Box.createHorizontalStrut(8));
        row.add(remove);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JComponent tableFrame() {
        Theme.styleTable(table);
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        sorter.setComparator(SubscriptionTableModel.NAME,
                (a, b) -> String.CASE_INSENSITIVE_ORDER.compare(((Subscription) a).getName(), ((Subscription) b).getName()));
        sorter.setComparator(SubscriptionTableModel.CATEGORY, String.CASE_INSENSITIVE_ORDER);
        sorter.setSortKeys(List.of(new RowSorter.SortKey(SubscriptionTableModel.NEXT_PAYMENT, SortOrder.ASCENDING)));

        table.getColumnModel().getColumn(SubscriptionTableModel.NAME).setCellRenderer(new NameRenderer());
        table.getColumnModel().getColumn(SubscriptionTableModel.CATEGORY).setCellRenderer(Renderers.text());
        table.getColumnModel().getColumn(SubscriptionTableModel.COST).setCellRenderer(Renderers.money());
        table.getColumnModel().getColumn(SubscriptionTableModel.PER_MONTH).setCellRenderer(Renderers.money());
        table.getColumnModel().getColumn(SubscriptionTableModel.CYCLE).setCellRenderer(Renderers.cycle());
        table.getColumnModel().getColumn(SubscriptionTableModel.NEXT_PAYMENT).setCellRenderer(Renderers.dateDue());
        int[] widths = {260, 140, 110, 100, 110, 200};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                    editSelected();
                }
            }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "editRow");
        table.getActionMap().put("editRow", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                editSelected();
            }
        });

        return Theme.framed(table);
    }

    @Override
    public void refresh() {
        SubscriptionManager manager = window.manager();
        Subscription keep = selected();
        model.setRows(manager.getAll());
        if (keep != null) {
            for (int i = 0; i < model.getRowCount(); i++) {
                if (model.getSubscription(i).getId() == keep.getId()) {
                    int view = table.convertRowIndexToView(i);
                    if (view >= 0) {
                        table.getSelectionModel().setSelectionInterval(view, view);
                    }
                }
            }
        }
        updateCards(manager);
        updateTrialBanner(manager);
        updateButtons();
        refreshCount();
    }

    private void updateCards(SubscriptionManager manager) {
        LocalDate today = LocalDate.now();
        BigDecimal monthly = manager.getMonthlyTotal();
        perMonth.setValue(money(monthly));
        switch (manager.getBudgetStatus()) {
            case NO_BUDGET -> {
                perMonth.hideBar();
                perMonth.setDetail("No monthly budget set");
            }
            case UNDER -> budgetLine(manager, monthly, Theme.ACCENT, Theme.MUTED, "");
            case NEAR -> budgetLine(manager, monthly, Theme.WARN_BAR, Theme.WARN_TEXT, "");
            case OVER -> budgetLine(manager, monthly, Theme.DANGER, Theme.DANGER, "Over budget: ");
        }

        List<Subscription> due = manager.getUpcoming(today, 7);
        BigDecimal dueTotal = due.stream().map(Subscription::getCost).reduce(BigDecimal.ZERO, BigDecimal::add);
        dueSoon.setValue(money(dueTotal));
        dueSoon.setDetail(due.isEmpty() ? "Nothing due this week"
                : due.size() + " payment" + (due.size() == 1 ? "" : "s") + " · next: " + due.get(0).getName()
                        + " " + Display.dueIn(due.get(0).getNextPayment()));

        perYear.setValue(money(manager.getYearlyTotal()));
        int active = manager.getAll().size();
        perYear.setDetail(active + " active subscription" + (active == 1 ? "" : "s"));
    }

    private void budgetLine(SubscriptionManager manager, BigDecimal monthly, Color bar, Color text, String prefix) {
        BigDecimal budget = manager.getMonthlyBudget().orElseThrow();
        int percent = monthly.multiply(BigDecimal.valueOf(100)).divide(budget, 0, RoundingMode.DOWN).intValue();
        BigDecimal left = budget.subtract(monthly);
        String remaining = left.signum() >= 0 ? money(left) + " left" : money(left.negate()) + " over";
        perMonth.setToolTipText("Monthly budget: " + money(budget));
        perMonth.showBar(percent, bar);
        perMonth.setDetail(prefix + percent + "% of budget · " + remaining, text);
    }

    private void updateTrialBanner(SubscriptionManager manager) {
        List<Subscription> trials = new ArrayList<>();
        for (Subscription s : manager.getTrialsEndingWithin(LocalDate.now(), 7)) {
            if (!dismissedTrials.contains(s.getId())) {
                trials.add(s);
            }
        }
        if (trials.isEmpty()) {
            remindedTrial = null;
            trialBanner.getParent().setVisible(false);
            return;
        }
        remindedTrial = trials.get(0);
        String more = trials.size() > 1 ? " (and " + (trials.size() - 1) + " more ending this week)" : "";
        trialText.setText("<html><b>" + Theme.html(remindedTrial.getName()) + " free trial ends "
                + Display.dueIn(remindedTrial.getNextPayment()) + "</b> ("
                + Theme.DATE.format(remindedTrial.getNextPayment()) + "). You'll be charged "
                + Theme.html(money(remindedTrial.getCost())) + " unless you cancel." + more + "</html>");
        trialBanner.getParent().setVisible(true);
    }

    private void applyFilter() {
        String text = search.getText();
        int index = cycleFilter.getSelectedIndex();
        BillingCycle cycle = index <= 0 ? null : BillingCycle.values()[index - 1];
        sorter.setRowFilter(new RowFilter<SubscriptionTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends SubscriptionTableModel, ? extends Integer> entry) {
                return SubscriptionTableModel.matches(model.getSubscription(entry.getIdentifier()), text, cycle);
            }
        });
        refreshCount();
    }

    private void refreshCount() {
        int shown = table.getRowCount();
        int total = model.getRowCount();
        count.setText((shown == total ? total + " subscription" + (total == 1 ? "" : "s")
                : shown + " of " + total + " subscriptions") + " · double-click a row to edit");
    }

    private Subscription selected() {
        int view = table.getSelectedRow();
        return view < 0 ? null : model.getSubscription(table.convertRowIndexToModel(view));
    }

    private void updateButtons() {
        boolean any = selected() != null;
        edit.setEnabled(any);
        cancel.setEnabled(any);
        remove.setEnabled(any);
    }

    // ---- Actions ----

    private void addSubscription() {
        SubscriptionDialog dialog = new SubscriptionDialog(window, window.manager(), null);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            window.changed("Added \"" + dialog.getSubscription().getName() + "\".");
        }
    }

    private void editSelected() {
        Subscription sub = selected();
        if (sub == null) {
            return;
        }
        SubscriptionDialog dialog = new SubscriptionDialog(window, window.manager(), sub);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            PriceChange change = dialog.getRecordedPriceChange();
            window.changed("Updated \"" + sub.getName() + "\"."
                    + (change == null ? "" : " Price change recorded: " + Theme.describe(change) + "."));
        }
    }

    private void cancel(Subscription sub) {
        if (sub == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(window, "Cancel \"" + sub.getName() + "\"?\n\nIt moves to your "
                + "cancelled list, and you'll save " + money(sub.getMonthlyCost()) + " a month ("
                + money(sub.getYearlyCost()) + " a year).", "Cancel subscription", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            sub.cancel(LocalDate.now());
            window.changed("Cancelled \"" + sub.getName() + "\". You'll save " + money(sub.getMonthlyCost())
                    + " a month.");
        }
    }

    private void removeSelected() {
        Subscription sub = selected();
        if (sub == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(window, "Remove \"" + sub.getName() + "\" permanently?\n\n"
                + "To keep a record of what cancelling saves you, use Cancel subscription instead.",
                "Remove subscription", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION) {
            window.manager().remove(sub.getId());
            window.changed("Removed \"" + sub.getName() + "\".");
        }
    }

    // ---- Cell renderers ----

    /** The name in bold, with a "Free trial" badge and the note, if any. */
    private static class NameRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, "", selected, focused, row, column);
            Subscription s = (Subscription) value;
            StringBuilder text = new StringBuilder("<html><b>").append(Theme.html(s.getName())).append("</b>");
            if (s.isFreeTrial()) {
                text.append("&nbsp;&nbsp;<span style='background-color:#fff0d6; color:#7a4a00'>&nbsp;Free trial&nbsp;</span>");
            }
            if (s.hasNote()) {
                text.append("&nbsp;&nbsp;<span style='color:#5b6068'>· ").append(Theme.html(s.getNote())).append("</span>");
            }
            setText(text.append("</html>").toString());
            setToolTipText(s.hasNote() ? s.getNote() : null);
            setBorder(Theme.padding(0, 14, 0, 8));
            return this;
        }
    }
}
