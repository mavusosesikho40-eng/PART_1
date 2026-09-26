package subscriptiontracker.gui;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import javax.swing.table.AbstractTableModel;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.Subscription;

/**
 * The rows of the main table. Each column holds the underlying value (an
 * amount, a date, a billing cycle), so sorting by a column sorts by value,
 * and the renderers format it.
 */
public class SubscriptionTableModel extends AbstractTableModel {

    static final int NAME = 0;
    static final int CATEGORY = 1;
    static final int COST = 2;
    static final int CYCLE = 3;
    static final int PER_MONTH = 4;
    static final int NEXT_PAYMENT = 5;

    private static final String[] COLUMNS = {"Name", "Category", "Cost", "Cycle", "Per month", "Next payment"};
    private static final Class<?>[] TYPES = {
        Subscription.class, String.class, BigDecimal.class, BillingCycle.class, BigDecimal.class, LocalDate.class
    };

    private List<Subscription> rows = List.of();

    public void setRows(List<Subscription> rows) {
        this.rows = List.copyOf(rows);
        fireTableDataChanged();
    }

    public Subscription getSubscription(int modelRow) {
        return rows.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return TYPES[column];
    }

    @Override
    public Object getValueAt(int row, int column) {
        Subscription s = rows.get(row);
        return switch (column) {
            case NAME -> s;
            case CATEGORY -> s.getCategory();
            case COST -> s.getCost();
            case CYCLE -> s.getCycle();
            case PER_MONTH -> s.getMonthlyCost();
            case NEXT_PAYMENT -> s.getNextPayment();
            default -> throw new IndexOutOfBoundsException("column " + column);
        };
    }

    /**
     * Whether a subscription is shown for a search and a billing-cycle
     * filter: the search matches its name or category, ignoring case; a
     * null cycle means any cycle.
     */
    public static boolean matches(Subscription s, String search, BillingCycle cycle) {
        if (cycle != null && s.getCycle() != cycle) {
            return false;
        }
        String needle = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return needle.isEmpty()
                || s.getName().toLowerCase(Locale.ROOT).contains(needle)
                || s.getCategory().toLowerCase(Locale.ROOT).contains(needle);
    }
}
