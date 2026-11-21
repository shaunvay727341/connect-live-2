package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.media.SafMediaManager
import com.example.service.CallListenerService
import com.example.ui.components.GlassBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPillChip
import com.example.ui.components.LocalTouchFeedback
import com.example.ui.components.checkCallReadiness
import com.example.ui.components.openBatteryOptimizationSettings
import com.example.ui.components.openFullScreenIntentSettings
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.CobaltDeep
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.RingingAmber
import com.example.util.BiometricAuthHelper
import com.example.util.BiometricAvailability

@Composable
fun BiometricLockScreen(
    userName: String,
    allowPinFallback: Boolean,
    onUnlocked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val feedback = LocalTouchFeedback.current
    var errorText by remember { mutableStateOf<String?>(null) }

    val availability = remember(activity, allowPinFallback) {
        if (activity != null) {
            BiometricAuthHelper.checkAvailability(activity, allowPinFallback)
        } else {
            BiometricAvailability.NotAvailable("Biometric host activity unavailable.")
        }
    }

    val launchPrompt: () -> Unit = {
        if (activity != null && availability is BiometricAvailability.Ready) {
            errorText = null
            BiometricAuthHelper.authenticate(
                activity = activity,
                title = "Unlock Connect Live",
                subtitle = "Verify your fingerprint to protect your chats, calls & folder",
                allowPinFallback = allowPinFallback,
                onSuccess = onUnlocked,
                onError = { msg -> errorText = msg }
            )
        }
    }

    LaunchedEffect(activity, availability) {
        if (availability is BiometricAvailability.Ready) {
            launchPrompt()
        }
    }

    GlassBackdrop(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                cornerRadius = 30.dp,
                borderColor = AzurePrimary.copy(alpha = 0.35f),
                modifier = Modifier.widthIn(max = 420.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(AzurePrimary, CobaltDeep))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = "Fingerprint security lock",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                    }

                    GlassPillChip(borderColor = AzurePrimary.copy(alpha = 0.3f)) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = AzurePrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "App Lock Active",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = AzurePrimary
                        )
                    }

                    Text(
                        text = "Connect Live Locked",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (userName.isNotBlank()) {
                            "Welcome back, $userName. Scan your fingerprint to unlock chats, media and calls."
                        } else {
                            "Scan your fingerprint to enter Connect Live."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (errorText != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = CallCrimson.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorText.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = CallCrimson,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    when (availability) {
                        is BiometricAvailability.Ready -> {
                            Button(
                                onClick = {
                                    feedback?.triggerTap()
                                    launchPrompt()
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("unlock_biometric_button")
                            ) {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Unlock with Fingerprint", fontWeight = FontWeight.Bold)
                            }
                        }
                        is BiometricAvailability.NotAvailable -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = RingingAmber.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = availability.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            Button(
                                onClick = {
                                    feedback?.triggerTap()
                                    onUnlocked()
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("unlock_fallback_continue_button")
                            ) {
                                Text("Continue Without Fingerprint", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MandatoryPermissionsGate(
    safTreeUri: String,
    onSafTreeSelected: (String) -> Unit,
    onGatePassed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = LocalTouchFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var readiness by remember { mutableStateOf(checkCallReadiness(context)) }
    var hasFolderAccess by remember(safTreeUri) {
        mutableStateOf(SafMediaManager.hasPersistedFolderAccess(context, safTreeUri))
    }
    var folderStatusError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, safTreeUri) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                readiness = checkCallReadiness(context)
                hasFolderAccess = SafMediaManager.hasPersistedFolderAccess(context, safTreeUri)
                CallListenerService.startIfSignedIn(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        readiness = checkCallReadiness(context)
        CallListenerService.startIfSignedIn(context)
    }

    val optionalCameraMicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        readiness = checkCallReadiness(context)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            SafMediaManager.persistTreePermission(context, treeUri)
                .onSuccess { persisted ->
                    folderStatusError = null
                    hasFolderAccess = true
                    onSafTreeSelected(persisted)
                }
                .onFailure { err ->
                    folderStatusError = err.localizedMessage ?: "Unable to persist folder permission."
                }
        }
    }

    val mandatoryComplete = readiness.hasNotifications && hasFolderAccess

    GlassBackdrop(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GlassCard(cornerRadius = 26.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = AzurePrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "App Setup & Permissions",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Choose your Connect Live storage folder for photos, videos, voice notes & chat backups, and enable alerts so messages & calls arrive reliably.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // MANDATORY SECTION
                GlassCard(
                    cornerRadius = 24.dp,
                    borderColor = AzurePrimary.copy(alpha = 0.28f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "REQUIRED TO ENTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = AzurePrimary,
                            fontWeight = FontWeight.Bold
                        )

                        PermissionRow(
                            icon = Icons.Rounded.FolderOpen,
                            title = "Connect Live Folder (Media & Chat Backup)",
                            subtitle = if (hasFolderAccess) {
                                "Selected: ${SafMediaManager.getFolderDisplayName(context, safTreeUri)}"
                            } else {
                                "Select a folder for Images, Videos, Voice Notes & JSON Backups"
                            },
                            granted = hasFolderAccess,
                            actionLabel = if (hasFolderAccess) "Change" else "Choose Folder",
                            testTag = "gate_pick_folder_button",
                            onAction = {
                                feedback?.triggerTap()
                                folderPickerLauncher.launch(null)
                            }
                        )

                        if (folderStatusError != null) {
                            Text(
                                text = folderStatusError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = CallCrimson
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            PermissionRow(
                                icon = Icons.Rounded.NotificationsActive,
                                title = "Notifications & Message Alerts",
                                subtitle = "Required for incoming calls, messages & Feed activity",
                                granted = readiness.hasNotifications,
                                actionLabel = "Allow",
                                testTag = "gate_notifications_button",
                                onAction = {
                                    feedback?.triggerTap()
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            )
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            PermissionRow(
                                icon = Icons.Rounded.NotificationsActive,
                                title = "Full-Screen Lock-Screen Call Alert",
                                subtitle = "Shows iPhone-style call screen even when device is locked",
                                granted = readiness.hasFullScreenIntent,
                                actionLabel = "Enable",
                                testTag = "gate_fullscreen_button",
                                onAction = {
                                    feedback?.triggerTap()
                                    openFullScreenIntentSettings(context)
                                }
                            )
                        }

                        PermissionRow(
                            icon = Icons.Rounded.BatteryChargingFull,
                            title = "Unrestricted Background Battery",
                            subtitle = "Keeps offline recovery & WebRTC voice P2P active",
                            granted = readiness.isBatteryUnrestricted,
                            actionLabel = "Allow",
                            testTag = "gate_battery_button",
                            onAction = {
                                feedback?.triggerTap()
                                openBatteryOptimizationSettings(context)
                            }
                        )
                    }
                }

                // OPTIONAL SECTION (Camera & Microphone MUST stay optional)
                GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OPTIONAL (CAN SKIP FOR NOW)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            GlassPillChip {
                                Text(
                                    text = "Enable Later",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        PermissionRow(
                            icon = Icons.Rounded.Mic,
                            title = "Microphone",
                            subtitle = "Needed only when recording voice notes or audio/video calls",
                            granted = readiness.hasMic,
                            actionLabel = "Allow",
                            testTag = "gate_optional_mic_button",
                            onAction = {
                                feedback?.triggerTap()
                                optionalCameraMicLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        )

                        PermissionRow(
                            icon = Icons.Rounded.CameraAlt,
                            title = "Camera",
                            subtitle = "Needed only for video calls & live QR code scanning",
                            granted = readiness.hasCamera,
                            actionLabel = "Allow",
                            testTag = "gate_optional_cam_button",
                            onAction = {
                                feedback?.triggerTap()
                                optionalCameraMicLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        )

                        if (!readiness.hasMic || !readiness.hasCamera) {
                            OutlinedButton(
                                onClick = {
                                    feedback?.triggerTap()
                                    optionalCameraMicLauncher.launch(
                                        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) {
                                Text("Grant Both Mic & Camera (Optional)")
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        feedback?.triggerTap()
                        onGatePassed()
                    },
                    enabled = mandatoryComplete,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("gate_enter_app_button")
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (mandatoryComplete) {
                            "Enter Connect Live"
                        } else {
                            "Select Folder & Notifications to Continue"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    testTag: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (granted) OnlineEmerald.copy(alpha = 0.14f) else AzurePrimary.copy(alpha = 0.12f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                tint = if (granted) OnlineEmerald else AzurePrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        FilledTonalButton(
            onClick = onAction,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.testTag(testTag)
        ) {
            Text(if (granted) "Ready" else actionLabel, style = MaterialTheme.typography.labelMedium)
        }
    }
}
