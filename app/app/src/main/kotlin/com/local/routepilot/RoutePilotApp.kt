package com.local.routepilot

import android.app.Application
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.local.routepilot.data.AiSettings
import com.local.routepilot.data.DeviceLocationProvider
import com.local.routepilot.data.GeocodingService
import com.local.routepilot.data.LucifClient
import com.local.routepilot.data.OsrmRoute
import com.local.routepilot.ui.map.NavigationViewModel
import com.local.routepilot.data.RoutePlanner
import com.local.routepilot.data.RouteRepository
import com.local.routepilot.notifications.DeliveryAlarmReceiver
import com.local.routepilot.notifications.DeliveryNotifications
import com.local.routepilot.notifications.WindowWorker
import com.local.routepilot.ui.main.MainScreenViewModel
import com.local.routepilot.ui.scan.ScanViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

class RoutePilotApp : Application() {
  private val deliveryReceiver = DeliveryAlarmReceiver()

  override fun onCreate() {
    super.onCreate()
    val filter = IntentFilter(DeliveryNotifications.ACTION_DELIVERY_WINDOW)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      registerReceiver(deliveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      registerReceiver(deliveryReceiver, filter)
    }
    startKoin {
      androidContext(this@RoutePilotApp)
      modules(
        module {
          single { RouteRepository(androidContext()) }
          single { AiSettings(androidContext()) }
          single { LucifClient(get()) }
          single { GeocodingService(get(), androidContext()) }
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
