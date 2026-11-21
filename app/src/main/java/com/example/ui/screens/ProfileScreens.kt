package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.media.SafMediaManager
import com.example.data.model.DirectoryUser
import com.example.data.model.UserPrivate
import com.example.data.prefs.ChatBubbleStyle
import com.example.data.prefs.DialToneMode
import com.example.data.prefs.IncomingToneMode
import com.example.data.prefs.RingtoneAudioController
import com.example.data.prefs.RingtoneConfig
import com.example.data.turn.IceConfigResult
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPillChip
import com.example.ui.components.LocalTouchFeedback
import com.example.ui.components.PermissionsReadinessCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.CobaltDeep
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.RingingAmber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class ProfileDestination(val title: String) {
    HUB("Profile"),
    PERSONAL_DETAILS("Personal Details"),
    ACCOUNT_SETTINGS("Account Settings"),
    APP_SETTINGS("App Settings"),
    PRIVACY_SETTINGS("Privacy Settings")
}

private val PROFILE_CHAT_BG_PRESETS = listOf(
    "preset_default" to Pair("Default", listOf(Color(0xFFEAF3FF), Color(0xFFD8E9FF))),
    "preset_aurora" to Pair("Aurora", listOf(Color(0xFF062127), Color(0xFF0B1325))),
    "preset_indigo" to Pair("Galaxy", listOf(Color(0xFF14183E), Color(0xFF0A0F1D))),
    "preset_sunset" to Pair("Dusk", listOf(Color(0xFF2D1627), Color(0xFF101321)))
)

@Composable
fun ProfileHubTab(
    userPrivate: UserPrivate?,
    directoryUser: DirectoryUser?,
    myConnectNumber: String,
    isDarkTheme: Boolean,
    isSaving: Boolean,
    isUploadingAvatar: Boolean,
    ringtoneConfig: RingtoneConfig,
    turnDiagnostic: IceConfigResult?,
    hasCloudImageConfigured: Boolean,
    hasCloudinaryConfigured: Boolean = false,
    onShowMyQr: () -> Unit,
    onUploadAvatar: (Uri) -> Unit,
    onSetIncomingRingtone: (IncomingToneMode, String, String) -> Unit,
    onSetDialTone: (DialToneMode, String, String) -> Unit,
    onToggleTheme: (Boolean) -> Unit,
    onSetTouchSound: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSaveCustomImgBbKey: (String) -> Unit,
    onSaveCloudinaryConfig: (cloudName: String, uploadPreset: String) -> Unit = { _, _ -> },
    onSaveSafTreeUri: (String) -> Unit = {},
    onSetChatBubbleStyle: (ChatBubbleStyle) -> Unit = {},
    onSetDefaultChatBgPreset: (String) -> Unit = {},
    onSetIncomingCallBackgroundUri: (String) -> Unit = {},
    onSetBiometricSettings: (enabled: Boolean, allowPinFallback: Boolean) -> Unit = { _, _ -> },
    onRefreshTurn: () -> Unit,
    onSaveProfile: (
        displayName: String,
        statusText: String,
        photoUrl: String,
        discoverable: Boolean,
        bio: String?,
        location: String?,
        birthday: String?,
        gender: String?
    ) -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val feedback = LocalTouchFeedback.current
    var destination by rememberSaveable { mutableStateOf(ProfileDestination.HUB) }

    BackHandler(enabled = destination != ProfileDestination.HUB) {
        destination = ProfileDestination.HUB
    }

    val previewAudioCtrl = remember(context) { RingtoneAudioController(context) }
    DisposableEffect(Unit) {
        onDispose { previewAudioCtrl.stop() }
    }

    var displayName by remember(directoryUser?.displayName, userPrivate?.displayName) {
        mutableStateOf(directoryUser?.displayName ?: userPrivate?.displayName ?: "")
    }
    var statusText by remember(directoryUser?.statusText) {
        mutableStateOf(directoryUser?.statusText ?: "Available on Connect Live")
    }
    var bio by remember(userPrivate?.bio, directoryUser?.bio) {
        mutableStateOf(userPrivate?.bio ?: directoryUser?.bio ?: "")
    }
    var location by remember(userPrivate?.location, directoryUser?.location) {
        mutableStateOf(userPrivate?.location ?: directoryUser?.location ?: "")
    }
    var birthday by remember(userPrivate?.birthday) {
        mutableStateOf(userPrivate?.birthday ?: "")
    }
    var gender by remember(userPrivate?.gender) {
        mutableStateOf(userPrivate?.gender ?: "")
    }
    val photoUrl = directoryUser?.photoUrl ?: userPrivate?.photoUrl ?: ""
    var discoverable by remember(directoryUser?.discoverable) {
        mutableStateOf(directoryUser?.discoverable ?: true)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) onUploadAvatar(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 108.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (destination != ProfileDestination.HUB) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        feedback?.triggerTap()
                        previewAudioCtrl.stop()
                        destination = ProfileDestination.HUB
                    },
                    modifier = Modifier.testTag("profile_subscreen_back")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Profile Hub")
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = destination.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        when (destination) {
            ProfileDestination.HUB -> {
                // STRICT PROFILE HUB ORDER:
                // 1. Profile Photo -> 2. Name -> 3. Phone Number (01xxxxxxx + Copy Icon) -> 4. Email
                ProfileIdentityCard(
                    displayName = displayName,
                    myConnectNumber = myConnectNumber,
                    email = userPrivate?.email.orEmpty(),
                    followersCount = directoryUser?.followersCount ?: 0L,
                    followingCount = directoryUser?.followingCount ?: 0L,
                    totalLikesCount = directoryUser?.totalLikesCount ?: 0L,
                    postsCount = directoryUser?.postsCount ?: 0L,
                    photoUrl = photoUrl,
                    isUploadingAvatar = isUploadingAvatar,
                    onPickAvatar = {
                        feedback?.triggerTap()
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onShowMyQr = onShowMyQr
                )

                // 5. Personal Details -> 6. Account Settings -> 7. App Settings -> 8. Privacy Settings
                GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileMenuRow(
                            icon = Icons.Rounded.Person,
                            iconTint = AzurePrimary,
                            title = "Personal Details",
                            subtitle = "Name, status, bio, location, birthday & photo",
                            testTag = "profile_menu_personal_details",
                            onClick = { destination = ProfileDestination.PERSONAL_DETAILS }
                        )
                        MenuDivider()
                        ProfileMenuRow(
                            icon = Icons.Rounded.ManageAccounts,
                            iconTint = OnlineEmerald,
                            title = "Account Settings",
                            subtitle = "QR code badge, 01 Connect ID & SAF storage folder",
                            testTag = "profile_menu_account",
                            onClick = { destination = ProfileDestination.ACCOUNT_SETTINGS }
                        )
                        MenuDivider()
                        ProfileMenuRow(
                            icon = Icons.Rounded.Tune,
                            iconTint = CobaltDeep,
                            title = "App Settings",
                            subtitle = "Call wallpaper, chat bubbles, ringtones, Dark Mode & ImgBB/Cloudinary",
                            testTag = "profile_menu_settings",
                            onClick = { destination = ProfileDestination.APP_SETTINGS }
                        )
                        MenuDivider()
                        ProfileMenuRow(
                            icon = Icons.Rounded.PrivacyTip,
                            iconTint = RingingAmber,
                            title = "Privacy Settings",
                            subtitle = if (ringtoneConfig.biometricLockEnabled) {
                                "Fingerprint lock active • ${if (discoverable) "Discoverable" else "Private"}"
                            } else {
                                if (discoverable) "Discoverable in Friends" else "Private (QR/01 Invite only)"
                            },
                            testTag = "profile_menu_privacy",
                            onClick = { destination = ProfileDestination.PRIVACY_SETTINGS }
                        )
                    }
                }

                // 9. Sign Out
                OutlinedButton(
                    onClick = {
                        feedback?.triggerTap()
                        onSignOut()
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CallCrimson),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.sign_out), fontWeight = FontWeight.SemiBold)
                }
            }

            ProfileDestination.PERSONAL_DETAILS -> {
                GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                name = displayName.ifBlank { "Connect User" },
                                photoUrl = photoUrl,
                                size = 64.dp,
                                online = true
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            OutlinedButton(
                                onClick = {
                                    feedback?.triggerTap()
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                enabled = !isUploadingAvatar,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("edit_pick_profile_photo_button")
                            ) {
                                if (isUploadingAvatar) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Updating...")
                                } else {
                                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Change Photo")
                                }
                            }
                        }

                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Display Name") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_name_input")
                        )

                        OutlinedTextField(
                            value = statusText,
                            onValueChange = { statusText = it },
                            label = { Text("Status Message") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_status_input")
                        )

                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text("Bio") },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_bio_input")
                        )

                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Location / City") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = birthday,
                                onValueChange = { birthday = it },
                                label = { Text("Birthday") },
                                placeholder = { Text("YYYY-MM-DD") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = gender,
                                onValueChange = { gender = it },
                                label = { Text("Gender") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                feedback?.triggerTap()
                                onSaveProfile(
                                    displayName,
                                    statusText,
                                    photoUrl,
                                    discoverable,
                                    bio,
                                    location,
                                    birthday,
                                    gender
                                )
                            },
                            enabled = !isSaving && displayName.isNotBlank(),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_profile_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Save Personal Details", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            ProfileDestination.ACCOUNT_SETTINGS -> {
                AccountSettingsSubScreen(
                    email = userPrivate?.email.orEmpty(),
                    myConnectNumber = myConnectNumber,
                    safTreeUri = ringtoneConfig.safTreeUri,
                    onShowMyQr = onShowMyQr,
                    onSaveSafTreeUri = onSaveSafTreeUri
                )
            }

            ProfileDestination.APP_SETTINGS -> {
                AppSettingsSubScreen(
                    isDarkTheme = isDarkTheme,
                    ringtoneConfig = ringtoneConfig,
                    previewAudioCtrl = previewAudioCtrl,
                    turnDiagnostic = turnDiagnostic,
                    hasCloudImageConfigured = hasCloudImageConfigured,
                    hasCloudinaryConfigured = hasCloudinaryConfigured,
                    onToggleTheme = onToggleTheme,
                    onSetTouchSound = onSetTouchSound,
                    onSetHaptics = onSetHaptics,
                    onSetIncomingCallBackgroundUri = onSetIncomingCallBackgroundUri,
                    onSetChatBubbleStyle = onSetChatBubbleStyle,
                    onSetDefaultChatBgPreset = onSetDefaultChatBgPreset,
                    onSetIncomingRingtone = onSetIncomingRingtone,
                    onSetDialTone = onSetDialTone,
                    onSaveCustomImgBbKey = onSaveCustomImgBbKey,
                    onSaveCloudinaryConfig = onSaveCloudinaryConfig,
                    onRefreshTurn = onRefreshTurn
                )
            }

            ProfileDestination.PRIVACY_SETTINGS -> {
                PrivacySettingsSubScreen(
                    biometricEnabled = ringtoneConfig.biometricLockEnabled,
                    allowPinFallback = ringtoneConfig.biometricAllowPinFallback,
                    discoverable = discoverable,
                    onSetBiometricSettings = onSetBiometricSettings,
                    onToggleDiscoverable = { checked ->
                        discoverable = checked
                        onSaveProfile(displayName, statusText, photoUrl, checked, bio, location, birthday, gender)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    displayName: String,
    myConnectNumber: String,
    email: String,
    followersCount: Long,
    followingCount: Long,
    totalLikesCount: Long,
    postsCount: Long,
    photoUrl: String,
    isUploadingAvatar: Boolean,
    onPickAvatar: () -> Unit,
    onShowMyQr: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val feedback = LocalTouchFeedback.current

    GlassCard(cornerRadius = 26.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Profile Photo
            Box(contentAlignment = Alignment.BottomEnd) {
                UserAvatar(
                    name = displayName.ifBlank { "Connect User" },
                    photoUrl = photoUrl,
                    size = 94.dp,
                    online = true
                )
                androidx.compose.material3.Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable(enabled = !isUploadingAvatar, onClick = onPickAvatar)
                        .testTag("pick_profile_photo_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isUploadingAvatar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PhotoCamera,
                                contentDescription = "Change profile photo",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. Name
            Text(
                text = displayName.ifBlank { "Connect User" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Phone Number (01xxxxxxx) + Copy Icon (+ QR button)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlassPillChip(
                    borderColor = AzurePrimary.copy(alpha = 0.35f),
                    onClick = {
                        if (myConnectNumber.isNotBlank()) {
                            clipboard.setText(AnnotatedString(myConnectNumber))
                            Toast.makeText(context, "Copied $myConnectNumber", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("profile_phone_copy_chip")
                ) {
                    Text(
                        text = myConnectNumber.ifBlank { "Generating..." },
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = AzurePrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy Connect Number",
                        tint = AzurePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        feedback?.triggerTap()
                        onShowMyQr()
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("profile_show_qr_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCode2,
                        contentDescription = "Show My QR Code",
                        tint = AzurePrimary
                    )
                }
            }

            // 4. Email Address
            if (email.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("profile_email_text")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(12.dp))

            // Social Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStatPill(value = followersCount, label = "Followers")
                ProfileStatPill(value = followingCount, label = "Following")
                ProfileStatPill(value = totalLikesCount, label = "Likes")
                ProfileStatPill(value = postsCount, label = "Posts")
            }
        }
    }
}

@Composable
private fun ProfileStatPill(value: Long, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AccountSettingsSubScreen(
    email: String,
    myConnectNumber: String,
    safTreeUri: String,
    onShowMyQr: () -> Unit,
    onSaveSafTreeUri: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val feedback = LocalTouchFeedback.current
    val hasFolder = SafMediaManager.hasPersistedFolderAccess(context, safTreeUri)
    var fileCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    LaunchedEffect(safTreeUri, hasFolder) {
        if (hasFolder) {
            fileCounts = SafMediaManager.countTransferredFiles(context, safTreeUri)
        }
    }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            SafMediaManager.persistTreePermission(context, uri)
                .onSuccess { persisted -> onSaveSafTreeUri(persisted) }
                .onFailure {
                    Toast.makeText(context, "Could not grant folder permission", Toast.LENGTH_SHORT).show()
                }
        }
    }

    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Connect Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Email: ${email.ifBlank { "Google Account" }}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Connect Number: ${myConnectNumber.ifBlank { "..." }}",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = AzurePrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        feedback?.triggerTap()
                        if (myConnectNumber.isNotBlank()) {
                            clipboard.setText(AnnotatedString(myConnectNumber))
                            Toast.makeText(context, "Copied $myConnectNumber", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Number")
                }
                Button(
                    onClick = {
                        feedback?.triggerTap()
                        onShowMyQr()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Rounded.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("QR Badge")
                }
            }
        }
    }

    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = if (hasFolder) OnlineEmerald else RingingAmber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Media & Chat Backup Folder",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = if (hasFolder) {
                    "Connected folder: ${SafMediaManager.getFolderDisplayName(context, safTreeUri)}"
                } else {
                    "Select a folder to mirror images, videos, P2P voice notes & JSON chat backups."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (hasFolder) OnlineEmerald else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hasFolder && fileCounts.isNotEmpty()) {
                Text(
                    text = "Images: ${fileCounts[SafMediaManager.SUB_IMAGES] ?: 0} • " +
                        "Videos: ${fileCounts[SafMediaManager.SUB_VIDEOS] ?: 0} • " +
                        "VoiceNotes: ${fileCounts[SafMediaManager.SUB_VOICE_NOTES] ?: 0} • " +
                        "Backups: ${fileCounts[SafMediaManager.SUB_BACKUPS] ?: 0}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    feedback?.triggerTap()
                    folderLauncher.launch(null)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("change_saf_folder_button")
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (hasFolder) "Change Storage Folder" else "Choose Storage Folder")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppSettingsSubScreen(
    isDarkTheme: Boolean,
    ringtoneConfig: RingtoneConfig,
    previewAudioCtrl: RingtoneAudioController,
    turnDiagnostic: IceConfigResult?,
    hasCloudImageConfigured: Boolean,
    hasCloudinaryConfigured: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onSetTouchSound: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetIncomingCallBackgroundUri: (String) -> Unit,
    onSetChatBubbleStyle: (ChatBubbleStyle) -> Unit,
    onSetDefaultChatBgPreset: (String) -> Unit,
    onSetIncomingRingtone: (IncomingToneMode, String, String) -> Unit,
    onSetDialTone: (DialToneMode, String, String) -> Unit,
    onSaveCustomImgBbKey: (String) -> Unit,
    onSaveCloudinaryConfig: (String, String) -> Unit,
    onRefreshTurn: () -> Unit
) {
    val context = LocalContext.current
    val feedback = LocalTouchFeedback.current
    val scope = rememberCoroutineScope()

    var imgBbInput by remember(ringtoneConfig.customImgBbKey) {
        mutableStateOf(ringtoneConfig.customImgBbKey)
    }
    var revealImgBbKey by remember { mutableStateOf(false) }

    var cloudNameInput by remember(ringtoneConfig.cloudinaryCloudName) {
        mutableStateOf(ringtoneConfig.cloudinaryCloudName)
    }
    var uploadPresetInput by remember(ringtoneConfig.cloudinaryUploadPreset) {
        mutableStateOf(ringtoneConfig.cloudinaryUploadPreset)
    }

    val callBgPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val persistedUrl = withContext(Dispatchers.IO) {
                    val dest = File(context.filesDir, "incoming_call_bg.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    Uri.fromFile(dest).toString() + "?t=" + System.currentTimeMillis()
                }
                if (ringtoneConfig.safTreeUri.isNotBlank()) {
                    SafMediaManager.copyUriToSafFolder(
                        context = context,
                        safTreeUri = ringtoneConfig.safTreeUri,
                        subfolder = SafMediaManager.SUB_IMAGES,
                        sourceUri = uri,
                        fileName = "call_screen_bg_${System.currentTimeMillis()}.jpg",
                        mimeType = "image/jpeg"
                    )
                }
                onSetIncomingCallBackgroundUri(persistedUrl)
            }
        }
    }

    val systemRingtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val pickedUri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (pickedUri != null) {
                val title = runCatching {
                    RingtoneManager.getRingtone(context, pickedUri)?.getTitle(context)
                }.getOrNull().orEmpty().ifBlank { "System Ringtone" }
                onSetIncomingRingtone(IncomingToneMode.CUSTOM_URI, pickedUri.toString(), title)
            }
        }
    }

    val customIncomingAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val title = queryUriDisplayName(context, uri) ?: "Custom Ringtone"
            onSetIncomingRingtone(IncomingToneMode.CUSTOM_URI, uri.toString(), title)
        }
    }

    val customDialAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val title = queryUriDisplayName(context, uri) ?: "Custom Dial Tone"
            onSetDialTone(DialToneMode.CUSTOM_URI, uri.toString(), title)
        }
    }

    // 1. Appearance & Touch Feedback
    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Appearance & Touch Feedback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ToggleSettingRow(
                title = "Dark Mode (Midnight Sapphire)",
                subtitle = "White & Blue Frosted Azure is active when off",
                checked = isDarkTheme,
                onCheckedChange = onToggleTheme
            )
            MenuDivider()
            ToggleSettingRow(
                title = "Touch Click Sounds",
                subtitle = "Subtle audio feedback on nav tabs, swipes & buttons",
                checked = ringtoneConfig.touchSoundEnabled,
                onCheckedChange = onSetTouchSound
            )
            MenuDivider()
            ToggleSettingRow(
                title = "Haptic Vibration Feedback",
                subtitle = "Crisp tactile tick on gestures and call slides",
                checked = ringtoneConfig.hapticsEnabled,
                onCheckedChange = onSetHaptics
            )
        }
    }

    // 2. Upcoming Call Screen Image Customization
    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Wallpaper, contentDescription = null, tint = AzurePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Upcoming Call Screen Background",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Set a custom photo wallpaper behind the iPhone-style frosted incoming call screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ringtoneConfig.incomingCallBackgroundUri.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, AzurePrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                ) {
                    AsyncImage(
                        model = ringtoneConfig.incomingCallBackgroundUri,
                        contentDescription = "Incoming call screen wallpaper preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ringtoneConfig.incomingCallBackgroundUri.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            feedback?.triggerTap()
                            onSetIncomingCallBackgroundUri("")
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("Reset Default")
                    }
                }
                Button(
                    onClick = {
                        feedback?.triggerTap()
                        callBgPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("choose_call_bg_button")
                ) {
                    Text("Choose Image")
                }
            }
        }
    }

    // 3. Chat Theme & Bubble Style Customization
    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = CobaltDeep)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chat Theme & Bubble Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChatBubbleStyle.entries.forEach { style ->
                    val selected = ringtoneConfig.chatBubbleStyle == style
                    FilterChip(
                        selected = selected,
                        onClick = {
                            feedback?.triggerTap()
                            onSetChatBubbleStyle(style)
                        },
                        label = { Text(style.label) }
                    )
                }
            }

            Text(
                text = "Default Chat Background",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PROFILE_CHAT_BG_PRESETS.forEach { (key, info) ->
                    val (label, colors) = info
                    val selected = ringtoneConfig.defaultChatBgPreset == key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                feedback?.triggerTap()
                                onSetDefaultChatBgPreset(key)
                            }
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.verticalGradient(colors))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) AzurePrimary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    // 4. Ringtones
    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = AzurePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Incoming Call Ringtone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Active: ${ringtoneConfig.incomingLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AzurePrimary
                    )
                }
            }

            val incomingPresets = listOf(
                IncomingToneMode.IOS_REFLECTION,
                IncomingToneMode.IOS_MARIMBA,
                IncomingToneMode.IOS_OPENING,
                IncomingToneMode.VIRAL_AURORA,
                IncomingToneMode.VIRAL_SILK,
                IncomingToneMode.SYSTEM_DEFAULT
            )

            incomingPresets.forEach { mode ->
                val selected = ringtoneConfig.incomingMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) AzurePrimary.copy(alpha = 0.10f) else Color.Transparent
                        )
                        .clickable {
                            feedback?.triggerTap()
                            onSetIncomingRingtone(mode, "", mode.label)
                            previewAudioCtrl.playIncomingRingtone(
                                ringtoneConfig.copy(incomingMode = mode, incomingLabel = mode.label),
                                previewOnly = true
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (selected) AzurePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            text = mode.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            feedback?.triggerTap()
                            previewAudioCtrl.playIncomingRingtone(
                                ringtoneConfig.copy(incomingMode = mode, incomingLabel = mode.label),
                                previewOnly = true
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview ${mode.label}", tint = AzurePrimary)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        feedback?.triggerTap()
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Incoming Ringtone")
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        }
                        systemRingtonePickerLauncher.launch(intent)
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Phone Tone", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        feedback?.triggerTap()
                        customIncomingAudioLauncher.launch(arrayOf("audio/*"))
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Custom MP3", maxLines = 1)
                }
            }
        }
    }

    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = CobaltDeep)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Outgoing Dial Ringback", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Active: ${ringtoneConfig.dialLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CobaltDeep
                    )
                }
            }

            listOf(
                DialToneMode.IOS_DIALING,
                DialToneMode.PRESET_CLASSIC,
                DialToneMode.PRESET_SOFT,
                DialToneMode.SILENT
            ).forEach { mode ->
                val selected = ringtoneConfig.dialMode == mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) AzurePrimary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable {
                            feedback?.triggerTap()
                            onSetDialTone(mode, "", mode.label)
                            previewAudioCtrl.playOutgoingDialTone(
                                ringtoneConfig.copy(dialMode = mode, dialLabel = mode.label),
                                previewOnly = true
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (selected) AzurePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (mode != DialToneMode.SILENT) {
                        IconButton(
                            onClick = {
                                feedback?.triggerTap()
                                previewAudioCtrl.playOutgoingDialTone(
                                    ringtoneConfig.copy(dialMode = mode, dialLabel = mode.label),
                                    previewOnly = true
                                )
                            }
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview ${mode.label}", tint = AzurePrimary)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    feedback?.triggerTap()
                    customDialAudioLauncher.launch(arrayOf("audio/*"))
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Select Custom Dial File...")
            }
        }
    }

    // 5. ImgBB & Cloudinary API + TURN Network
    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = if (hasCloudImageConfigured) OnlineEmerald else RingingAmber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("ImgBB Photo Hosting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (hasCloudImageConfigured) {
                    "ImgBB ready for HD avatars, Feed images, chat photos & wallpapers."
                } else {
                    "Add your ImgBB API key here (or in Secrets) for HD chat & Feed photos."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = imgBbInput,
                onValueChange = { imgBbInput = it },
                label = { Text("ImgBB API Key") },
                singleLine = true,
                visualTransformation = if (revealImgBbKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        text = if (revealImgBbKey) "Hide" else "Show",
                        style = MaterialTheme.typography.labelMedium,
                        color = AzurePrimary,
                        modifier = Modifier
                            .clickable { revealImgBbKey = !revealImgBbKey }
                            .padding(8.dp)
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_imgbb_key_input")
            )
            Button(
                onClick = {
                    feedback?.triggerTap()
                    onSaveCustomImgBbKey(imgBbInput)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("save_imgbb_key_button")
            ) {
                Text("Save ImgBB Key", fontWeight = FontWeight.Bold)
            }
        }
    }

    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = if (hasCloudinaryConfigured) OnlineEmerald else RingingAmber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cloudinary Video Hosting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (hasCloudinaryConfigured) {
                    "Cloudinary configured for Chat & Feed video transfers."
                } else {
                    "Enter Cloudinary Cloud Name & Unsigned Upload Preset (or set in AI Studio Secrets) for video uploads."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = cloudNameInput,
                onValueChange = { cloudNameInput = it },
                label = { Text("Cloudinary Cloud Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloudinary_cloud_name_input")
            )
            OutlinedTextField(
                value = uploadPresetInput,
                onValueChange = { uploadPresetInput = it },
                label = { Text("Unsigned Upload Preset") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cloudinary_preset_input")
            )
            Button(
                onClick = {
                    feedback?.triggerTap()
                    onSaveCloudinaryConfig(cloudNameInput, uploadPresetInput)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("save_cloudinary_button")
            ) {
                Text("Save Cloudinary Settings", fontWeight = FontWeight.Bold)
            }
        }
    }

    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("WebRTC & TURN Relay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = {
                    feedback?.triggerTap()
                    onRefreshTurn()
                }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh TURN status")
                }
            }
            Text(
                text = "ICE Mode: ${turnDiagnostic?.statusLabel ?: "Checking..."}",
                style = MaterialTheme.typography.labelLarge,
                color = if (turnDiagnostic?.turnRelayEnabled == true) OnlineEmerald else MaterialTheme.colorScheme.secondary
            )
            turnDiagnostic?.warningMessage?.let { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Active ICE Servers: ${turnDiagnostic?.iceServers?.size ?: 0}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    PermissionsReadinessCard(hideWhenAllReady = false)
}

@Composable
private fun PrivacySettingsSubScreen(
    biometricEnabled: Boolean,
    allowPinFallback: Boolean,
    discoverable: Boolean,
    onSetBiometricSettings: (enabled: Boolean, allowPinFallback: Boolean) -> Unit,
    onToggleDiscoverable: (Boolean) -> Unit
) {
    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = AzurePrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Security Lock", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            ToggleSettingRow(
                title = "Fingerprint Security Lock",
                subtitle = "Require fingerprint verification on launch and foreground return",
                checked = biometricEnabled,
                onCheckedChange = { checked ->
                    onSetBiometricSettings(checked, allowPinFallback)
                }
            )
            MenuDivider()
            ToggleSettingRow(
                title = "Allow Device PIN / Pattern Fallback",
                subtitle = "Allow unlocking with device screen lock if fingerprint fails",
                checked = allowPinFallback,
                onCheckedChange = { checked ->
                    onSetBiometricSettings(biometricEnabled, checked)
                }
            )
            MenuDivider()
            ToggleSettingRow(
                title = "Discoverable in Friends",
                subtitle = "Allow signed-in users to find you in the Friends tab",
                checked = discoverable,
                onCheckedChange = onToggleDiscoverable
            )
        }
    }

    GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Connect Live — Privacy Policy & Data Controls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "1. Account & Identity: Connect Live uses Google Sign-In to authenticate your account and assign a unique 9-digit 01 Connect Number.\n" +
                    "2. Friend-Only Messaging & Feed DM Limit: Directory messaging is restricted to confirmed friends. Non-friends in Feed DMs can send up to 3 messages until you reply.\n" +
                    "3. Voice Notes & Storage Folder: Voice notes transfer peer-to-peer over WebRTC DataChannels and save alongside photos, videos, and JSON chat backups inside your selected SAF folder.\n" +
                    "4. Hardware Permissions: Camera & Microphone remain optional until you record voice notes, scan QR codes, or place audio/video calls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val feedback = LocalTouchFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                feedback?.triggerTap()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconTint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val feedback = LocalTouchFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                feedback?.triggerTap()
                onCheckedChange(it)
            }
        )
    }
}

@Composable
private fun MenuDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

private fun queryUriDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
    }.getOrNull()
}
