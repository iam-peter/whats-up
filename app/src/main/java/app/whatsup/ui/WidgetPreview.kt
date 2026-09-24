package app.whatsup.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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

/**
 * Live preview (FR-C1): the real Glance content rendered to RemoteViews.
 * The views are laid out at the widget's real size and scaled down to fit,
 * so the preview shows the same fitting decisions as the home screen.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@Composable
fun WidgetPreview(state: WidgetState, size: DpSize, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var views by remember { mutableStateOf<RemoteViews?>(null) }
    LaunchedEffect(state, size) {
        views = GlanceRemoteViews().compose(context, size) { WidgetContent(state) }.remoteViews
    }
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val scale = minOf(1f, maxWidth / size.width)
        Box(Modifier.size(size.width * scale, size.height * scale)) {
            AndroidView(
                factory = { FrameLayout(it) },
                update = { frame ->
                    views?.let {
                        frame.removeAllViews()
                        frame.addView(it.apply(context, frame))
                    }
                },
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(size)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            )
        }
    }
}
