package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.CallListenerService
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.RingingAmber

data class CallReadinessStatus(
    val hasMic: Boolean,
    val hasCamera: Boolean,
    val hasNotifications: Boolean,
    val hasBluetooth: Boolean,
    val hasFullScreenIntent: Boolean,
    val isBatteryUnrestricted: Boolean
) {
    val allRuntimeGranted: Boolean
        get() = hasMic && hasCamera && hasNotifications && hasBluetooth

    val allReady: Boolean
        get() = allRuntimeGranted && hasFullScreenIntent && isBatteryUnrestricted
}

fun runtimeCallPermissions(): Array<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}.toTypedArray()

fun checkCallReadiness(context: Context): CallReadinessStatus {
    val hasMic = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val hasCamera = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
    val hasNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val hasBluetooth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
    val hasFullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.canUseFullScreenIntent()
    } else {
        true
    }
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isBatteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName)

    return CallReadinessStatus(
        hasMic = hasMic,
        hasCamera = hasCamera,
        hasNotifications = hasNotifications,
        hasBluetooth = hasBluetooth,
        hasFullScreenIntent = hasFullScreenIntent,
        isBatteryUnrestricted = isBatteryUnrestricted
    )
}

fun openFullScreenIntentSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    } else {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
    }
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

@SuppressLint("BatteryLife")
fun openBatteryOptimizationSettings(context: Context) {
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(requestIntent)
    } catch (_: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}

@Composable
fun PermissionsReadinessCard(
    hideWhenAllReady: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var readiness by remember { mutableStateOf(checkCallReadiness(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readiness = checkCallReadiness(context)
                CallListenerService.startIfSignedIn(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        readiness = checkCallReadiness(context)
        CallListenerService.startIfSignedIn(context)
    }

    if (hideWhenAllReady && readiness.allReady) return

    GlassCard(
        cornerRadius = 22.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (readiness.allReady) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                    contentDescription = null,
                    tint = if (readiness.allReady) OnlineEmerald else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (readiness.allReady) {
                            "Background & Lock-Screen Calls Ready"
                        } else {
                            "Enable Full Background & Call Alerts"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (readiness.allReady) {
                            "Incoming calls ring over other apps & lock screen; active calls float in PiP."
                        } else {
                            "Allow permissions so calls stay live when minimized and ring even if the screen is off."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!readiness.allRuntimeGranted) {
                ReadinessActionRow(
                    icon = Icons.Rounded.WarningAmber,
                    title = "Mic, Camera, Bluetooth & Call Alerts",
                    granted = false,
                    buttonLabel = "Allow All",
                    tag = "grant_runtime_permissions_btn",
                    onClick = { runtimeLauncher.launch(runtimeCallPermissions()) }
                )
            } else {
                ReadinessStatusRow(
                    title = "Mic, Camera, Bluetooth & Notifications",
                    granted = true
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (!readiness.hasFullScreenIntent) {
                    ReadinessActionRow(
                        icon = Icons.Rounded.NotificationsActive,
                        title = "Full-Screen Lock-Screen Call Alert",
                        granted = false,
                        buttonLabel = "Enable",
                        tag = "grant_fullscreen_intent_btn",
                        onClick = { openFullScreenIntentSettings(context) }
                    )
                } else {
                    ReadinessStatusRow(
                        title = "Full-Screen Lock-Screen Call Alert",
                        granted = true
                    )
                }
            }

            if (!readiness.isBatteryUnrestricted) {
                ReadinessActionRow(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Unrestricted Background Battery",
                    granted = false,
                    buttonLabel = "Allow",
                    tag = "grant_battery_unrestricted_btn",
                    onClick = { openBatteryOptimizationSettings(context) }
                )
            } else {
                ReadinessStatusRow(
                    title = "Unrestricted Background Battery",
                    granted = true
                )
            }

            if (!readiness.allReady) {
                Button(
                    onClick = {
                        when {
                            !readiness.allRuntimeGranted -> runtimeLauncher.launch(runtimeCallPermissions())
                            !readiness.hasFullScreenIntent -> openFullScreenIntentSettings(context)
                            !readiness.isBatteryUnrestricted -> openBatteryOptimizationSettings(context)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("setup_next_permission_btn")
                ) {
                    Text(
                        text = when {
                            !readiness.allRuntimeGranted -> "Grant Calling & Alert Permissions"
                            !readiness.hasFullScreenIntent -> "Allow Full-Screen Call Alerts"
                            else -> "Disable Battery Restrictions"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadinessActionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    buttonLabel: String,
    tag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) OnlineEmerald else RingingAmber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag(tag)
        ) {
            Text(buttonLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ReadinessStatusRow(
    title: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
            contentDescription = null,
            tint = if (granted) OnlineEmerald else RingingAmber,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
