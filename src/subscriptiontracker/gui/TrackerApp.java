package subscriptiontracker.gui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.nio.file.Path;
import java.util.Map;
import javax.swing.SwingUtilities;
import subscriptiontracker.DataFile;
import subscriptiontracker.Display;
import subscriptiontracker.SubscriptionStorage;

/**
 * Starts the Subscription Tracker window. The subscriptions are kept in
 * subscriptions.txt (or the file given as the first argument), the same
 * file the console app uses.
 */
public final class TrackerApp {

    private TrackerApp() {
    }

    public static void main(String[] args) {
        Path file = Path.of(args.length > 0 ? args[0] : "subscriptions.txt");
        DataFile data = new DataFile(new SubscriptionStorage(file));
        DataFile.LoadResult result = data.load();
        Display.setCurrency(data.manager().getCurrencySymbol());

        SwingUtilities.invokeLater(() -> {
            FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", "#245ba8"));
            FlatLightLaf.setup();
            TrackerWindow window = new TrackerWindow(data);
            window.setVisible(true);
            window.showLoadProblems(result);
        });
    }
}
