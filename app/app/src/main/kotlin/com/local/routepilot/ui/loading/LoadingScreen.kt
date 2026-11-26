package com.local.routepilot.ui.loading

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LoadingScreen() {
  val context = LocalContext.current
  val reduceMotion = remember(context) {
    runCatching {
      Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
  }
  val lineProgress = remember { Animatable(0f) }
  val fade = remember { Animatable(0f) }
  LaunchedEffect(reduceMotion) {
    if (reduceMotion) {
      lineProgress.snapTo(1f)
      fade.animateTo(1f, tween(200))
    } else {
      fade.snapTo(1f)
      lineProgress.animateTo(1f, tween(800))
    }
  }
  val accent = MaterialTheme.colorScheme.primary
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      Modifier
        .fillMaxSize()
        .padding(24.dp)
        .semantics(mergeDescendants = true) { contentDescription = "RoutePilot. Your route, sorted." },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Canvas(Modifier.size(190.dp, 120.dp)) {
        val scaleX = size.width / 190f
        val scaleY = size.height / 120f
        val start = Offset(24f * scaleX, 104f * scaleY)
        val control1 = Offset(78f * scaleX, 104f * scaleY)
        val control2 = Offset(104f * scaleX, 22f * scaleY)
        val end = Offset(166f * scaleX, 22f * scaleY)
        val steps = 48
        val visibleSteps = (steps * lineProgress.value).toInt()
        val path = Path().apply { moveTo(start.x, start.y) }
        for (step in 1..visibleSteps) {
          val t = step.toFloat() / steps
          val u = 1f - t
          val x = u * u * u * start.x + 3f * u * u * t * control1.x +
            3f * u * t * t * control2.x + t * t * t * end.x
          val y = u * u * u * start.y + 3f * u * u * t * control1.y +
            3f * u * t * t * control2.y + t * t * t * end.y
          path.lineTo(x, y)
        }
        drawPath(path, accent, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        val pinRadius = 8.dp.toPx()
        drawCircle(accent, pinRadius, start)
        drawCircle(accent, pinRadius, end)
        drawCircle(Color.White, 3.dp.toPx(), start)
        drawCircle(Color.White, 3.dp.toPx(), end)
      }
      Text(
        "RoutePilot",
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 24.dp).alpha(fade.value),
      )
      Text(
        "Your route, sorted.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp).alpha(fade.value),
      )
    }
  }
}
