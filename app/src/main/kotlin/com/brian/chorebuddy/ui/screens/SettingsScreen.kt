package com.brian.chorebuddy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brian.chorebuddy.data.ThinqApi
import com.brian.chorebuddy.data.ThinqDevice
import com.brian.chorebuddy.util.IntentUtils
import com.brian.chorebuddy.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("LG ThinQ", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Create a personal access token with permission to view devices and device status. The token stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { IntentUtils.openUrl(context, ThinqApi.PAT_URL) }) {
            Text("Open token page")
        }
        OutlinedTextField(
            value = ui.pat,
            onValueChange = viewModel::onPat,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Personal access token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        OutlinedTextField(
            value = ui.country,
            onValueChange = viewModel::onCountry,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Country code") },
            supportingText = { Text("US for a United States LG account") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        Button(onClick = viewModel::connect, enabled = !ui.busy) {
            Text(if (ui.busy) "Connecting" else "Connect")
        }
        if (ui.message.isNotBlank()) {
            Text(ui.message, color = MaterialTheme.colorScheme.secondary)
        }
        if (ui.error.isNotBlank()) {
            Text(ui.error, color = MaterialTheme.colorScheme.error)
        }
        if (ui.devices.isNotEmpty()) {
            Text("Devices", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "A WashTower can be one device. Choose it for both Wash and Dry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SlotPicker("Wash", ui.washerId, ui.devices, viewModel::onWasher)
            SlotPicker("Dry", ui.dryerId, ui.devices, viewModel::onDryer)
            SlotPicker("Dishes", ui.dishId, ui.devices, viewModel::onDish)
            Button(onClick = viewModel::saveSelection, enabled = !ui.busy) {
                Text("Save selection")
            }
        }
    }
}

@Composable
private fun SlotPicker(
    title: String,
    selectedId: String,
    devices: List<ThinqDevice>,
    onSelect: (String) -> Unit,
) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    DeviceChoice("Not linked", selectedId.isEmpty()) { onSelect("") }
    devices.forEach { device ->
        DeviceChoice(device.label, selectedId == device.id) { onSelect(device.id) }
    }
}

@Composable
private fun DeviceChoice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
