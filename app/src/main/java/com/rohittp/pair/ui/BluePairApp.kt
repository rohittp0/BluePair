package com.rohittp.pair.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rohittp.pair.R

private const val BLUE_PAIR_REPO_URL = "https://github.com/rohittp0/BluePair"

private data class BluePairDestination(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int
)

private val destinations = listOf(
    BluePairDestination("home", Icons.Default.Home, R.string.home_title),
    BluePairDestination("configure", Icons.Default.Settings, R.string.nav_configure),
    BluePairDestination("channels", Icons.Default.Speaker, R.string.nav_channels),
    BluePairDestination("about", Icons.Default.Info, R.string.nav_about)
)

@Composable
fun BluePairApp(viewModel: BluePairViewModel = viewModel()) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { androidx.compose.material3.Icon(destination.icon, null) },
                        label = { Text(stringResource(destination.labelRes)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") {
                HomeScreen(
                    uiState = uiState,
                    onToggleOutput = viewModel::toggleOutputMode
                )
            }
            composable("configure") {
                ConfigureScreen(
                    uiState = uiState,
                    onPermissionChanged = viewModel::onBluetoothPermissionChanged,
                    onToggleDeviceSelection = viewModel::toggleDeviceSelection
                )
            }
            composable("channels") {
                ChannelAssignmentScreen(
                    uiState = uiState,
                    onAssignLeft = viewModel::assignLeft,
                    onAssignRight = viewModel::assignRight
                )
            }
            composable("about") {
                AboutScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen(
    uiState: BluePairUiState,
    onToggleOutput: () -> Unit
) {
    val leftName = uiState.leftAddress?.let(uiState.deviceNameByAddress::get)
        ?: stringResource(R.string.unassigned)
    val rightName = uiState.rightAddress?.let(uiState.deviceNameByAddress::get)
        ?: stringResource(R.string.unassigned)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.main_toggle_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (uiState.isBluetoothModeEnabled) R.string.mode_bluetooth else R.string.mode_phone
            ),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onToggleOutput,
            modifier = Modifier
                .size(220.dp)
                .clip(androidx.compose.foundation.shape.CircleShape),
            contentPadding = PaddingValues(20.dp)
        ) {
            Text(
                text = stringResource(
                    if (uiState.isBluetoothModeEnabled) {
                        R.string.toggle_disable
                    } else {
                        R.string.toggle_enable
                    }
                ),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = stringResource(R.string.left_device_summary, leftName))
        Text(text = stringResource(R.string.right_device_summary, rightName))
    }
}

@Composable
private fun ConfigureScreen(
    uiState: BluePairUiState,
    onPermissionChanged: () -> Unit,
    onToggleDeviceSelection: (String) -> Unit
) {
    val context = LocalContext.current
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { onPermissionChanged() }
    )

    val selectedCount = uiState.selectedAddresses.size
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.configure_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.configure_hint))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.selected_devices_count, selectedCount))
        Spacer(modifier = Modifier.height(16.dp))

        if (!uiState.hasBluetoothPermission) {
            Text(text = stringResource(R.string.permission_required))
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                }
            ) {
                Text(text = stringResource(R.string.grant_permission))
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    // No-op: Settings screen not found.
                }
            }
        ) {
            Text(text = stringResource(R.string.open_bt_settings))
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.bondedDevices.isEmpty()) {
            Text(text = stringResource(R.string.no_bonded_devices))
        } else {
            uiState.bondedDevices.forEach { device ->
                val isSelected = uiState.selectedAddresses.contains(device.address)
                val canSelect = isSelected || uiState.selectedAddresses.size < 2
                DeviceConfigRow(
                    device = device,
                    isSelected = isSelected,
                    isEnabled = canSelect,
                    onToggle = { onToggleDeviceSelection(device.address) }
                )
            }
        }
    }
}

@Composable
private fun DeviceConfigRow(
    device: SpeakerDevice,
    isSelected: Boolean,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(enabled = isEnabled || isSelected, onClick = onToggle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                enabled = isEnabled || isSelected
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.device_address, device.address),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ChannelAssignmentScreen(
    uiState: BluePairUiState,
    onAssignLeft: (String) -> Unit,
    onAssignRight: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.assign_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.assign_hint))
        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.selectedDevices.size < 2) {
            Text(text = stringResource(R.string.configure_hint))
            return
        }

        uiState.selectedDevices.forEach { device ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = device.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .selectable(
                                    selected = uiState.leftAddress == device.address,
                                    onClick = { onAssignLeft(device.address) }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.leftAddress == device.address,
                                onClick = { onAssignLeft(device.address) }
                            )
                            Text(text = stringResource(R.string.left_channel))
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .selectable(
                                    selected = uiState.rightAddress == device.address,
                                    onClick = { onAssignRight(device.address) }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.rightAddress == device.address,
                                onClick = { onAssignRight(device.address) }
                            )
                            Text(text = stringResource(R.string.right_channel))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.about_body),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BLUE_PAIR_REPO_URL))
                context.startActivity(intent)
            }
        ) {
            Text(text = stringResource(R.string.github_link_label))
        }
    }
}
