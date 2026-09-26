package subscriptiontracker.mobile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import subscriptiontracker.mobile.data.Dates
import subscriptiontracker.mobile.data.Summary

/** The home-screen widget: what you spend a month, and the next payment. Tap it to open the app. */
class SubscriptionWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val summary = Summary.of(PhoneData.load(context), Dates.today())
        provideContent { Content(summary) }
    }

    @Composable
    private fun Content(summary: Summary) {
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color.White))
                .cornerRadius(16.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("Subscriptions per month", style = TextStyle(color = ColorProvider(Color(0xFF5B6068)), fontSize = 12.sp))
            Text(
                summary.monthly,
                style = TextStyle(color = ColorProvider(Color(0xFF1C1F23)), fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(summary.next, style = TextStyle(color = ColorProvider(Color(0xFF245BA8)), fontSize = 13.sp), maxLines = 2)
        }
    }
}

class SubscriptionWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SubscriptionWidget()
}
