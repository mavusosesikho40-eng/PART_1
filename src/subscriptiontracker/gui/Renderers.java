package subscriptiontracker.gui;

import java.awt.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import subscriptiontracker.BillingCycle;
import subscriptiontracker.Display;

/** How the tables show amounts, dates and text, with the same padding everywhere. */
final class Renderers {

    private Renderers() {
    }

    /** A cell showing a value as text, padded, optionally right-aligned. */
    private static class Cell extends DefaultTableCellRenderer {

        private final Function<Object, String> format;
        private final boolean right;

        Cell(Function<Object, String> format, boolean right) {
            this.format = format;
            this.right = right;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value == null ? "" : format.apply(value),
                    selected, focused, row, column);
            setHorizontalAlignment(right ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(right ? Theme.padding(0, 8, 0, 14) : Theme.padding(0, 14, 0, 8));
            if (!selected) {
                setForeground(Theme.TEXT);
            }
            return this;
        }
    }

    static TableCellRenderer text() {
        return new Cell(Object::toString, false);
    }

    /** Amounts, right-aligned, with the currency symbol. */
    static TableCellRenderer money() {
        return new Cell(v -> Display.money((BigDecimal) v), true);
    }

    static TableCellRenderer cycle() {
        return new Cell(v -> ((BillingCycle) v).getLabel(), false);
    }

    /** "1 Oct 2026". */
    static TableCellRenderer date() {
        return new Cell(v -> Theme.DATE.format((LocalDate) v), false);
    }

    /** "1 Oct 2026 · in 5 days". */
    static TableCellRenderer dateDue() {
        return new Cell(v -> "<html>" + Theme.DATE.format((LocalDate) v)
                + "&nbsp;&nbsp;<span style='color:#5b6068'>· " + Display.dueIn((LocalDate) v) + "</span></html>",
                false);
    }
}
