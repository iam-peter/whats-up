package app.whatsup.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import app.whatsup.widget.WidgetContent
import app.whatsup.widget.WidgetState

/** Current size of the widget; falls back to a 5 × 2 cell widget while placing. */
fun widgetSize(context: Context, appWidgetId: Int): DpSize {
    val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
    @Suppress("DEPRECATION")
    val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
    sizes?.firstOrNull()?.let { return DpSize(it.width.dp, it.height.dp) }
    val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
    val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    return if (w > 0 && h > 0) DpSize(w.dp, h.dp) else DpSize(330.dp, 180.dp)
}

/** Live preview (FR-C1): the real Glance content rendered to RemoteViews. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun WidgetPreview(state: WidgetState, size: DpSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(state, size) {
        views = GlanceRemoteViews().compose(context, size) { WidgetContent(state) }.remoteViews
    }
    AndroidView(
        factory = { FrameLayout(it) },
        update = { frame ->
            views?.let {
                frame.removeAllViews()
                frame.addView(it.apply(context, frame))
            }
        },
        modifier = modifier.size(size),
    )
}
