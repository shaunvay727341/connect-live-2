package com.example

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.call.CallEngine
import com.example.data.auth.attemptAutoSignIn
import com.example.data.auth.authStateFlow
import com.example.data.auth.signOutWithCleanup
import com.example.data.model.CallType
import com.example.data.model.DirectoryUser
import com.example.data.prefs.RingtonePreferences
import com.example.data.repository.ConnectLiveRepository
import com.example.data.turn.TurnCredentialsService
import com.example.service.CallActionReceiver
import com.example.service.CallListenerService
import com.example.service.CallNotifications
import com.example.ui.components.FloatingCallBubble
import com.example.ui.components.ProvideTouchFeedback
import com.example.ui.components.checkCallReadiness
import com.example.ui.components.runtimeCallPermissions
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.IncomingCallOverlay
import com.example.ui.theme.ConnectLiveTheme
import com.example.ui.viewmodel.CallPhase
import com.example.ui.viewmodel.CallViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.MainViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_CHAT = "chat/{conversationId}"
private const val ROUTE_ACTIVE_CALL = "active_call"

class MainActivity : ComponentActivity() {

    private val isInPipModeState = mutableStateOf(false)
    private val openActiveCallTrigger = mutableIntStateOf(0)
    private val openConversationIdState = mutableStateOf<String?>(null)

    private var pipCallEligible = false
    private var pipCallType = CallType.AUDIO
    private var pipMuted = false

    private val pipChangedListener = Consumer<PictureInPictureModeChangedInfo> { info ->
        isInPipModeState.value = info.isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            addOnPictureInPictureModeChangedListener(pipChangedListener)
        }
        handleIncomingNavigationIntent(intent)

        setContent {
            val inPip by isInPipModeState
            val openCallCount by openActiveCallTrigger
            val openConversationId by openConversationIdState
            ConnectLiveRoot(
                isInPipMode = inPip,
                openActiveCallTrigger = openCallCount,
                openConversationId = openConversationId,
                onConversationDeepLinkConsumed = { openConversationIdState.value = null },
                onCallPipConfigChanged = { active, callType, muted ->
                    updateCallPipConfig(active, callType, muted)
                },
                onEnterPipRequested = { enterCallPipMode() }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingNavigationIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.R &&
            pipCallEligible &&
            !isInPipModeState.value
        ) {
            enterCallPipMode()
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            removeOnPictureInPictureModeChangedListener(pipChangedListener)
        }
        super.onDestroy()
    }

    private fun handleIncomingNavigationIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == CallActionReceiver.ACTION_ACCEPT_CALL) {
            val callId = intent.getStringExtra(CallNotifications.EXTRA_CALL_ID)
            val engine = CallEngine.getInstance(applicationContext)
            val hasAudio = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            val session = engine.incomingRingingCall.value
            val hasCam = if (session?.parsedType == CallType.VIDEO) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (engine.acceptIncomingFromIntent(callId, hasAudio && hasCam)) {
                openActiveCallTrigger.intValue += 1
            }
            intent.action = null
        }

        if (intent.getBooleanExtra(CallNotifications.EXTRA_OPEN_ACTIVE_CALL, false)) {
            intent.removeExtra(CallNotifications.EXTRA_OPEN_ACTIVE_CALL)
            openActiveCallTrigger.intValue += 1
        }

        val convId = intent.getStringExtra(CallNotifications.EXTRA_OPEN_CONVERSATION_ID)
        if (!convId.isNullOrBlank()) {
            intent.removeExtra(CallNotifications.EXTRA_OPEN_CONVERSATION_ID)
            openConversationIdState.value = convId
        }
    }

    private fun supportsPip(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    private fun updateCallPipConfig(active: Boolean, callType: CallType, muted: Boolean) {
        pipCallEligible = active
        pipCallType = callType
        pipMuted = muted
        if (supportsPip()) {
            try {
                setPictureInPictureParams(buildPipParams(callType, muted, autoEnter = active))
            } catch (_: Exception) {
            }
        }
    }

    private fun enterCallPipMode() {
        if (!supportsPip() || !pipCallEligible) return
        try {
            enterPictureInPictureMode(buildPipParams(pipCallType, pipMuted, autoEnter = true))
        } catch (_: Exception) {
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(
        callType: CallType,
        muted: Boolean,
        autoEnter: Boolean
    ): PictureInPictureParams {
        val ratio = if (callType == CallType.VIDEO) Rational(9, 16) else Rational(1, 1)

        val muteIntent = PendingIntent.getBroadcast(
            this,
            201,
            Intent(this, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_TOGGLE_MUTE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val muteLabel = getString(if (muted) R.string.pip_unmute else R.string.pip_mute)
        val muteAction = RemoteAction(
            Icon.createWithResource(this, if (muted) R.drawable.ic_pip_mic_off else R.drawable.ic_pip_mic_on),
            muteLabel,
            muteLabel,
            muteIntent
        )

        val endIntent = PendingIntent.getBroadcast(
            this,
            202,
            Intent(this, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_HANGUP_CALL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endLabel = getString(R.string.pip_end)
        val endAction = RemoteAction(
            Icon.createWithResource(this, R.drawable.ic_pip_call_end),
            endLabel,
            endLabel,
            endIntent
        )

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(ratio)
            .setActions(listOf(muteAction, endAction))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter)
            builder.setSeamlessResizeEnabled(callType == CallType.VIDEO)
        }
        return builder.build()
    }
}

@Composable
fun ConnectLiveRoot(
    isInPipMode: Boolean,
    openActiveCallTrigger: Int,
    openConversationId: String?,
    onConversationDeepLinkConsumed: () -> Unit,
    onCallPipConfigChanged: (active: Boolean, callType: CallType, muted: Boolean) -> Unit,
    onEnterPipRequested: () -> Unit
) {
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    var autoSignInAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            CallListenerService.stop(context)
            onCallPipConfigChanged(false, CallType.AUDIO, false)
            if (!autoSignInAttempted) {
                autoSignInAttempted = true
                attemptAutoSignIn(
                    context = context,
                    credentialManager = credentialManager,
                    scope = scope,
                    onAuthSuccess = {},
                    onUnauthenticated = {}
                )
            }
        }
    }

    val user = currentUser
    if (user == null) {
        ConnectLiveTheme(darkTheme = false) {
            ProvideTouchFeedback(soundEnabled = true, hapticsEnabled = true) {
                AuthScreen(onAuthSuccess = {})
            }
        }
    } else {
        key(user.uid) {
            AuthenticatedConnectLiveApp(
                currentUserId = user.uid,
                credentialManager = credentialManager,
                isInPipMode = isInPipMode,
                openActiveCallTrigger = openActiveCallTrigger,
                openConversationId = openConversationId,
                onConversationDeepLinkConsumed = onConversationDeepLinkConsumed,
                onCallPipConfigChanged = onCallPipConfigChanged,
                onEnterPipRequested = onEnterPipRequested
            )
        }
    }
}

@Composable
private fun AuthenticatedConnectLiveApp(
    currentUserId: String,
    credentialManager: CredentialManager,
    isInPipMode: Boolean,
    openActiveCallTrigger: Int,
    openConversationId: String?,
    onConversationDeepLinkConsumed: () -> Unit,
    onCallPipConfigChanged: (active: Boolean, callType: CallType, muted: Boolean) -> Unit,
    onEnterPipRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val callEngine = remember(context) { CallEngine.getInstance(context.applicationContext) }
    val ringtonePreferences = remember(context) { RingtonePreferences(context.applicationContext) }

    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                MainViewModel(
                    repository = ConnectLiveRepository(db),
                    turnService = TurnCredentialsService(),
                    ringtonePreferences = RingtonePreferences(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val callViewModel: CallViewModel = viewModel(
        key = "call_$currentUserId",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                CallViewModel(
                    appContext = app,
                    repository = ConnectLiveRepository(db),
                    turnService = TurnCredentialsService(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val ringtoneConfig by mainViewModel.ringtoneConfig.collectAsStateWithLifecycle()
    val isDarkTheme by mainViewModel.isDarkTheme.collectAsStateWithLifecycle()
    val incomingCall by mainViewModel.incomingCall.collectAsStateWithLifecycle()
    val currentDirectoryUser by mainViewModel.currentDirectoryUser.collectAsStateWithLifecycle()
    val currentUserPrivate by mainViewModel.currentUserPrivate.collectAsStateWithLifecycle()
    val activeCallState by callViewModel.uiState.collectAsStateWithLifecycle()

    var activeChatPeer by remember { mutableStateOf<DirectoryUser?>(null) }
    var startupPermissionsPrompted by remember(currentUserId) { mutableStateOf(false) }

    val startupPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        CallListenerService.startIfSignedIn(context)
    }

    LaunchedEffect(currentUserId) {
        CallNotifications.ensureChannels(context)
        CallListenerService.startIfSignedIn(context)
        if (!startupPermissionsPrompted && !checkCallReadiness(context).allRuntimeGranted) {
            startupPermissionsPrompted = true
            startupPermissionsLauncher.launch(runtimeCallPermissions())
        }
    }

    val isCallActiveForPip = activeCallState.phase != CallPhase.IDLE &&
        activeCallState.phase != CallPhase.ENDED &&
        activeCallState.phase != CallPhase.REQUESTING_PERMISSIONS

    LaunchedEffect(isCallActiveForPip, activeCallState.callType, activeCallState.isMuted) {
        onCallPipConfigChanged(isCallActiveForPip, activeCallState.callType, activeCallState.isMuted)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    LaunchedEffect(openActiveCallTrigger, isInPipMode, activeCallState.phase) {
        val shouldOpenCallRoute = (openActiveCallTrigger > 0 || isInPipMode) &&
            callEngine.hasActiveOrPendingCall()
        if (shouldOpenCallRoute && currentRoute != ROUTE_ACTIVE_CALL) {
            callViewModel.setMinimizedInApp(false)
            navController.navigate(ROUTE_ACTIVE_CALL) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(openConversationId) {
        val convId = openConversationId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        activeChatPeer = null
        navController.navigate("chat/$convId") {
            launchSingleTop = true
        }
        onConversationDeepLinkConsumed()
    }

    val latestIsInPipMode by rememberUpdatedState(isInPipMode)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentUserId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    callEngine.isAppInForeground = true
                    mainViewModel.setOnlinePresence(true)
                    CallListenerService.startIfSignedIn(context)
                }
                Lifecycle.Event.ON_STOP -> {
                    callEngine.isAppInForeground = false
                    if (!callEngine.hasActiveOrPendingCall() && !latestIsInPipMode) {
                        mainViewModel.setOnlinePresence(false)
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            callEngine.isAppInForeground = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val startCallToPeer: (DirectoryUser, CallType) -> Unit = { peer, callType ->
        val caller = currentDirectoryUser ?: DirectoryUser(
            uid = currentUserId,
            displayName = currentUserPrivate?.displayName ?: "Connect User",
            displayNameLower = (currentUserPrivate?.displayName ?: "connect user").lowercase(),
            photoUrl = currentUserPrivate?.photoUrl ?: ""
        )
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val hasCam = if (callType == CallType.VIDEO) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        callViewModel.setMinimizedInApp(false)
        callViewModel.prepareOutgoingCall(
            caller = caller,
            callee = peer,
            callType = callType,
            hasRequiredPermissions = hasAudio && hasCam
        )
        navController.navigate(ROUTE_ACTIVE_CALL) {
            launchSingleTop = true
        }
    }

    ConnectLiveTheme(darkTheme = isDarkTheme) {
        ProvideTouchFeedback(
            soundEnabled = ringtoneConfig.touchSoundEnabled,
            hapticsEnabled = ringtoneConfig.hapticsEnabled
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_DASHBOARD
                ) {
                    composable(ROUTE_DASHBOARD) {
                        DashboardScreen(
                            viewModel = mainViewModel,
                            isDarkTheme = isDarkTheme,
                            onOpenConversation = { conversationId, peer ->
                                activeChatPeer = peer
                                navController.navigate("chat/$conversationId") {
                                    launchSingleTop = true
                                }
                            },
                            onStartCall = startCallToPeer,
                            onSignOut = {
                                signOutWithCleanup(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onBeforeSignOut = {
                                        CallListenerService.stop(context)
                                        if (callEngine.hasActiveOrPendingCall()) {
                                            callEngine.endActiveCall()
                                        }
                                        mainViewModel.markOfflineBeforeSignOut()
                                    }
                                )
                            }
                        )
                    }

                    composable(
                        route = ROUTE_CHAT,
                        arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
                    ) { entry ->
                        val convId = entry.arguments?.getString("conversationId") ?: return@composable
                        val chatViewModel: ChatViewModel = viewModel(
                            key = "chat_${currentUserId}_$convId",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val db = FirebaseFirestore.getInstance(app.getString(R.string.firestore_database_id))
                                    ChatViewModel(
                                        repository = ConnectLiveRepository(db),
                                        conversationId = convId,
                                        currentUserId = currentUserId,
                                        ringtonePreferences = ringtonePreferences
                                    )
                                }
                            }
                        )
                        val myName = currentDirectoryUser?.displayName
                            ?: currentUserPrivate?.displayName
                            ?: "Connect User"

                        ChatScreen(
                            viewModel = chatViewModel,
                            currentUserName = myName,
                            peerDirectoryUser = activeChatPeer,
                            onBack = { navController.popBackStack() },
                            onStartCall = startCallToPeer
                        )
                    }

                    composable(ROUTE_ACTIVE_CALL) {
                        val callbackPeer = remember(
                            activeCallState.peerId,
                            activeCallState.peerName,
                            activeCallState.peerPhoto
                        ) {
                            activeCallState.peerId.takeIf { it.isNotBlank() }?.let { pid ->
                                DirectoryUser(
                                    uid = pid,
                                    displayName = activeCallState.peerName.ifBlank { "Connect User" },
                                    displayNameLower = activeCallState.peerName.lowercase(),
                                    photoUrl = activeCallState.peerPhoto
                                )
                            }
                        }

                        ActiveCallScreen(
                            viewModel = callViewModel,
                            isInPipMode = isInPipMode,
                            onMinimizeToPip = onEnterPipRequested,
                            onMinimizeInApp = {
                                callViewModel.setMinimizedInApp(true)
                                if (navController.currentDestination?.route == ROUTE_ACTIVE_CALL) {
                                    navController.popBackStack()
                                }
                            },
                            onCallbackAudio = {
                                val target = callbackPeer ?: return@ActiveCallScreen
                                callViewModel.dismissEndedState()
                                startCallToPeer(target, CallType.AUDIO)
                            },
                            onCallbackVideo = {
                                val target = callbackPeer ?: return@ActiveCallScreen
                                callViewModel.dismissEndedState()
                                startCallToPeer(target, CallType.VIDEO)
                            },
                            onMessagePeer = {
                                val target = callbackPeer ?: return@ActiveCallScreen
                                callViewModel.dismissEndedState()
                                scope.launch {
                                    val res = mainViewModel.openConversationWith(target)
                                    res.onSuccess { convId ->
                                        activeChatPeer = target
                                        navController.navigate("chat/$convId") {
                                            popUpTo(ROUTE_DASHBOARD)
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            },
                            onCallFinished = {
                                callViewModel.setMinimizedInApp(false)
                                if (navController.currentDestination?.route == ROUTE_ACTIVE_CALL) {
                                    navController.popBackStack()
                                }
                            }
                        )
                    }
                }

                // In-App Draggable Floating Mini Call Bubble (imo-style)
                if (activeCallState.isMinimizedInApp &&
                    callEngine.hasActiveOrPendingCall() &&
                    currentRoute != ROUTE_ACTIVE_CALL &&
                    !isInPipMode
                ) {
                    FloatingCallBubble(
                        state = activeCallState,
                        eglContext = remember(
                            activeCallState.isMediaReady,
                            activeCallState.callId,
                            activeCallState.phase
                        ) { callViewModel.eglContext() },
                        onAttachRemote = callViewModel::attachRemoteRenderer,
                        onDetachRemote = callViewModel::detachRemoteRenderer,
                        onToggleMute = callViewModel::toggleMute,
                        onExpand = {
                            callViewModel.setMinimizedInApp(false)
                            navController.navigate(ROUTE_ACTIVE_CALL) {
                                launchSingleTop = true
                            }
                        },
                        onEndCall = callViewModel::endActiveCall,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                    )
                }

                // Incoming Call Full-Screen Overlay (when app is foregrounded)
                val pendingIncoming = incomingCall
                if (pendingIncoming != null &&
                    currentRoute != ROUTE_ACTIVE_CALL &&
                    !isInPipMode &&
                    !callEngine.hasActiveOrPendingCall()
                ) {
                    IncomingCallOverlay(
                        callSession = pendingIncoming,
                        onAccept = {
                            val hasAudio = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            val hasCam = if (pendingIncoming.parsedType == CallType.VIDEO) {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                            } else {
                                true
                            }
                            callViewModel.setMinimizedInApp(false)
                            callViewModel.prepareIncomingCallAcceptance(
                                session = pendingIncoming,
                                hasRequiredPermissions = hasAudio && hasCam
                            )
                            navController.navigate(ROUTE_ACTIVE_CALL) {
                                launchSingleTop = true
                            }
                        },
                        onReject = {
                            callViewModel.rejectIncomingCall(pendingIncoming.id)
                        }
                    )
                }
            }
        }
    }
}
