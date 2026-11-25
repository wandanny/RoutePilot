package com.local.routepilot

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.local.routepilot.data.AiSettings
import com.local.routepilot.data.DeviceLocationProvider
import com.local.routepilot.data.LucifClient
import com.local.routepilot.data.OsrmRoute
import com.local.routepilot.ui.map.NavigationViewModel
import com.local.routepilot.data.RoutePlanner
import com.local.routepilot.data.RouteRepository
import com.local.routepilot.notifications.WindowWorker
import com.local.routepilot.ui.main.MainScreenViewModel
import com.local.routepilot.ui.scan.ScanViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

class RoutePilotApp : Application() {
  override fun onCreate() {
    super.onCreate()
    startKoin {
      androidContext(this@RoutePilotApp)
      modules(
        module {
          single { RouteRepository(androidContext()) }
          single { AiSettings(androidContext()) }
          single { LucifClient(get()) }
          single { RoutePlanner(get(), get()) }
          single { OsrmRoute() }
          single { DeviceLocationProvider(androidContext()) }
          viewModel { MainScreenViewModel(get(), get(), get()) }
          viewModel { ScanViewModel(get(), get(), get()) }
          viewModel { NavigationViewModel(get(), get()) }
        },
      )
    }
    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
      "delivery-window-check",
      ExistingPeriodicWorkPolicy.UPDATE,
      PeriodicWorkRequestBuilder<WindowWorker>(15, TimeUnit.MINUTES).build(),
    )
  }
}
