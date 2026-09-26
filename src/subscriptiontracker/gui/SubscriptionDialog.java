package subscriptiontracker.gui;

import static subscriptiontracker.Display.money;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeSet;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.PriceChange;
import subscriptiontracker.Subscription;
import subscriptiontracker.SubscriptionManager;

/**
 * The dialog for adding a subscription or editing one. What's typed is
 * checked and saved by {@link SubscriptionForm}; this only lays it out.
 */
class SubscriptionDialog extends JDialog {

    private final SubscriptionManager manager;
    private final Subscription editing;
    private final SubscriptionForm form;

    private final JTextField name = new JTextField(24);
    private final JTextField cost = new JTextField(10);
    private final JLabel was = Theme.muted(" ");
    private final JCheckBox recordChange = new JCheckBox("Record this as a price change");
    private final JLabel changeHint = Theme.muted(" ");
    private JLabel changeLabel;
    private final Map<BillingCycle, JToggleButton> cycles = new java.util.EnumMap<>(BillingCycle.class);
    private final JCheckBox freeTrial = new JCheckBox("This is a free trial (remind me before it ends)");
    private final JTextField date = new JTextField(12);
    private final JLabel dateHint = Theme.muted(" ");
    private final JComboBox<String> category = new JComboBox<>();
    private final JTextArea note = new JTextArea(2, 24);
    private final JLabel error = new JLabel(" ");

    /** False while the fields are being filled in, so the hints don't read half-filled fields. */
    private boolean ready;
    private boolean saved;
    private Subscription result;
    private PriceChange recordedChange;

    /**
     * @param editing the subscription to edit, or null to add a new one
     */
    SubscriptionDialog(TrackerWindow owner, SubscriptionManager manager, Subscription editing) {
        super(owner, editing == null ? "Add subscription" : "Edit " + editing.getName(), true);
        this.manager = manager;
        this.editing = editing;
        this.form = editing == null ? new SubscriptionForm() : SubscriptionForm.from(editing);
        if (editing == null) {
            form.setDate(LocalDate.now().toString());
        }

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(java.awt.Color.WHITE);
        root.setBorder(Theme.padding(22, 26, 20, 26));
        root.add(Theme.heading(editing == null ? "Add subscription" : "Edit subscription"), BorderLayout.NORTH);
        root.add(fields(), BorderLayout.CENTER);
        root.add(buttons(), BorderLayout.SOUTH);
        setContentPane(root);

        fillFields();
        ready = true;
        update();
        pack();
        setMinimumSize(new Dimension(Math.max(getWidth(), 540), getHeight()));
        setResizable(false);
        setLocationRelativeTo(owner);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private JComponent fields() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        int row = 0;

        name.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g. Netflix");
        addRow(panel, row++, "Name", name);

        cost.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "0.00");
        String symbol = manager.getCurrencySymbol();
        if (!symbol.isEmpty()) {
            JLabel prefix = new JLabel(symbol + " ");
            prefix.setForeground(Theme.MUTED);
            cost.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, prefix);
        }
        Box costRow = Box.createHorizontalBox();
        costRow.add(cost);
        costRow.add(Box.createHorizontalStrut(10));
        costRow.add(was);
        costRow.add(Box.createHorizontalGlue());
        cost.setMaximumSize(cost.getPreferredSize());
        addRow(panel, row++, "Cost", costRow);

        recordChange.setOpaque(false);
        JPanel change = new JPanel();
        change.setOpaque(false);
        change.setLayout(new javax.swing.BoxLayout(change, javax.swing.BoxLayout.Y_AXIS));
        recordChange.setAlignmentX(Component.LEFT_ALIGNMENT);
        changeHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        changeHint.setBorder(Theme.padding(0, 26, 0, 0));
        change.add(recordChange);
        change.add(changeHint);
        changeLabel = addRow(panel, row++, "", change);

        JPanel cycleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        cycleRow.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (BillingCycle c : BillingCycle.values()) {
            JToggleButton button = new JToggleButton(c.getLabel());
            button.putClientProperty(FlatClientProperties.STYLE, "selectedBackground: #245ba8;"
                    + " selectedForeground: #ffffff; margin: 5,14,5,14");
            button.addActionListener(e -> update());
            group.add(button);
            cycles.put(c, button);
            cycleRow.add(button);
            cycleRow.add(Box.createHorizontalStrut(4));
        }
        addRow(panel, row++, "Billed", cycleRow);

        freeTrial.setOpaque(false);
        addRow(panel, row++, "", freeTrial);

        date.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "2026-10-01");
        JPanel dateBox = new JPanel();
        dateBox.setOpaque(false);
        dateBox.setLayout(new javax.swing.BoxLayout(dateBox, javax.swing.BoxLayout.Y_AXIS));
        date.setMaximumSize(date.getPreferredSize());
        date.setAlignmentX(Component.LEFT_ALIGNMENT);
        dateHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        dateHint.setBorder(Theme.padding(4, 2, 0, 0));
        dateBox.add(date);
        dateBox.add(dateHint);
        addRow(panel, row++, editing == null ? "First payment" : "Next payment", dateBox);

        category.setEditable(true);
        TreeSet<String> known = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Subscription s : manager.getAllIncludingCancelled()) {
            known.add(s.getCategory());
        }
        known.addAll(java.util.List.of("Entertainment", "Music", "Software", "Utilities", "Other"));
        for (String c : known) {
            category.addItem(c);
        }
        addRow(panel, row++, "Category", category);

        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Optional, e.g. shared with family");
        note.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "none");
        note.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "none");
        JScrollPane noteScroll = new JScrollPane(note);
        addRow(panel, row++, "Note", noteScroll);

        error.setForeground(Theme.DANGER);
        addRow(panel, row, "", error);

        DocumentListener changed = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update();
            }
        };
        cost.getDocument().addDocumentListener(changed);
        date.getDocument().addDocumentListener(changed);
        recordChange.addActionListener(e -> update());
        return panel;
    }

    private static JLabel addRow(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.NORTHWEST;
        left.insets = new Insets(6, 0, 6, 16);
        JLabel text = new JLabel(label);
        text.setForeground(Theme.MUTED);
        text.setLabelFor(field);
        panel.add(text, left);

        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.anchor = GridBagConstraints.WEST;
        right.insets = new Insets(2, 0, 2, 0);
        panel.add(field, right);
        return text;
    }

    private JComponent buttons() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        row.setOpaque(false);
        JButton cancel = Theme.secondary("Cancel");
        cancel.addActionListener(e -> dispose());
        JButton save = Theme.primary(editing == null ? "Add" : "Save");
        save.addActionListener(e -> save());
        row.add(cancel);
        row.add(save);
        getRootPane().setDefaultButton(save);
        return row;
    }

    private void fillFields() {
        name.setText(form.getName());
        cost.setText(form.getCost());
        cycles.get(form.getCycle()).setSelected(true);
        freeTrial.setSelected(form.isFreeTrial());
        date.setText(form.getDate());
        category.setSelectedItem(form.getCategory().isEmpty() ? "Other" : form.getCategory());
        note.setText(form.getNote());
        recordChange.setSelected(form.isRecordPriceChange());
    }

    /** Copies the fields into the form. */
    private void readFields() {
        form.setName(name.getText());
        form.setCost(cost.getText());
        for (Map.Entry<BillingCycle, JToggleButton> e : cycles.entrySet()) {
            if (e.getValue().isSelected()) {
                form.setCycle(e.getKey());
            }
        }
        form.setFreeTrial(freeTrial.isSelected());
        form.setDate(date.getText());
        Object chosen = category.getEditor().getItem();
        form.setCategory(chosen == null ? "" : chosen.toString());
        form.setNote(note.getText());
        form.setRecordPriceChange(recordChange.isSelected());
    }

    /** Refreshes the hints under the fields as things are typed. */
    private void update() {
        if (!ready) {
            return;
        }
        readFields();
        LocalDate today = LocalDate.now();
        String billing = form.billingDescription(today);
        dateHint.setText(billing == null ? "Type a date like 2026-10-01 or 01/10/2026" : billing);

        PriceChange pending = editing == null ? null : form.pendingPriceChange(editing, today);
        was.setText(editing != null && pending != null ? "was " + money(editing.getCost()) : " ");
        recordChange.getParent().setVisible(pending != null);
        changeLabel.setVisible(pending != null);
        if (pending != null) {
            changeHint.setText(recordChange.isSelected()
                    ? Theme.describe(pending) + ", kept in your price history"
                    : "Just correcting a mistake: the old price is not kept");
        }
        if (isShowing()) {
            pack();
        }
    }

    private void save() {
        readFields();
        String problem = form.problem();
        if (problem != null) {
            error.setText(problem);
            return;
        }
        LocalDate today = LocalDate.now();
        if (editing == null) {
            result = form.add(manager, today);
        } else {
            recordedChange = form.applyTo(editing, today);
            result = editing;
        }
        saved = true;
        dispose();
    }

    boolean isSaved() {
        return saved;
    }

    /** The subscription that was added or edited, once saved. */
    Subscription getSubscription() {
        return result;
    }

    /** The price change recorded by saving an edit, or null if none was. */
    PriceChange getRecordedPriceChange() {
        return recordedChange;
    }
}
