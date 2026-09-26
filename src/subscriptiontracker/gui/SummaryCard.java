package subscriptiontracker.gui;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JProgressBar;

/**
 * One of the cards at the top of a screen: a caption, a big value, an
 * optional bar (the budget) and a line of detail.
 */
class SummaryCard extends Theme.RoundedPanel {

    private final JLabel value = new JLabel(" ");
    private final JProgressBar bar = Theme.bar();
    private final JLabel detail = Theme.muted(" ");

    SummaryCard(String caption) {
        this(caption, null);
    }

    /** A card with an extra control (e.g. a Change button) at the top right. */
    SummaryCard(String caption, JComponent action) {
        super(Color.WHITE, Theme.BORDER, 14);
        setBorder(Theme.padding(16, 18, 16, 18));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        Box top = Box.createHorizontalBox();
        top.add(Theme.muted(caption));
        top.add(Box.createHorizontalGlue());
        if (action != null) {
            top.add(action);
        }
        top.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(top);
        add(Box.createVerticalStrut(6));

        value.putClientProperty(FlatClientProperties.STYLE, "font: bold +12");
        value.setForeground(Theme.TEXT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(value);
        add(Box.createVerticalStrut(8));

        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        bar.setPreferredSize(new Dimension(100, 8));
        bar.setVisible(false);
        add(bar);
        add(Box.createVerticalStrut(8));

        detail.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(detail);
    }

    void setValue(String text, Color color) {
        value.setText(text);
        value.setForeground(color);
    }

    void setValue(String text) {
        setValue(text, Theme.TEXT);
    }

    void setDetail(String text, Color color) {
        detail.setText(text);
        detail.setForeground(color);
    }

    void setDetail(String text) {
        setDetail(text, Theme.MUTED);
    }

    /** Shows the bar filled to the given percent in the given colour. */
    void showBar(int percent, Color color) {
        bar.setValue(Math.max(0, Math.min(100, percent)));
        bar.setForeground(color);
        bar.setVisible(true);
    }

    void hideBar() {
        bar.setVisible(false);
    }
}
