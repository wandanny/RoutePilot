package com.local.routepilot.ui.loading

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.routepilot.R

@Composable
fun LoadingScreen() {
  val pulse by rememberInfiniteTransition(label = "logo-pulse").animateFloat(
    initialValue = 0.72f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
    label = "logo-alpha",
  )
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
      Modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = "RoutePilot logo",
        modifier = Modifier.size(148.dp).alpha(pulse),
      )
      Text(
        "RoutePilot",
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(top = 24.dp),
      )
      Text(
        "Your route, sorted.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
      )
    }
  }
}
