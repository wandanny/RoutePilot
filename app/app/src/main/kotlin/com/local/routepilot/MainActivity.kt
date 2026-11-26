package com.local.routepilot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.local.routepilot.ui.loading.LoadingScreen
import com.local.routepilot.ui.main.MainScreen
import com.local.routepilot.ui.main.MainScreenViewModel
import com.local.routepilot.ui.theme.RoutePilotTheme
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
  private val covered = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: MainScreenViewModel = koinViewModel()
      val ready by viewModel.ready.collectAsState(false)
      var minimumElapsed by remember { mutableStateOf(false) }
      var permissionsResolved by remember { mutableStateOf(false) }
      var showRationale by remember { mutableStateOf(false) }
      var denied by remember { mutableStateOf(false) }
      var showPermissionMessage by remember { mutableStateOf(false) }
      var messageShown by remember { mutableStateOf(false) }

      val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          add(Manifest.permission.POST_NOTIFICATIONS)
        }
      }
      val missing = permissions.filter {
        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
      }

      val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
      ) { results ->
        if (results.values.any { !it }) denied = true
        applyLastKnownLocation(viewModel)
        permissionsResolved = true
      }

      val dismissRationale = {
        showRationale = false
        denied = true
        permissionsResolved = true
      }

      LaunchedEffect(Unit) {
        delay(800)
        minimumElapsed = true
      }

      LaunchedEffect(Unit) {
        when {
          missing.isEmpty() -> {
            applyLastKnownLocation(viewModel)
            permissionsResolved = true
          }
          missing.any { this@MainActivity.shouldShowRequestPermissionRationale(it) } -> showRationale = true
          else -> permissionLauncher.launch(missing.toTypedArray())
        }
      }

      LaunchedEffect(permissionsResolved, ready, minimumElapsed, denied) {
        if (permissionsResolved && ready && minimumElapsed && denied && !messageShown) {
          messageShown = true
          showPermissionMessage = true
        }
      }

      RoutePilotTheme {
        Box(Modifier.fillMaxSize()) {
          Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (ready && minimumElapsed && permissionsResolved) {
              MainScreen(viewModel = viewModel)
            } else {
              LoadingScreen()
            }
          }
          if (covered.value) AppCover()
        }
      }

      if (showRationale) {
        AlertDialog(
          onDismissRequest = dismissRationale,
          title = { Text("Permissions for RoutePilot") },
          text = {
            Text(
              "Location orders your stops from where you are. Notifications warn you before a delivery " +
                "window opens. RoutePilot still works without them.",
            )
          },
          confirmButton = {
            TextButton(onClick = {
              showRationale = false
              permissionLauncher.launch(missing.toTypedArray())
            }) { Text("Continue") }
          },
          dismissButton = { TextButton(onClick = dismissRationale) { Text("Not now") } },
        )
      }

      if (showPermissionMessage) {
        AlertDialog(
          onDismissRequest = { showPermissionMessage = false },
          title = { Text("RoutePilot") },
          text = { Text("Some features are unavailable. Allow permissions in Settings if you want them.") },
          confirmButton = { TextButton(onClick = { showPermissionMessage = false }) { Text("OK") } },
        )
      }
    }
  }

  override fun onPause() {
    super.onPause()
    covered.value = true
  }

  override fun onResume() {
    super.onResume()
    covered.value = false
  }

  private fun applyLastKnownLocation(viewModel: MainScreenViewModel) {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
      PackageManager.PERMISSION_GRANTED
    ) return
    val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
    val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
      .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
    location?.let { viewModel.setOrigin(it.latitude, it.longitude) }
  }
}

@Composable
private fun AppCover() {
  val colors = RoutePilotTheme.colors
  Surface(Modifier.fillMaxSize(), color = colors.darkSheet) {
    Column(
      Modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = "RoutePilot logo",
        modifier = Modifier.size(148.dp),
      )
      Text(
        "RoutePilot",
        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        color = colors.onDarkSheet,
        modifier = Modifier.padding(top = 24.dp),
      )
      Text(
        "Your route, sorted.",
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onDarkSheet.copy(alpha = 0.72f),
        modifier = Modifier.padding(top = 8.dp),
      )
    }
  }
}
