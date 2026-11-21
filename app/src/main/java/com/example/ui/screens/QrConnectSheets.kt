package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ConnectNumberEntry
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPillChip
import com.example.ui.components.LocalTouchFeedback
import com.example.ui.components.UserAvatar
import com.example.ui.theme.AuroraTeal
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.FrostOutline
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.RingingAmber
import com.example.util.QrCodeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectQrBottomSheet(
    myName: String,
    myConnectNumber: String,
    lookupLoading: Boolean,
    lookupResult: ConnectNumberEntry?,
    lookupError: String?,
    friendUids: Set<String> = emptySet(),
    pendingOutgoingUids: Set<String> = emptySet(),
    onLookupNumber: (String) -> Unit,
    onSendFriendRequest: (ConnectNumberEntry) -> Unit,
    onClearLookup: () -> Unit,
    onDismiss: () -> Unit,
    initialTab: Int = 0
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = {
            onClearLookup()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connect ID & QR",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    onClearLookup()
                    onDismiss()
                }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("My QR", maxLines = 1) },
                    icon = { Icon(Icons.Rounded.QrCode2, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("01 Number", maxLines = 1) },
                    icon = { Icon(Icons.Rounded.Dialpad, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Scan QR", maxLines = 1) },
                    icon = { Icon(Icons.Rounded.QrCodeScanner, contentDescription = null) }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (selectedTab) {
                    0 -> MyQrSection(
                        myName = myName,
                        myConnectNumber = myConnectNumber
                    )
                    1 -> NumberLookupSection(
                        lookupLoading = lookupLoading,
                        lookupResult = lookupResult,
                        lookupError = lookupError,
                        friendUids = friendUids,
                        pendingOutgoingUids = pendingOutgoingUids,
                        onLookupNumber = onLookupNumber,
                        onSendFriendRequest = { entry ->
                            onSendFriendRequest(entry)
                            onDismiss()
                        }
                    )
                    else -> QrScannerSection(
                        lookupLoading = lookupLoading,
                        lookupResult = lookupResult,
                        lookupError = lookupError,
                        friendUids = friendUids,
                        pendingOutgoingUids = pendingOutgoingUids,
                        onQrNumberDetected = { num ->
                            onLookupNumber(num)
                        },
                        onSendFriendRequest = { entry ->
                            onSendFriendRequest(entry)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MyQrSection(
    myName: String,
    myConnectNumber: String
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val feedback = LocalTouchFeedback.current

    val qrBitmap = remember(myConnectNumber) {
        if (QrCodeUtils.isValidConnectNumber(myConnectNumber)) {
            QrCodeUtils.generateQrBitmap(QrCodeUtils.encodeConnectQrPayload(myConnectNumber), 680)
        } else {
            null
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = myName.ifBlank { "Connect User" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        GlassCard(
            cornerRadius = 28.dp,
            borderColor = AzurePrimary.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassPillChip(
                    borderColor = AzurePrimary.copy(alpha = 0.28f)
                ) {
                    Text(
                        text = "ID: ${myConnectNumber.ifBlank { "Generating..." }}",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (qrBitmap != null) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier.border(
                            width = 1.5.dp,
                            color = FrostOutline,
                            shape = RoundedCornerShape(24.dp)
                        )
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "My Connect QR Code",
                            modifier = Modifier
                                .size(228.dp)
                                .padding(14.dp)
                        )
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }

                Text(
                    text = "Share your 9-digit 01 number or let friends scan this Connect Live badge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    feedback?.triggerTap()
                    if (myConnectNumber.isNotBlank()) {
                        clipboard.setText(AnnotatedString(myConnectNumber))
                        Toast.makeText(context, "Copied $myConnectNumber", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = myConnectNumber.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy ID")
            }
            Button(
                onClick = {
                    feedback?.triggerTap()
                    if (myConnectNumber.isNotBlank()) {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Add me on Connect Live! My Connect Number is $myConnectNumber"
                            )
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Connect Number"))
                    }
                },
                enabled = myConnectNumber.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share")
            }
        }
    }
}

@Composable
private fun NumberLookupSection(
    lookupLoading: Boolean,
    lookupResult: ConnectNumberEntry?,
    lookupError: String?,
    friendUids: Set<String>,
    pendingOutgoingUids: Set<String>,
    onLookupNumber: (String) -> Unit,
    onSendFriendRequest: (ConnectNumberEntry) -> Unit
) {
    var numberInput by remember { mutableStateOf("01") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Enter a friend's 9-digit Connect Number (starts with 01) to find them and send a friend request.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = numberInput,
            onValueChange = { numberInput = QrCodeUtils.sanitizeConnectNumberInput(it) },
            label = { Text("9-Digit Connect Number") },
            placeholder = { Text("014829301") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { onLookupNumber(numberInput) }),
            trailingIcon = {
                IconButton(
                    onClick = { onLookupNumber(numberInput) },
                    enabled = !lookupLoading
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = "Lookup number")
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("connect_number_input")
        )

        Button(
            onClick = { onLookupNumber(numberInput) },
            enabled = !lookupLoading && numberInput.length == 9,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("lookup_number_button")
        ) {
            if (lookupLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Find User")
            }
        }

        LookupResultCard(
            lookupResult = lookupResult,
            lookupError = lookupError,
            friendUids = friendUids,
            pendingOutgoingUids = pendingOutgoingUids,
            onSendFriendRequest = onSendFriendRequest
        )
    }
}

@Composable
private fun QrScannerSection(
    lookupLoading: Boolean,
    lookupResult: ConnectNumberEntry?,
    lookupError: String?,
    friendUids: Set<String>,
    pendingOutgoingUids: Set<String>,
    onQrNumberDetected: (String) -> Unit,
    onSendFriendRequest: (ConnectNumberEntry) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var lastDetectedNumber by remember { mutableStateOf<String?>(null) }
    var galleryError by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPerm = granted
    }

    val galleryQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                galleryError = null
                val raw = withContext(Dispatchers.IO) { QrCodeUtils.decodeQrFromUri(context, uri) }
                val num = QrCodeUtils.extractConnectNumberFromQr(raw)
                if (num != null) {
                    lastDetectedNumber = num
                    onQrNumberDetected(num)
                } else {
                    galleryError = "No valid Connect Live QR code found in that image."
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasCameraPerm) {
            val previewView = remember { PreviewView(context) }
            val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

            DisposableEffect(lifecycleOwner, previewView) {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                val listener = Runnable {
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analyzer ->
                                analyzer.setAnalyzer(analyzerExecutor) { imageProxy ->
                                    try {
                                        val raw = QrCodeUtils.decodeQrFromImageProxy(imageProxy)
                                        val num = QrCodeUtils.extractConnectNumberFromQr(raw)
                                        if (num != null && num != lastDetectedNumber) {
                                            lastDetectedNumber = num
                                            ContextCompat.getMainExecutor(context).execute {
                                                onQrNumberDetected(num)
                                            }
                                        }
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }
                }
                cameraProviderFuture.addListener(listener, ContextCompat.getMainExecutor(context))

                onDispose {
                    runCatching { cameraProviderFuture.get().unbindAll() }
                    analyzerExecutor.shutdown()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(2.dp, AuroraTeal.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0x99090E18),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(10.dp)
                ) {
                    Text(
                        text = if (lastDetectedNumber != null) {
                            "Scanned: $lastDetectedNumber"
                        } else {
                            "Point camera at a Connect Live QR code"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Camera permission needed to scan live QR codes.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Allow Camera Scanner")
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                galleryQrLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan QR from Gallery")
        }

        if (lookupLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }

        galleryError?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = CallCrimson
            )
        }

        LookupResultCard(
            lookupResult = lookupResult,
            lookupError = lookupError,
            friendUids = friendUids,
            pendingOutgoingUids = pendingOutgoingUids,
            onSendFriendRequest = onSendFriendRequest
        )
    }
}

@Composable
private fun LookupResultCard(
    lookupResult: ConnectNumberEntry?,
    lookupError: String?,
    friendUids: Set<String>,
    pendingOutgoingUids: Set<String>,
    onSendFriendRequest: (ConnectNumberEntry) -> Unit
) {
    if (lookupError != null) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CallCrimson.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = lookupError,
                style = MaterialTheme.typography.bodySmall,
                color = CallCrimson,
                modifier = Modifier.padding(14.dp)
            )
        }
    }

    if (lookupResult != null) {
        val feedback = LocalTouchFeedback.current
        val isFriend = lookupResult.uid in friendUids
        val isPending = lookupResult.uid in pendingOutgoingUids

        GlassCard(
            cornerRadius = 22.dp,
            borderColor = if (isFriend) OnlineEmerald.copy(alpha = 0.35f) else AzurePrimary.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(
                    name = lookupResult.displayName,
                    photoUrl = lookupResult.photoUrl,
                    size = 54.dp,
                    online = isFriend
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lookupResult.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${lookupResult.number}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = when {
                            isFriend -> "Already connected as Friend"
                            isPending -> "Friend request sent"
                            else -> "Tap Add Friend to chat & call"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isFriend -> OnlineEmerald
                            isPending -> RingingAmber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                when {
                    isFriend -> {
                        GlassPillChip(
                            containerColor = OnlineEmerald.copy(alpha = 0.14f),
                            borderColor = OnlineEmerald.copy(alpha = 0.35f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = OnlineEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Friends",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnlineEmerald
                            )
                        }
                    }
                    isPending -> {
                        GlassPillChip(
                            containerColor = RingingAmber.copy(alpha = 0.14f),
                            borderColor = RingingAmber.copy(alpha = 0.35f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HourglassTop,
                                contentDescription = null,
                                tint = RingingAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Requested",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = RingingAmber
                            )
                        }
                    }
                    else -> {
                        Button(
                            onClick = {
                                feedback?.triggerTap()
                                onSendFriendRequest(lookupResult)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("send_friend_request_button")
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Friend")
                        }
                    }
                }
            }
        }
    }
}
