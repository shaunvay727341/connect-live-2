package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.CallSession
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.Conversation
import com.example.data.model.DirectoryUser
import com.example.data.model.FriendRequest
import com.example.data.model.FriendRequestStatus
import com.example.data.model.Friendship
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GlassBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassPillChip
import com.example.ui.components.LiquidGlassBottomBar
import com.example.ui.components.LiquidNavItem
import com.example.ui.components.LocalTouchFeedback
import com.example.ui.components.PermissionsReadinessCard
import com.example.ui.components.UserAvatar
import com.example.ui.components.formatCallDuration
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.isGlassDarkTheme
import com.example.ui.theme.AzurePrimary
import com.example.ui.theme.CallCrimson
import com.example.ui.theme.MidnightSurface
import com.example.ui.theme.OnlineEmerald
import com.example.ui.theme.RingingAmber
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    isDarkTheme: Boolean,
    onOpenConversation: (conversationId: String, peer: DirectoryUser?) -> Unit,
    onStartCall: (peer: DirectoryUser, callType: CallType) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val feedback = LocalTouchFeedback.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showConnectSheet by remember { mutableStateOf(false) }
    var connectSheetInitialTab by remember { mutableIntStateOf(0) }

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentUserPrivate by viewModel.currentUserPrivate.collectAsStateWithLifecycle()
    val currentDirectoryUser by viewModel.currentDirectoryUser.collectAsStateWithLifecycle()
    val contactsState by viewModel.filteredContacts.collectAsStateWithLifecycle()
    val friendRequestsState by viewModel.friendRequestsState.collectAsStateWithLifecycle()
    val pendingIncomingCount by viewModel.incomingPendingRequestsCount.collectAsStateWithLifecycle()
    val friendshipsState by viewModel.friendshipsState.collectAsStateWithLifecycle()
    val conversationsState by viewModel.conversationsState.collectAsStateWithLifecycle()
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val missedCallsCount by viewModel.missedCallsCount.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val turnDiagnostic by viewModel.turnDiagnostic.collectAsStateWithLifecycle()
    val isSavingProfile by viewModel.isSavingProfile.collectAsStateWithLifecycle()
    val isUploadingAvatar by viewModel.isUploadingAvatar.collectAsStateWithLifecycle()
    val ringtoneConfig by viewModel.ringtoneConfig.collectAsStateWithLifecycle()
    val lookupLoading by viewModel.lookupLoading.collectAsStateWithLifecycle()
    val lookupResult by viewModel.lookupResult.collectAsStateWithLifecycle()
    val lookupError by viewModel.lookupError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val contactsMap = remember(contactsState) {
        (contactsState as? UiState.Success)?.data?.associateBy { it.uid }.orEmpty()
    }

    val myConnectNumber = currentUserPrivate?.connectNumber?.takeIf { it.isNotBlank() }
        ?: currentDirectoryUser?.connectNumber.orEmpty()
    val myName = currentDirectoryUser?.displayName
        ?: currentUserPrivate?.displayName
        ?: "Connect User"

    val navItems = listOf(
        LiquidNavItem(
            label = stringResource(R.string.tab_chats),
            selectedIcon = Icons.AutoMirrored.Filled.Chat,
            unselectedIcon = Icons.AutoMirrored.Outlined.Chat,
            testTag = "nav_tab_chats"
        ),
        LiquidNavItem(
            label = stringResource(R.string.tab_contacts),
            selectedIcon = Icons.Filled.Contacts,
            unselectedIcon = Icons.Outlined.Contacts,
            badgeCount = pendingIncomingCount,
            testTag = "nav_tab_friends"
        ),
        LiquidNavItem(
            label = stringResource(R.string.tab_calls),
            selectedIcon = Icons.Filled.Call,
            unselectedIcon = Icons.Outlined.Call,
            badgeCount = missedCallsCount,
            testTag = "nav_tab_calls"
        ),
        LiquidNavItem(
            label = stringResource(R.string.tab_profile),
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            testTag = "nav_tab_profile"
        )
    )

    val glassBarBg = if (isGlassDarkTheme()) {
        MidnightSurface.copy(alpha = 0.84f)
    } else {
        Color.White.copy(alpha = 0.84f)
    }

    GlassBackdrop(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = glassBarBg
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (selectedTab) {
                                        0 -> stringResource(R.string.app_name)
                                        1 -> stringResource(R.string.tab_contacts)
                                        2 -> stringResource(R.string.tab_calls)
                                        else -> stringResource(R.string.tab_profile)
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                GlassPillChip(
                                    containerColor = OnlineEmerald.copy(alpha = 0.14f),
                                    borderColor = OnlineEmerald.copy(alpha = 0.30f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(OnlineEmerald)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Live",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = OnlineEmerald
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    feedback?.triggerTap()
                                    connectSheetInitialTab = 2
                                    showConnectSheet = true
                                },
                                modifier = Modifier.testTag("open_qr_connect_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCodeScanner,
                                    contentDescription = "Scan QR or Add 01 Number",
                                    tint = AzurePrimary
                                )
                            }
                            IconButton(
                                onClick = {
                                    feedback?.triggerTap()
                                    viewModel.toggleDarkTheme(!isDarkTheme)
                                },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = if (isDarkTheme) "Switch to light mode" else "Switch to dark mode"
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        LiquidGlassBottomBar(
                            items = navItems,
                            selectedIndex = selectedTab,
                            onSelect = { selectedTab = it }
                        )
                    }
                },
                floatingActionButton = {
                    if (selectedTab == 0) {
                        FloatingActionButton(
                            onClick = {
                                feedback?.triggerTap()
                                selectedTab = 1
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = CircleShape,
                            modifier = Modifier.testTag("new_chat_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AddComment,
                                contentDescription = "New conversation"
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = glassBarBg,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            navItems.forEachIndexed { index, item ->
                                val selected = selectedTab == index
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        feedback?.triggerTap()
                                        selectedTab = index
                                    },
                                    icon = {
                                        if (item.badgeCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(containerColor = CallCrimson, contentColor = Color.White) {
                                                        Text(item.badgeCount.coerceAtMost(99).toString())
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                    contentDescription = item.label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = item.label
                                            )
                                        }
                                    },
                                    label = { Text(item.label) },
                                    modifier = Modifier.testTag(item.testTag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxSize()
                        ) {
                            if (selectedTab == 0 || selectedTab == 1) {
                                SearchBarField(
                                    query = searchQuery,
                                    onQueryChange = viewModel::updateSearchQuery,
                                    placeholder = if (selectedTab == 0) "Search chats..." else "Search friends or 01 numbers..."
                                )
                            }

                            when (selectedTab) {
                                0 -> ChatsTab(
                                    currentUserId = viewModel.currentUserId,
                                    conversationsState = conversationsState,
                                    contactsState = contactsState,
                                    contactsMap = contactsMap,
                                    onOpenConversation = onOpenConversation,
                                    onStartChatWithContact = { peer ->
                                        feedback?.triggerTap()
                                        scope.launch {
                                            val res = viewModel.openConversationWith(peer)
                                            res.onSuccess { convId -> onOpenConversation(convId, peer) }
                                        }
                                    }
                                )
                                1 -> FriendsAndContactsTab(
                                    currentUserId = viewModel.currentUserId,
                                    myConnectNumber = myConnectNumber,
                                    friendRequestsState = friendRequestsState,
                                    friendshipsState = friendshipsState,
                                    contactsState = contactsState,
                                    contactsMap = contactsMap,
                                    onOpenQrConnect = { tabIdx ->
                                        feedback?.triggerTap()
                                        connectSheetInitialTab = tabIdx
                                        showConnectSheet = true
                                    },
                                    onRespondFriendRequest = { req, accept ->
                                        feedback?.triggerTap()
                                        viewModel.respondToFriendRequest(req, accept) { convId, peer ->
                                            onOpenConversation(convId, peer)
                                        }
                                    },
                                    onAddFriend = { peer ->
                                        feedback?.triggerTap()
                                        viewModel.sendFriendRequestToDirectoryUser(peer)
                                    },
                                    onChat = { peer ->
                                        feedback?.triggerTap()
                                        scope.launch {
                                            val res = viewModel.openConversationWith(peer)
                                            res.onSuccess { convId -> onOpenConversation(convId, peer) }
                                        }
                                    },
                                    onAudioCall = { peer ->
                                        feedback?.triggerTap()
                                        onStartCall(peer, CallType.AUDIO)
                                    },
                                    onVideoCall = { peer ->
                                        feedback?.triggerTap()
                                        onStartCall(peer, CallType.VIDEO)
                                    }
                                )
                                2 -> CallsTab(
                                    currentUserId = viewModel.currentUserId,
                                    callsState = callsState,
                                    missedCallsCount = missedCallsCount,
                                    contactsMap = contactsMap,
                                    onCallback = { call, callType ->
                                        feedback?.triggerTap()
                                        val peerId = call.peerId(viewModel.currentUserId)
                                        val peer = contactsMap[peerId] ?: DirectoryUser(
                                            uid = peerId,
                                            displayName = call.peerName(viewModel.currentUserId),
                                            displayNameLower = call.peerName(viewModel.currentUserId).lowercase(),
                                            photoUrl = call.peerPhoto(viewModel.currentUserId)
                                        )
                                        onStartCall(peer, callType)
                                    }
                                )
                                else -> ProfileHubTab(
                                    userPrivate = currentUserPrivate,
                                    directoryUser = currentDirectoryUser,
                                    myConnectNumber = myConnectNumber,
                                    isDarkTheme = isDarkTheme,
                                    isSaving = isSavingProfile,
                                    isUploadingAvatar = isUploadingAvatar,
                                    ringtoneConfig = ringtoneConfig,
                                    turnDiagnostic = turnDiagnostic,
                                    hasCloudImageConfigured = viewModel.hasCloudImageUploadConfigured(),
                                    onShowMyQr = {
                                        connectSheetInitialTab = 0
                                        showConnectSheet = true
                                    },
                                    onUploadAvatar = { uri ->
                                        viewModel.uploadAndSetProfilePhoto(context, uri)
                                    },
                                    onSetIncomingRingtone = viewModel::setIncomingRingtone,
                                    onSetDialTone = viewModel::setDialTone,
                                    onToggleTheme = viewModel::toggleDarkTheme,
                                    onSetTouchSound = viewModel::setTouchSoundEnabled,
                                    onSetHaptics = viewModel::setHapticsEnabled,
                                    onSaveCustomImgBbKey = viewModel::setCustomImgBbKey,
                                    onRefreshTurn = viewModel::refreshTurnDiagnostics,
                                    onSaveProfile = viewModel::updateProfile,
                                    onSignOut = onSignOut
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConnectSheet) {
        ConnectQrBottomSheet(
            myName = myName,
            myConnectNumber = myConnectNumber,
            lookupLoading = lookupLoading,
            lookupResult = lookupResult,
            lookupError = lookupError,
            onLookupNumber = viewModel::lookupByConnectNumber,
            onSendFriendRequest = viewModel::sendFriendRequestToEntry,
            onClearLookup = viewModel::clearLookupState,
            onDismiss = { showConnectSheet = false },
            initialTab = connectSheetInitialTab
        )
    }
}

@Composable
private fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    val isDark = isGlassDarkTheme()
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = AzurePrimary) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (isDark) MidnightSurface.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.88f),
            unfocusedContainerColor = if (isDark) MidnightSurface.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.82f),
            focusedBorderColor = AzurePrimary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("search_input")
    )
}

@Composable
private fun ChatsTab(
    currentUserId: String,
    conversationsState: UiState<List<Conversation>>,
    contactsState: UiState<List<DirectoryUser>>,
    contactsMap: Map<String, DirectoryUser>,
    onOpenConversation: (String, DirectoryUser?) -> Unit,
    onStartChatWithContact: (DirectoryUser) -> Unit
) {
    val onlineUsers = (contactsState as? UiState.Success)?.data?.filter { it.online }.orEmpty()

    when (conversationsState) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> EmptyStateView(
            title = "Unable to load chats",
            subtitle = conversationsState.message,
            modifier = Modifier.fillMaxSize()
        )
        is UiState.Success -> {
            val conversations = conversationsState.data
            LazyColumn(
                contentPadding = PaddingValues(bottom = 112.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "permissions_readiness_banner") {
                    PermissionsReadinessCard(
                        hideWhenAllReady = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                if (onlineUsers.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                            Text(
                                text = "ONLINE NOW",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(onlineUsers, key = { it.uid }) { user ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(68.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { onStartChatWithContact(user) }
                                            .padding(vertical = 6.dp)
                                    ) {
                                        UserAvatar(
                                            name = user.displayName,
                                            photoUrl = user.photoUrl,
                                            size = 54.dp,
                                            online = true
                                        )
                                        Spacer(modifier = Modifier.height(5.dp))
                                        Text(
                                            text = user.displayName.substringBefore(" "),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (conversations.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = stringResource(R.string.no_chats_title),
                            subtitle = stringResource(R.string.no_chats_subtitle),
                            modifier = Modifier.padding(top = 28.dp)
                        )
                    }
                } else {
                    items(conversations, key = { it.id }) { conv ->
                        val peerId = conv.peerUid(currentUserId)
                        val peer = contactsMap[peerId]
                        ConversationRow(
                            conversation = conv,
                            currentUserId = currentUserId,
                            peerOnline = peer?.online,
                            onClick = { onOpenConversation(conv.id, peer) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    currentUserId: String,
    peerOnline: Boolean?,
    onClick: () -> Unit
) {
    val peerName = conversation.peerName(currentUserId)
    val peerPhoto = conversation.peerPhoto(currentUserId)
    val isTyping = conversation.isPeerTyping(currentUserId)
    val preview = if (isTyping) "Typing..." else conversation.lastMessageText.ifBlank { "Tap to start chatting" }

    GlassCard(
        cornerRadius = 22.dp,
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("conversation_item_${conversation.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = peerName,
                photoUrl = peerPhoto,
                size = 52.dp,
                online = peerOnline
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatTimestampShort(conversation.lastMessageAt ?: conversation.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTyping) AzurePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun FriendsAndContactsTab(
    currentUserId: String,
    myConnectNumber: String,
    friendRequestsState: UiState<List<FriendRequest>>,
    friendshipsState: UiState<List<Friendship>>,
    contactsState: UiState<List<DirectoryUser>>,
    contactsMap: Map<String, DirectoryUser>,
    onOpenQrConnect: (tabIndex: Int) -> Unit,
    onRespondFriendRequest: (FriendRequest, Boolean) -> Unit,
    onAddFriend: (DirectoryUser) -> Unit,
    onChat: (DirectoryUser) -> Unit,
    onAudioCall: (DirectoryUser) -> Unit,
    onVideoCall: (DirectoryUser) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val feedback = LocalTouchFeedback.current

    val pendingRequests = (friendRequestsState as? UiState.Success)?.data
        ?.filter { it.parsedStatus == FriendRequestStatus.PENDING }
        .orEmpty()
    val incomingRequests = pendingRequests.filter { it.toUid == currentUserId }
    val outgoingRequests = pendingRequests.filter { it.fromUid == currentUserId }

    val friendships = (friendshipsState as? UiState.Success)?.data.orEmpty()
    val friendUids = remember(friendships) {
        friendships.map { it.peerUid(currentUserId) }.toSet()
    }
    val discoverUsers = (contactsState as? UiState.Success)?.data.orEmpty()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "connect_id_banner") {
            GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "YOUR CONNECT NUMBER",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = myConnectNumber.ifBlank { "Generating..." },
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        IconButton(
                            onClick = {
                                feedback?.triggerTap()
                                if (myConnectNumber.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(myConnectNumber))
                                    Toast.makeText(context, "Copied $myConnectNumber", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy my number")
                        }
                        IconButton(onClick = { onOpenQrConnect(0) }) {
                            Icon(Icons.Rounded.QrCode2, contentDescription = "Show my QR", tint = AzurePrimary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenQrConnect(1) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("friends_add_number_button")
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add by 01...", maxLines = 1)
                        }
                        Button(
                            onClick = { onOpenQrConnect(2) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("friends_qr_scan_button")
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan QR", maxLines = 1)
                        }
                    }
                }
            }
        }

        if (incomingRequests.isNotEmpty()) {
            item(key = "header_incoming_reqs") {
                Text(
                    text = "FRIEND REQUESTS (${incomingRequests.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = RingingAmber,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(incomingRequests, key = { "in_${it.id}" }) { req ->
                GlassCard(cornerRadius = 20.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(name = req.fromName, photoUrl = req.fromPhoto, size = 48.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = req.fromName, style = MaterialTheme.typography.titleMedium)
                            if (req.fromNumber.isNotBlank()) {
                                Text(
                                    text = "ID: ${req.fromNumber}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(
                            onClick = { onRespondFriendRequest(req, false) },
                            modifier = Modifier.testTag("decline_request_${req.id}")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Decline", tint = CallCrimson)
                        }
                        Button(
                            onClick = { onRespondFriendRequest(req, true) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("accept_request_${req.id}")
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accept")
                        }
                    }
                }
            }
        }

        if (outgoingRequests.isNotEmpty()) {
            item(key = "header_outgoing_reqs") {
                Text(
                    text = "SENT REQUESTS (${outgoingRequests.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(outgoingRequests, key = { "out_${it.id}" }) { req ->
                GlassCard(cornerRadius = 18.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(name = req.toName, photoUrl = req.toPhoto, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = req.toName, style = MaterialTheme.typography.titleSmall)
                            if (req.toNumber.isNotBlank()) {
                                Text(text = req.toNumber, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.labelMedium,
                            color = RingingAmber
                        )
                    }
                }
            }
        }

        if (friendships.isNotEmpty()) {
            item(key = "header_friends") {
                Text(
                    text = "MY FRIENDS (${friendships.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = OnlineEmerald,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            items(friendships, key = { "friend_${it.id}" }) { friendship ->
                val peerUid = friendship.peerUid(currentUserId)
                val dirPeer = contactsMap[peerUid] ?: DirectoryUser(
                    uid = peerUid,
                    displayName = friendship.peerName(currentUserId),
                    displayNameLower = friendship.peerName(currentUserId).lowercase(),
                    photoUrl = friendship.peerPhoto(currentUserId),
                    connectNumber = friendship.peerNumber(currentUserId)
                )
                ContactActionCard(
                    user = dirPeer,
                    showAddButton = false,
                    onAddFriend = {},
                    onChat = { onChat(dirPeer) },
                    onAudioCall = { onAudioCall(dirPeer) },
                    onVideoCall = { onVideoCall(dirPeer) }
                )
            }
        }

        item(key = "header_discover") {
            Text(
                text = "DISCOVER PEOPLE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        when (contactsState) {
            UiState.Loading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            is UiState.Error -> item {
                EmptyStateView(
                    title = "Unable to load contacts",
                    subtitle = contactsState.message
                )
            }
            is UiState.Success -> {
                if (discoverUsers.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = stringResource(R.string.no_contacts_title),
                            subtitle = stringResource(R.string.no_contacts_subtitle)
                        )
                    }
                } else {
                    items(discoverUsers, key = { "dir_${it.uid}" }) { user ->
                        val isFriend = user.uid in friendUids
                        ContactActionCard(
                            user = user,
                            showAddButton = !isFriend,
                            onAddFriend = { onAddFriend(user) },
                            onChat = { onChat(user) },
                            onAudioCall = { onAudioCall(user) },
                            onVideoCall = { onVideoCall(user) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactActionCard(
    user: DirectoryUser,
    showAddButton: Boolean,
    onAddFriend: () -> Unit,
    onChat: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    GlassCard(
        cornerRadius = 22.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("contact_card_${user.uid}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                name = user.displayName,
                photoUrl = user.photoUrl,
                size = 50.dp,
                online = user.online
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = buildString {
                    if (user.connectNumber.isNotBlank()) append("${user.connectNumber} • ")
                    if (user.online) append("Online") else append(user.statusText)
                }
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (user.online) OnlineEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showAddButton) {
                IconButton(onClick = onAddFriend) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = "Add ${user.displayName} as friend",
                        tint = AzurePrimary
                    )
                }
            }
            IconButton(
                onClick = onChat,
                modifier = Modifier.testTag("contact_chat_${user.uid}")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = "Message ${user.displayName}",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(
                onClick = onAudioCall,
                modifier = Modifier.testTag("contact_audio_call_${user.uid}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "Audio call ${user.displayName}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onVideoCall,
                modifier = Modifier.testTag("contact_video_call_${user.uid}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Videocam,
                    contentDescription = "Video call ${user.displayName}",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun CallsTab(
    currentUserId: String,
    callsState: UiState<List<CallSession>>,
    missedCallsCount: Int,
    contactsMap: Map<String, DirectoryUser>,
    onCallback: (CallSession, CallType) -> Unit
) {
    when (callsState) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is UiState.Error -> EmptyStateView(
            title = "Unable to load call history",
            subtitle = callsState.message,
            modifier = Modifier.fillMaxSize()
        )
        is UiState.Success -> {
            val calls = callsState.data
            if (calls.isEmpty()) {
                EmptyStateView(
                    title = stringResource(R.string.no_calls_title),
                    subtitle = stringResource(R.string.no_calls_subtitle),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (missedCallsCount > 0) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = CallCrimson.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CallCrimson.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.CallMissed,
                                        contentDescription = null,
                                        tint = CallCrimson
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "$missedCallsCount missed call${if (missedCallsCount > 1) "s" else ""} recorded",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CallCrimson
                                    )
                                }
                            }
                        }
                    }

                    items(calls, key = { it.id }) { call ->
                        val isOutgoing = call.callerId == currentUserId
                        val isMissed = !isOutgoing && call.parsedStatus == CallStatus.MISSED
                        val isDeclined = call.parsedStatus == CallStatus.REJECTED
                        val peerId = call.peerId(currentUserId)
                        val peerName = call.peerName(currentUserId)
                        val peerPhoto = call.peerPhoto(currentUserId)
                        val peerOnline = contactsMap[peerId]?.online

                        val statusText = when {
                            isMissed -> "Missed ${call.parsedType.name.lowercase()} call"
                            isDeclined -> "Declined"
                            call.durationSeconds > 0 -> "${if (isOutgoing) "Outgoing" else "Incoming"} • ${formatCallDuration(call.durationSeconds)}"
                            call.parsedStatus == CallStatus.RINGING -> "Ringing"
                            else -> if (isOutgoing) "Outgoing • No answer" else "Incoming"
                        }

                        GlassCard(
                            cornerRadius = 22.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("call_log_${call.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(
                                    name = peerName,
                                    photoUrl = peerPhoto,
                                    size = 48.dp,
                                    online = peerOnline
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = peerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isMissed) CallCrimson else MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when {
                                                isMissed -> Icons.AutoMirrored.Rounded.CallMissed
                                                isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                                                else -> Icons.AutoMirrored.Rounded.CallReceived
                                            },
                                            contentDescription = null,
                                            tint = if (isMissed || isDeclined) CallCrimson else OnlineEmerald,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "$statusText • ${formatTimestampShort(call.createdAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onCallback(call, CallType.AUDIO) },
                                    modifier = Modifier.testTag("callback_audio_${call.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Call,
                                        contentDescription = "Audio call back $peerName",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = { onCallback(call, CallType.VIDEO) },
                                    modifier = Modifier.testTag("callback_button_${call.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = "Video call back $peerName",
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
