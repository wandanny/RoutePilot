package com.local.routepilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.local.routepilot.ui.loading.LoadingScreen
import com.local.routepilot.ui.main.MainScreen
import com.local.routepilot.ui.main.MainScreenViewModel
import com.local.routepilot.ui.theme.RoutePilotTheme
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val viewModel: MainScreenViewModel = koinViewModel()
      val ready by viewModel.ready.collectAsState(false)
      var minimumElapsed by remember { mutableStateOf(false) }
      androidx.compose.runtime.LaunchedEffect(Unit) {
        delay(800)
        minimumElapsed = true
      }

      RoutePilotTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          if (ready && minimumElapsed) {
            MainScreen(viewModel = viewModel)
          } else {
            LoadingScreen()
          }
        }
      }
    }
  }
}
