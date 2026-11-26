package com.local.routepilot.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.local.routepilot.domain.Stop

@Composable
fun BatchSheet(
  batches: List<List<Stop>>,
  batchIndex: Int,
  isLocked: Boolean,
  onOpen: (Int) -> Unit,
  onNext: () -> Unit,
  onClose: () -> Unit,
) {
  Dialog(onDismissRequest = onClose) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
    ) {
      Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Row(
          Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Google Maps", style = MaterialTheme.typography.headlineSmall)
          TextButton(
            onClick = onClose,
            modifier = Modifier.semantics { contentDescription = "Close batch sheet" },
          ) { Text("Close") }
        }
        if (batchIndex in batches.indices) {
          val current = batches[batchIndex]
          Text(
            "BATCH ${batchIndex + 1} OF ${batches.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp),
          )
          Text("Open in Google Maps", style = MaterialTheme.typography.titleLarge)
          Text(
            "${batches.size} batches of up to 9 stops. Open each batch in turn.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (isLocked) {
            Text(
              "Order locked — batches follow the saved route order.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            batches.forEachIndexed { index, batch ->
              FilterChip(
                selected = index == batchIndex,
                onClick = { onOpen(index) },
                label = { Text("${index + 1} · ${batch.size}") },
                modifier = Modifier
                  .weight(1f)
                  .semantics { contentDescription = "Batch ${index + 1}, ${batch.size} stops" },
              )
            }
          }
          Text(
            "Batch ${batchIndex + 1} contains ${current.size} stops.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
          )
          Button(
            onClick = { onOpen(batchIndex) },
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 12.dp)
              .semantics { contentDescription = "Open batch ${batchIndex + 1}" },
          ) { Text("Open batch ${batchIndex + 1}") }
          if (batchIndex < batches.lastIndex) {
            OutlinedButton(
              onClick = onNext,
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .semantics { contentDescription = "Next batch" },
            ) { Text("Next batch") }
          } else {
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Close") }
          }
        } else {
          Text(
            "No pending stops.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp),
          )
          TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Close") }
        }
      }
    }
  }
}
