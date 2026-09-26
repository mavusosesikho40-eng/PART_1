package subscriptiontracker.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;

/**
 * A read-only table of items, with each column given as a name and a way
 * to get its value from an item. Used for the smaller tables.
 */
class ListTableModel<T> extends AbstractTableModel {

    private record Column<T>(String name, Class<?> type, Function<T, ?> value) {
    }

    private final List<Column<T>> columns = new ArrayList<>();
    private List<T> rows = List.of();

    ListTableModel<T> column(String name, Class<?> type, Function<T, ?> value) {
        columns.add(new Column<>(name, type, value));
        return this;
    }

    void setRows(List<T> rows) {
        this.rows = List.copyOf(rows);
        fireTableDataChanged();
    }

    T getItem(int modelRow) {
        return rows.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).name();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return columns.get(column).type();
    }

    @Override
    public Object getValueAt(int row, int column) {
        return columns.get(column).value().apply(rows.get(row));
    }
}
