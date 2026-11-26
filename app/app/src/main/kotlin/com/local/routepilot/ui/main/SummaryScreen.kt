package com.local.routepilot.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.local.routepilot.domain.Stop
import com.local.routepilot.domain.StopStatus

@Composable
fun SummaryScreen(stops: List<Stop>, onClose: () -> Unit) {
  val pending = stops.count { it.status == StopStatus.PENDING }
  val done = stops.count { it.status == StopStatus.DONE }
  val failed = stops.count { it.status == StopStatus.FAILED }
  Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Summary", style = MaterialTheme.typography.headlineSmall)
        TextButton(
          onClick = onClose,
          modifier = Modifier.semantics { contentDescription = "Close summary" },
        ) { Text("Close") }
      }
      LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Today", style = MaterialTheme.typography.titleMedium) }
        item { SummaryRow("Pending", pending) }
        item { SummaryRow("Done", done) }
        item { SummaryRow("Failed", failed) }
        item { Text("Stops", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp)) }
        items(stops, key = { it.id }) { stop ->
          Column {
            Text(stop.label.ifBlank { stop.address }, style = MaterialTheme.typography.bodyLarge)
            Text(
              "${stop.status.name} · ${stop.address}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        item {
          Text(
            "Map data © OpenStreetMap contributors (ODbL); postcodes © MCMC / GeoNames (CC BY 4.0); " +
              "POIs © Overture (CDLA Permissive 2.0)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun SummaryRow(title: String, count: Int) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Text(count.toString(), style = MaterialTheme.typography.bodyLarge)
  }
}
