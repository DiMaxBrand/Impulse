package eu.siacs.conversations.ui

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import androidx.activity.compose.BackHandler
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateUtils
import android.widget.EditText
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.siacs.conversations.AppSettings
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.Conversational
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.entities.Reaction
import eu.siacs.conversations.entities.Transferable
import eu.siacs.conversations.ui.adapter.MessageAdapter
import eu.siacs.conversations.utils.MessageUtils
import eu.siacs.conversations.utils.UIHelper
import im.conversations.android.xmpp.model.reactions.Restrictions
import im.conversations.android.xmpp.model.stanza.Presence
import im.conversations.android.xmpp.model.state.Composing
import eu.siacs.conversations.xmpp.manager.ChatStateManager
import eu.siacs.conversations.xmpp.manager.EntityTimeManager
import eu.siacs.conversations.xmpp.manager.JingleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.withContext

sealed class RecordingUiState {
    object Idle : RecordingUiState()
    data class Active(val elapsedMs: Long, val paused: Boolean) : RecordingUiState()
}

/** Observable state for the Compose conversation screen. */
class ConversationScreenState {
    // neverEqualPolicy: Conversation is a mutable Java object — same reference after an
    // in-place status update looks equal to structuralEqualityPolicy, so Compose would skip
    // recomposition. neverEqualPolicy treats every write as a change.
    internal val conversation = mutableStateOf<Conversation?>(null, androidx.compose.runtime.neverEqualPolicy())
    // neverEqualPolicy: same Message references after in-place status mutation look equal to
    // structuralEqualityPolicy, causing Compose to skip recomposition and leaving the status
    // icon stale. neverEqualPolicy treats every write as a change.
    internal val messages = mutableStateOf<List<Message>>(emptyList(), androidx.compose.runtime.neverEqualPolicy())
    internal val revision = mutableIntStateOf(0)
    internal val unreadCount = mutableIntStateOf(0)
    internal val inputText = mutableStateOf("")
    internal val replyingTo = mutableStateOf<Message?>(null)
    internal val correcting = mutableStateOf<Message?>(null)
    internal val attachments: SnapshotStateList<eu.siacs.conversations.ui.util.Attachment> =
        mutableStateListOf()
    val recordingState = mutableStateOf<RecordingUiState>(RecordingUiState.Idle)

    internal val pinnedMessages = mutableStateOf<List<Message>>(emptyList())
    internal val pinnedBannerVisible = mutableStateOf(false)
    internal val requestScrollToUuid = mutableStateOf<String?>(null)
    internal val deleteTarget = mutableStateOf<Message?>(null)
    internal val moderateTarget = mutableStateOf<Message?>(null)
    // Both set by the Fragment after MediaSelectionActivity returns a result — Compose can't be
    // handed a result directly (the picker is a separate Activity), so these are the drop box.
    internal val deleteGroupTarget = mutableStateOf<List<Message>?>(null)
    internal val pendingSelectionMerge = mutableStateOf<List<String>?>(null)
    // message UUIDs that a remote peer is actively editing right now
    internal val remoteEditingIds = mutableStateOf<Set<String>>(emptySet())

    fun update(conversation: Conversation?, source: List<Message>) {
        this.conversation.value = conversation
        messages.value = source
        unreadCount.intValue = conversation?.unreadCount() ?: 0
        revision.intValue++
    }

    fun setRemoteEditing(messageId: String, active: Boolean) {
        val current = remoteEditingIds.value
        remoteEditingIds.value = if (active) current + messageId else current - messageId
    }

    fun updatePinned(pinned: List<Message>) {
        pinnedMessages.value = pinned
        if (pinned.isEmpty()) pinnedBannerVisible.value = false
        else if (!pinnedBannerVisible.value) pinnedBannerVisible.value = true
    }

    fun setInput(text: String) {
        inputText.value = text
    }

    fun getInput(): String = inputText.value
}

/** Actions the screen delegates back to the hosting fragment. */
interface ConversationScreenListener {
    fun onBackPressed()

    fun onSendTextMessage(body: String)

    fun onAttachImage()

    fun onTakePhoto()

    fun onAttachFile()

    fun onCall(video: Boolean)

    fun onOpenDetails()

    fun onOpenMessage(message: Message)

    /** Grid tile tapped — [messages] is the whole batch, [tapped] the specific cell. */
    fun onOpenMediaGroup(messages: List<Message>, tapped: Message)

    fun onDownloadMessage(message: Message)

    fun onLoadMoreMessages()

    fun onScrolledToBottom()

    // Progressive read-marking: called with the UUID of the newest message that has actually
    // been on screen (present in the LazyColumn's visibleItemsInfo, i.e. genuinely rendered —
    // not a recycled/off-screen slot), as the user scrolls through an unread run. Marks
    // everything up to and including it as read, same as XEP-0333 "displayed" semantics.
    fun onMarkReadUpTo(uuid: String)

    fun onStartRecording()

    fun onPauseRecording()

    fun onCancelRecording()

    fun onSendRecording()

    fun onInputChanged(text: String)

    fun onCommitAttachments()

    fun onSearchMessages()

    fun onInviteContact()

    fun onChooseEncryption()

    fun onMuteConversation()

    fun onUnmuteConversation()

    fun onTogglePinned()

    fun onClearHistory()

    fun onBlockContact()

    fun onArchiveConversation()

    fun onSendReactions(message: Message, reactions: Set<String>)
    fun onAddReaction(message: Message)
    fun onShowReactionDetails(message: Message, emoji: String)
    fun onScrollToMessage(message: Message)
    fun onCopyLink(message: Message)
    fun onCopyUrl(message: Message)
    fun onShareMessage(message: Message)
    fun onForwardMessage(message: Message)
    fun onPrivateMessage(message: Message)
    fun onSaveFile(message: Message)
    fun onDeleteMessage(message: Message)
    fun onDeleteForEveryone(message: Message)
    fun onDeleteForMyself(message: Message)
    fun onModerateMessage(message: Message)
    fun onEditingStarted(message: Message)
    fun onEditingStopped(message: Message)
    fun onCancelTransmission(message: Message)
    fun onResendMessage(message: Message)
    fun onRetryAsP2P(message: Message)
    fun onPinMessage(message: Message)
    fun onUnpinMessage(message: Message)

    fun onDeleteSelectedMessages(messages: List<Message>)

    /** "Delete for everyone" on a whole grid tile — every message must be uniformly
     * retractable (checked by the caller); loops the same per-message retraction the
     * single-message flow uses. */
    fun onDeleteMediaGroupForEveryone(messages: List<Message>)

    /** XEP-0425 moderation of every message in a grid tile, when uniformly moderatable. */
    fun onModerateMediaGroup(messages: List<Message>)
    fun onCopySelectedMessages(messages: List<Message>)
    fun onForwardSelectedMessages(messages: List<Message>)

    /** Opens [MediaSelectionActivity] to hand-pick a subset of [messages] — the "+N" overflow
     * cell of an in-progress selection, or the "Select Photos"/"Select to delete" grid actions.
     * [forDelete] tells the caller which sheet to open with the picked result: merge into the
     * ongoing chat selection (false), or [DeleteGroupSheet] scoped to exactly that subset (true). */
    fun onOpenMediaSelector(messages: List<Message>, forDelete: Boolean)
}

object ConversationScreenHelper {
    @JvmStatic
    fun setup(
        composeView: ComposeView,
        state: ConversationScreenState,
        listener: ConversationScreenListener,
    ) {
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent { ImpulseExpressiveTheme { ConversationScreen(state, listener) } }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImpulseExpressiveTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val isSamsung = remember { Build.MANUFACTURER.equals("samsung", ignoreCase = true) }
    val rawColorScheme = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    // Samsung One UI 8.5 generates a much darker tone for surfaceContainerHigh than the M3
    // spec expects (~tone 70 vs. the standard ~92), making incoming chat bubbles appear as a
    // dark gray in light mode. Remap to the lighter surfaceContainerLow tier on Samsung.
    val colorScheme = if (isSamsung && !isDark) {
        rawColorScheme.copy(surfaceContainerHigh = rawColorScheme.surfaceContainerLow)
    } else rawColorScheme
    // Material3 has no built-in "success" role. We pick a green seed and harmonize its hue
    // toward the dynamic primary (same algorithm M3 itself uses), so it still feels designed
    // together with the wallpaper-derived palette instead of clashing as a flat, static green.
    val successColors = remember(colorScheme.primary, isDark) {
        val seedGreen = 0xFF2E7D32.toInt()
        val harmonized =
            com.google.android.material.color.utilities.Blend.harmonize(
                seedGreen,
                colorScheme.primary.toArgb(),
            )
        val palette = com.google.android.material.color.utilities.TonalPalette.fromInt(harmonized)
        if (isDark) {
            SuccessColors(success = Color(palette.tone(80)), onSuccess = Color(palette.tone(20)))
        } else {
            SuccessColors(success = Color(palette.tone(40)), onSuccess = Color(palette.tone(100)))
        }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalSuccessColors provides successColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}

/** Material3 has no standard "success" role; this is harmonized with the dynamic palette in [ImpulseExpressiveTheme]. */
private data class SuccessColors(val success: Color, val onSuccess: Color)

private val LocalSuccessColors =
    androidx.compose.runtime.staticCompositionLocalOf { SuccessColors(Color(0xFF2E7D32), Color.White) }

/** A chronological list entry: either a message bubble or a date pill. */
private sealed interface ChatItem {
    val key: String

    data class Msg(
        val message: Message,
        val firstOfGroup: Boolean,
        val lastOfGroup: Boolean,
    ) : ChatItem {
        override val key: String
            get() = message.getUuid() ?: message.hashCode().toString()
    }

    data class DatePill(val timestamp: Long) : ChatItem {
        override val key: String
            get() = "date-$timestamp"
    }

    data class NewMessagesPill(val count: Int) : ChatItem {
        override val key: String
            get() = "new-messages"
    }

    /** A run of 2+ consecutive same-sender photos/videos, rendered as one grid tile. */
    data class MediaGroup(
        val messages: List<Message>,
        val firstOfGroup: Boolean,
        val lastOfGroup: Boolean,
    ) : ChatItem {
        override val key: String
            get() = messages.first().getUuid() ?: messages.hashCode().toString()
    }
}

/** Would render as its own photo/video thumbnail — the unit media grouping operates on, and (via
 * ConversationComposeFragment.onOpenMessage()) what decides whether a tap opens the in-app
 * MediaViewerActivity instead of handing off to an external app. PDFs get fileParams.width/height
 * populated too (needed for the page-render preview), so without the explicit mime check below
 * they'd satisfy this the same as a real photo — landing in the in-app viewer's single-page,
 * baked-in-icon preview instead of the user's own PDF app, which is wrong for a multi-page
 * document (and unreadable for anything needing real fidelity, like a QR code). Restricted to
 * genuinely visual media only; anything else with dimensions still falls through to the external
 * "isFileOrImage" branch right below the isMediaCell() check at that call site. */
internal fun isMediaCell(message: Message): Boolean {
    val mime = message.mimeType
    val isVisualMedia = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true
    return isVisualMedia &&
        message.isFileOrImage &&
        !message.isDeleted &&
        message.encryption != Message.ENCRYPTION_PGP &&
        message.encryption != Message.ENCRYPTION_DECRYPTION_FAILED &&
        message.fileParams.width > 0 &&
        message.fileParams.height > 0
}

/** Would render with a real page/frame thumbnail in its own single bubble — a strictly broader
 * set than [isMediaCell]: also true for PDFs, which get a rendered-first-page preview same as a
 * photo, just with the baked-in "open PDF" icon watermarked on top so it's still obviously not a
 * photo. Deliberately NOT used for grid-grouping eligibility or the in-app-viewer-vs-external-app
 * tap decision — those stay on isMediaCell() specifically, since a PDF grouped into a multi-photo
 * grid (whose tap handler always opens the in-app viewer, unconditionally) or opened in the
 * single-page in-app viewer would still be wrong for the same reasons isMediaCell() excludes it.
 * This only governs which composable draws the bubble's resting content. */
internal fun hasBubbleThumbnailPreview(message: Message): Boolean {
    val mime = message.mimeType
    val isPreviewableMedia =
        mime?.startsWith("image/") == true || mime?.startsWith("video/") == true || mime == "application/pdf"
    return isPreviewableMedia &&
        message.isFileOrImage &&
        !message.isDeleted &&
        message.encryption != Message.ENCRYPTION_PGP &&
        message.encryption != Message.ENCRYPTION_DECRYPTION_FAILED &&
        message.fileParams.width > 0 &&
        message.fileParams.height > 0
}

private fun sameDay(a: Long, b: Long): Boolean {
    return UIHelper.sameDay(a, b)
}

// Compresses content horizontally (scaleX only, height untouched) instead of ellipsizing when it
// doesn't fit — measures the child unconstrained to get its natural width, then scales it down to
// the available width if needed. Below minScale it clips rather than shrinking further, so text
// never becomes illegibly small.
private fun Modifier.squeezeToFit(minScale: Float = 0.65f): Modifier =
    this.layout { measurable, constraints ->
        val natural = measurable.measure(Constraints(maxHeight = constraints.maxHeight))
        val availableWidth = constraints.maxWidth
        if (availableWidth == Constraints.Infinity || natural.width <= availableWidth) {
            layout(natural.width, natural.height) { natural.placeRelative(0, 0) }
        } else {
            val scale = (availableWidth.toFloat() / natural.width.toFloat()).coerceAtLeast(minScale)
            val placedWidth = (natural.width * scale).toInt().coerceIn(0, availableWidth)
            layout(placedWidth, natural.height) {
                natural.placeWithLayer(0, 0) {
                    scaleX = scale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    clip = true
                }
            }
        }
    }

private fun sameSender(a: Message, b: Message): Boolean {
    val aReceived = a.status == Message.STATUS_RECEIVED
    val bReceived = b.status == Message.STATUS_RECEIVED
    if (aReceived != bReceived) return false
    if (!aReceived) return true
    val ac = a.counterpart
    val bc = b.counterpart
    return ac != null && bc != null && ac == bc
}

private const val GROUP_WINDOW_MILLIS = 5 * 60 * 1000L

private fun groupable(a: Message, b: Message): Boolean {
    if (a.type == Message.TYPE_STATUS || b.type == Message.TYPE_STATUS) return false
    if (a.type == Message.TYPE_RTP_SESSION || b.type == Message.TYPE_RTP_SESSION) return false
    return sameSender(a, b) &&
        Math.abs(a.timeSent - b.timeSent) < GROUP_WINDOW_MILLIS &&
        sameDay(a.timeSent, b.timeSent)
}

/**
 * Builds the display list, newest first (for the reversed LazyColumn). [newMessagesBoundaryUuid]
 * and [newMessagesCount] are frozen at conversation-open time by the caller (not recomputed live
 * as messages get progressively marked read) — the pill marks "where you left off when you
 * opened this", not a running unread count that would shrink/flicker as you scroll.
 */
private fun buildChatItems(
    messages: List<Message>,
    newMessagesBoundaryUuid: String? = null,
    newMessagesCount: Int = 0,
): List<ChatItem> {
    val chronological = ArrayList<ChatItem>(messages.size + 8)
    var pillInserted = newMessagesBoundaryUuid == null
    var i = 0
    while (i < messages.size) {
        val message = messages[i]
        val previous = messages.getOrNull(i - 1)
        if (previous == null || !sameDay(previous.timeSent, message.timeSent)) {
            chronological.add(ChatItem.DatePill(message.timeSent))
        }
        if (!pillInserted && message.getUuid() == newMessagesBoundaryUuid) {
            chronological.add(ChatItem.NewMessagesPill(newMessagesCount))
            pillInserted = true
        }
        // Collect a run of consecutive, groupable, media-only messages so they can render as one
        // grid tile — but never merge across the new-messages boundary, so the pill always lands
        // between two items rather than mid-group.
        if (isMediaCell(message)) {
            val run = ArrayList<Message>()
            run.add(message)
            var j = i + 1
            while (j < messages.size) {
                val candidate = messages[j]
                if (candidate.getUuid() == newMessagesBoundaryUuid) break
                if (isMediaCell(candidate) && groupable(run.last(), candidate)) {
                    run.add(candidate)
                    j++
                } else {
                    break
                }
            }
            if (run.size >= 2) {
                val next = messages.getOrNull(j)
                val firstOfGroup = previous == null || !groupable(previous, message)
                val lastOfGroup =
                    next == null || !groupable(run.last(), next) || !sameDay(run.last().timeSent, next.timeSent)
                chronological.add(ChatItem.MediaGroup(run, firstOfGroup, lastOfGroup))
                i = j
                continue
            }
        }
        val next = messages.getOrNull(i + 1)
        val firstOfGroup = previous == null || !groupable(previous, message)
        val lastOfGroup =
            next == null || !groupable(message, next) || !sameDay(message.timeSent, next.timeSent)
        chronological.add(ChatItem.Msg(message, firstOfGroup, lastOfGroup))
        i++
    }
    return chronological.asReversed()
}

@Composable
fun ConversationScreen(state: ConversationScreenState, listener: ConversationScreenListener) {
    val conversation = state.conversation.value
    val context = LocalContext.current
    var menuTarget by remember { mutableStateOf<Message?>(null) }
    // Set alongside menuTarget when the long-press originated on a grid tile — lets the context
    // sheet's Delete action operate on the whole batch instead of silently acting on just the
    // first message it happens to represent.
    var menuTargetGroup by remember { mutableStateOf<List<Message>?>(null) }
    // Bottom sheet offering "All Photos" vs "Select Photos" when "Select" is tapped on a grid
    // tile's context sheet — set to the tapped tile's whole batch, null when not showing.
    var selectPopupGroup by remember { mutableStateOf<List<Message>?>(null) }
    // First-time edit/delete explainer — set by MessageContextSheet's onNeedsOnboarding, run
    // once MessageActionOnboardingSheet is dismissed (Got it), never again for that action.
    var pendingOnboarding by remember { mutableStateOf<OnboardingKind?>(null) }
    var onboardingContinuation by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Multi-select is pure screen-local UI state — nothing here has a business-logic effect
    // until one of the batch actions in the top bar actually fires, so (like menuTarget above)
    // it lives as local Compose state rather than in ConversationScreenState.
    val selectedUuids = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var deleteSelectedConfirm by remember { mutableStateOf(false) }
    // Back press while a selection is active should clear the selection first, not leave the
    // conversation — same "back backs out of the mode before backing out of the screen" pattern
    // as e.g. exiting search.
    BackHandler(enabled = selectedUuids.isNotEmpty()) { selectedUuids.clear() }
    Scaffold(
        topBar = {
            ConversationTopBar(
                conversation = conversation,
                revision = state.revision.intValue,
                listener = listener,
                selectedCount = selectedUuids.size,
                copyEnabled = state.messages.value.any {
                    it.getUuid() in selectedUuids && !it.isFileOrImage && !it.body.isNullOrBlank()
                },
                onExitSelection = { selectedUuids.clear() },
                onForwardSelected = {
                    val selected = state.messages.value.filter { it.getUuid() in selectedUuids }
                    selectedUuids.clear()
                    if (selected.isNotEmpty()) listener.onForwardSelectedMessages(selected)
                },
                onCopySelected = {
                    val selected = state.messages.value.filter { it.getUuid() in selectedUuids }
                    selectedUuids.clear()
                    if (selected.isNotEmpty()) listener.onCopySelectedMessages(selected)
                },
                onDeleteSelected = { deleteSelectedConfirm = true },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        // The activity is not edge-to-edge: the system already insets the window for status/nav
        // bars before Compose sees it. Letting Scaffold re-apply those insets doubles them,
        // producing a gray gap above the top bar (especially visible when the keyboard opens).
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val pinned = state.pinnedMessages.value
            val bannerVisible = state.pinnedBannerVisible.value
            if (bannerVisible && pinned.isNotEmpty()) {
                PinnedBanner(
                    pinnedMessages = pinned,
                    onDismiss = { state.pinnedBannerVisible.value = false },
                    onUnpin = { listener.onUnpinMessage(it) },
                    onScrollTo = { listener.onScrollToMessage(it) },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                MessageList(
                    state = state,
                    listener = listener,
                    onLongPress = {
                        menuTarget = it
                        menuTargetGroup = null
                    },
                    onLongPressGroupCell = { tapped, messages ->
                        menuTarget = tapped
                        menuTargetGroup = messages
                    },
                    onOpenSelector = { messages -> listener.onOpenMediaSelector(messages, false) },
                    selectedUuids = selectedUuids,
                    onToggleSelected = { uuid ->
                        if (!selectedUuids.remove(uuid)) selectedUuids.add(uuid)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            InputBar(state = state, listener = listener)
        }
    }
    // A picker-screen result to merge into the ongoing chat selection — set by the hosting
    // Fragment (which can't reach this Compose-local selectedUuids list directly, since
    // MediaSelectionActivity's result only ever reaches it, not this composable) via
    // ConversationScreenState, the one channel that crosses that boundary.
    LaunchedEffect(state.pendingSelectionMerge.value) {
        val toMerge = state.pendingSelectionMerge.value
        if (toMerge != null) {
            toMerge.forEach { uuid -> if (uuid !in selectedUuids) selectedUuids.add(uuid) }
            state.pendingSelectionMerge.value = null
        }
    }
    val target = menuTarget
    if (target != null) {
        MessageContextSheet(
            message = target,
            groupMessages = menuTargetGroup,
            state = state,
            listener = listener,
            onSelect = { it.getUuid()?.let { uuid -> selectedUuids.add(uuid) } },
            onSelectGroup = { selectPopupGroup = it },
            onSelectToDelete = { listener.onOpenMediaSelector(it, true) },
            onDeleteGroup = { state.deleteGroupTarget.value = it },
            onNeedsOnboarding = { kind, action ->
                pendingOnboarding = kind
                onboardingContinuation = action
            },
            onDismiss = {
                menuTarget = null
                menuTargetGroup = null
            },
        )
    }
    val onboardingKind = pendingOnboarding
    if (onboardingKind != null) {
        MessageActionOnboardingSheet(
            kind = onboardingKind,
            onDismiss = {
                val prefs = eu.siacs.conversations.utils.OnboardingPreferences(context)
                when (onboardingKind) {
                    OnboardingKind.EDIT -> prefs.hasSeenEditOnboarding = true
                    OnboardingKind.DELETE -> prefs.hasSeenDeleteOnboarding = true
                }
                pendingOnboarding = null
                val action = onboardingContinuation
                onboardingContinuation = null
                action?.invoke()
            },
        )
    }
    val popupGroup = selectPopupGroup
    if (popupGroup != null) {
        SelectModePopupSheet(
            onAllPhotos = {
                selectPopupGroup = null
                popupGroup.forEach { it.getUuid()?.let { uuid -> if (uuid !in selectedUuids) selectedUuids.add(uuid) } }
            },
            onSelectPhotos = {
                selectPopupGroup = null
                listener.onOpenMediaSelector(popupGroup, false)
            },
            onDismiss = { selectPopupGroup = null },
        )
    }
    val groupToDelete = state.deleteGroupTarget.value
    if (groupToDelete != null) {
        DeleteGroupSheet(
            messages = groupToDelete,
            onDeleteForEveryone = {
                state.deleteGroupTarget.value = null
                listener.onDeleteMediaGroupForEveryone(groupToDelete)
            },
            onDeleteForMyself = {
                state.deleteGroupTarget.value = null
                listener.onDeleteSelectedMessages(groupToDelete)
            },
            onModerate = {
                state.deleteGroupTarget.value = null
                listener.onModerateMediaGroup(groupToDelete)
            },
            onDismiss = { state.deleteGroupTarget.value = null },
        )
    }
    if (deleteSelectedConfirm) {
        val selected = state.messages.value.filter { it.getUuid() in selectedUuids }
        DeleteSelectedMessagesDialog(
            count = selected.size,
            onConfirm = {
                deleteSelectedConfirm = false
                selectedUuids.clear()
                if (selected.isNotEmpty()) listener.onDeleteSelectedMessages(selected)
            },
            onDismiss = { deleteSelectedConfirm = false },
        )
    }
    val deleteTarget = state.deleteTarget.value
    if (deleteTarget != null) {
        DeleteMessageSheet(
            message = deleteTarget,
            onDeleteForEveryone = {
                state.deleteTarget.value = null
                listener.onDeleteForEveryone(deleteTarget)
            },
            onDeleteForMyself = {
                state.deleteTarget.value = null
                listener.onDeleteForMyself(deleteTarget)
            },
            onModerate = {
                state.deleteTarget.value = null
                if (isModerationDisclaimerAcked()) {
                    listener.onModerateMessage(deleteTarget)
                } else {
                    state.moderateTarget.value = deleteTarget
                }
            },
            onDismiss = { state.deleteTarget.value = null },
        )
    }
    val moderateTarget = state.moderateTarget.value
    if (moderateTarget != null) {
        ModerationDisclaimerDialog(
            onConfirm = { doNotShowAgain ->
                if (doNotShowAgain) markModerationDisclaimerAcked()
                state.moderateTarget.value = null
                listener.onModerateMessage(moderateTarget)
            },
            onDismiss = { state.moderateTarget.value = null },
        )
    }
}

/** M3 Expressive floating menu: large rounded container on surfaceContainer. */
@Composable
internal fun ExpressiveDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

@Composable
internal fun ExpressiveMenuItem(iconRes: Int, label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun PinnedBanner(
    pinnedMessages: List<Message>,
    onDismiss: () -> Unit,
    onUnpin: (Message) -> Unit,
    onScrollTo: (Message) -> Unit,
) {
    var currentIndex by remember(pinnedMessages) { mutableIntStateOf(0) }
    val message = pinnedMessages.getOrNull(currentIndex) ?: return
    val total = pinnedMessages.size

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_push_pin_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f).clickable {
                    onScrollTo(message)
                    currentIndex = (currentIndex + 1) % total
                }
            ) {
                Text(
                    text = if (total > 1)
                        stringResource(R.string.pinned_message) + " ${currentIndex + 1}/$total"
                    else
                        stringResource(R.string.pinned_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = MessageUtils.replyPreview(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Hide banner temporarily
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_visibility_off),
                    contentDescription = stringResource(R.string.hide),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Unpin the current message
            IconButton(onClick = { onUnpin(message) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_push_pin_off_24dp),
                    contentDescription = stringResource(R.string.unpin_message),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ConversationTopBar(
    conversation: Conversation?,
    revision: Int,
    listener: ConversationScreenListener,
    selectedCount: Int,
    // Whether the current selection has anything actually copyable — a photo/file message's
    // `body` is its upload URL, not text meant to be copied, so a selection of only photos has
    // nothing to offer here. See ShareUtil.copyToClipboard(List<Message>) for the matching filter
    // on the copy side itself.
    copyEnabled: Boolean,
    onExitSelection: () -> Unit,
    onForwardSelected: () -> Unit,
    onCopySelected: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    val context = LocalContext.current
    val isSingle = conversation?.getMode() == Conversational.MODE_SINGLE

    val availability: Presence.Availability? =
        remember(conversation, revision) {
            if (conversation != null && isSingle) {
                try {
                    conversation.getContact().shownStatus
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    // "Away"/"Extended away" are gendered past-tense verbs in Russian ("Отошёл"/"Отошла") —
    // resolve the same way the chat list does (ConversationList.kt) so this bar agrees too.
    val isFeminine: Boolean =
        remember(conversation, revision) {
            if (conversation != null && isSingle) {
                try {
                    UIHelper.resolveGender(context, conversation.getContact()) ==
                        eu.siacs.conversations.utils.NameGenderGuesser.Gender.FEMININE
                } catch (_: Exception) {
                    false
                }
            } else false
        }
    // Same "am I mid-call with this specific contact" check the chat list uses for the
    // soft-burst avatar shape (ConversationList.kt) — last-seen has no place in the subtitle
    // while a call is actually happening.
    val hasOngoingCall: Boolean =
        remember(conversation, revision) {
            if (conversation != null && isSingle) {
                try {
                    conversation.getAccount()
                        .xmppConnection
                        .getManager(JingleManager::class.java)
                        .getOngoingRtpConnection(conversation.getContact())
                        .isPresent
                } catch (_: Exception) {
                    false
                }
            } else false
        }
    // Same reciprocity rule as the legacy screen (ConversationFragment.mShowLastUserInteraction):
    // showing someone else's last-seen time is gated on whether you broadcast your own — if you
    // don't share yours, the app doesn't show others' to you either.
    val lastUserInteraction: im.conversations.android.xmpp.model.idle.LastUserInteraction? =
        remember(conversation, revision, hasOngoingCall) {
            if (conversation != null &&
                isSingle &&
                !hasOngoingCall &&
                AppSettings(context).isBroadcastLastActivity
            ) {
                try {
                    conversation.getContact().lastUserInteraction
                } catch (_: Exception) {
                    null
                }
            } else null
        }
    val isTyping: Boolean =
        remember(conversation, revision) {
            if (conversation != null && isSingle) {
                try {
                    val s =
                        conversation.getAccount()
                            .xmppConnection
                            ?.getManager(ChatStateManager::class.java)
                            ?.getIncoming(conversation.getAddress())
                    s == Composing::class.java
                } catch (_: Exception) {
                    false
                }
            } else false
        }

    val avatarState = remember(conversation?.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
    val avatarSizePx = with(LocalDensity.current) { 40.dp.toPx() }.toInt()
    LaunchedEffect(conversation, revision) {
        val activity = context as? XmppActivity ?: return@LaunchedEffect
        val c = conversation ?: return@LaunchedEffect
        val bm =
            withContext(Dispatchers.IO) {
                try {
                    activity.avatarService().get(c, avatarSizePx, false)
                } catch (_: Exception) {
                    null
                }
            }
        if (bm != null) avatarState.value = bm.asImageBitmap()
    }

    var menuOpen by remember { mutableStateOf(false) }

    val inSelectionMode = selectedCount > 0
    TopAppBar(
        navigationIcon = {
            // Crossfade + scale rather than a shared-path morph (there's no natural arrow→X path
            // morph the way the FAB's plus→cross rotation trick works) — still reads as one
            // continuous transition rather than an abrupt swap.
            androidx.compose.animation.AnimatedContent(
                targetState = inSelectionMode,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(initialScale = 0.7f, animationSpec = tween(180))) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f, animationSpec = tween(120)))
                },
                label = "conversationTopBarNavIcon",
            ) { selecting ->
                IconButton(onClick = if (selecting) onExitSelection else listener::onBackPressed) {
                    Icon(
                        painter = painterResource(
                            if (selecting) R.drawable.ic_close_24dp else R.drawable.ic_arrow_back_24dp
                        ),
                        contentDescription = stringResource(
                            if (selecting) R.string.close_selection else R.string.back
                        ),
                    )
                }
            }
        },
        title = if (inSelectionMode) {
            {
                Text(
                    text = stringResource(R.string.messages_selected_count, selectedCount),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        } else {
            {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { listener.onOpenDetails() }
                        .padding(vertical = 2.dp, horizontal = 2.dp),
            ) {
                val avatar = avatarState.value
                if (avatar != null) {
                    Image(
                        bitmap = avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier.size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                Spacer(Modifier.width(12.dp))
                // weight(1f) claims all remaining width in the row (up to wherever TopAppBar
                // starts reserving space for the actions/call button) — without this the column
                // only sizes to its own content, leaving the subtitle way less room than is
                // actually available.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation?.getName()?.toString() ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Only the Idle variant carries genuine "last seen at time X" data — Online
                    // just means "no XEP-0319 Idle element on this presence".
                    val idleInteraction =
                        lastUserInteraction
                            as? im.conversations.android.xmpp.model.idle.LastUserInteraction.Idle
                    // For Away/Extended away specifically, the label is redundant once we have a
                    // real timing to show instead — "last seen just now" already says he just
                    // went away, so showing "Away · last seen just now" is noise. Fall back to
                    // the plain label only when no timing is available (broadcast disabled on
                    // either side). Other statuses (Online, DND) always keep their label — the
                    // timing is supplementary there, not a replacement.
                    val statusLabel: String? =
                        when {
                            isTyping -> stringResource(R.string.typing_indicator)
                            availability == Presence.Availability.CHAT ||
                                availability == Presence.Availability.ONLINE ->
                                stringResource(R.string.presence_online)
                            availability == Presence.Availability.AWAY ->
                                if (idleInteraction != null) null
                                else stringResource(
                                    if (isFeminine) R.string.presence_away_feminine
                                    else R.string.presence_away
                                )
                            availability == Presence.Availability.XA ->
                                if (idleInteraction != null) null
                                else stringResource(
                                    if (isFeminine) R.string.presence_xa_feminine
                                    else R.string.presence_xa
                                )
                            availability == Presence.Availability.DND ->
                                stringResource(R.string.presence_dnd)
                            else -> null
                        }
                    val lastSeenText: String? =
                        if (!isTyping &&
                            availability != Presence.Availability.CHAT &&
                            availability != Presence.Availability.ONLINE &&
                            idleInteraction != null
                        ) {
                            UIHelper.lastUserInteraction(
                                context,
                                idleInteraction,
                                conversation?.let { runCatching { it.getContact() }.getOrNull() },
                            )
                        } else null
                    val subtitle: String? =
                        when {
                            statusLabel != null && lastSeenText != null ->
                                "$statusLabel · $lastSeenText"
                            statusLabel != null -> statusLabel
                            else -> lastSeenText
                        }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (isTyping) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.squeezeToFit(),
                        )
                    }
                }
            }
            }
        },
        actions = {
            if (inSelectionMode) {
                var selectionOverflowOpen by remember { mutableStateOf(false) }
                IconButton(onClick = onForwardSelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_forward_24dp),
                        contentDescription = stringResource(R.string.forward_message),
                    )
                }
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(R.string.delete_message),
                    )
                }
                // Copy is currently the overflow menu's only item — nothing copyable in the
                // selection (e.g. only photos, whose "body" is an upload URL, not text) means
                // there's nothing this button would open, so it's hidden entirely rather than
                // opening onto an empty menu.
                if (copyEnabled) {
                    IconButton(onClick = { selectionOverflowOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_more_horiz_24dp),
                            contentDescription = stringResource(R.string.more_options),
                        )
                    }
                    ExpressiveDropdownMenu(
                        expanded = selectionOverflowOpen,
                        onDismissRequest = { selectionOverflowOpen = false },
                    ) {
                        ExpressiveMenuItem(R.drawable.ic_check_24dp, stringResource(android.R.string.copy)) {
                            selectionOverflowOpen = false
                            onCopySelected()
                        }
                    }
                }
            } else {
            if (isSingle) {
                IconButton(onClick = { listener.onCall(false) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_call_24dp),
                        contentDescription = stringResource(R.string.audio_call),
                    )
                }
                IconButton(onClick = { listener.onCall(true) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_videocam_24dp),
                        contentDescription = stringResource(R.string.video_call),
                    )
                }
            }
            // Menu visibility state, refreshed with every conversation update.
            val isMuted =
                remember(conversation, revision) {
                    try {
                        conversation?.isMuted ?: false
                    } catch (_: Exception) {
                        false
                    }
                }
            val isPinned =
                remember(conversation, revision) {
                    conversation?.getBooleanAttribute(
                        Conversation.ATTRIBUTE_PINNED_ON_TOP,
                        false,
                    ) ?: false
                }
            val canInvite =
                remember(conversation, revision) {
                    try {
                        if (conversation == null) false
                        else if (isSingle)
                            !conversation
                                .getAccount()
                                .xmppConnection
                                .getManager(
                                    eu.siacs.conversations.xmpp.manager.MultiUserChatManager::class
                                        .java
                                )
                                .services
                                .isEmpty()
                        else conversation.mucOptions.canInvite()
                    } catch (_: Exception) {
                        false
                    }
                }
            val showEncryption =
                remember(conversation, revision) {
                    try {
                        conversation != null &&
                            !eu.siacs.conversations.crypto.OmemoSetting.isAlways() &&
                            (isSingle || conversation.mucOptions.participating())
                    } catch (_: Exception) {
                        false
                    }
                }
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz_24dp),
                    contentDescription = stringResource(R.string.more_options),
                )
            }
            ExpressiveDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val dismissThen: (() -> Unit) -> () -> Unit = { action ->
                    {
                        menuOpen = false
                        action()
                    }
                }
                ExpressiveMenuItem(
                    R.drawable.ic_search_24dp,
                    stringResource(R.string.search_messages),
                    dismissThen(listener::onSearchMessages),
                )
                if (canInvite) {
                    ExpressiveMenuItem(
                        R.drawable.ic_person_add_24dp,
                        stringResource(
                            if (isSingle) R.string.start_group_chat else R.string.invite_contact
                        ),
                        dismissThen(listener::onInviteContact),
                    )
                }
                if (showEncryption) {
                    ExpressiveMenuItem(
                        R.drawable.ic_lock_24dp,
                        stringResource(R.string.choose_encryption),
                        dismissThen(listener::onChooseEncryption),
                    )
                }
                if (isMuted) {
                    ExpressiveMenuItem(
                        R.drawable.ic_notifications_24dp,
                        stringResource(R.string.enable_notifications),
                        dismissThen(listener::onUnmuteConversation),
                    )
                } else {
                    ExpressiveMenuItem(
                        R.drawable.ic_notifications_off_24dp,
                        stringResource(R.string.disable_notifications),
                        dismissThen(listener::onMuteConversation),
                    )
                }
                ExpressiveMenuItem(
                    R.drawable.ic_star_24dp,
                    stringResource(
                        if (isPinned) R.string.remove_from_favorites
                        else R.string.add_to_favorites
                    ),
                    dismissThen(listener::onTogglePinned),
                )
                ExpressiveMenuItem(
                    R.drawable.ic_delete_24dp,
                    stringResource(R.string.action_clear_history),
                    dismissThen(listener::onClearHistory),
                )
                if (isSingle) {
                    ExpressiveMenuItem(
                        R.drawable.ic_cancel_24dp,
                        stringResource(R.string.action_block_contact),
                        dismissThen(listener::onBlockContact),
                    )
                }
                ExpressiveMenuItem(
                    R.drawable.ic_archive_24dp,
                    stringResource(R.string.action_archive_chat),
                    dismissThen(listener::onArchiveConversation),
                )
            }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
        // fitsSystemWindows on the host FrameLayout already consumed the status bar inset.
        // TopAppBar adds it again by default — zero it out to prevent the double-height gap.
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    )
}

// Bridges a Guava ListenableFuture (used throughout the XMPP manager layer, incl.
// EntityTimeManager) into a suspend call — null on failure/cancellation rather than throwing, so
// callers can treat "couldn't get it" the same as "don't have it".
private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.awaitOrNull(): T? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        com.google.common.util.concurrent.Futures.addCallback(
            this,
            object : com.google.common.util.concurrent.FutureCallback<T> {
                override fun onSuccess(result: T) {
                    if (cont.isActive) cont.resume(result) {}
                }

                override fun onFailure(t: Throwable) {
                    if (cont.isActive) cont.resume(null) {}
                }
            },
            com.google.common.util.concurrent.MoreExecutors.directExecutor(),
        )
        cont.invokeOnCancellation { this@awaitOrNull.cancel(false) }
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageList(
    state: ConversationScreenState,
    listener: ConversationScreenListener,
    onLongPress: (Message) -> Unit,
    // The specific cell that was long-pressed, plus the whole group it belongs to — the sheet
    // tailors Reply/Open/Share/Forward to that one message, while Reaction/Pin/Delete still act
    // on the whole group.
    onLongPressGroupCell: (Message, List<Message>) -> Unit,
    onOpenSelector: (List<Message>) -> Unit,
    selectedUuids: List<String>,
    onToggleSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision = state.revision.intValue
    // Compute items in composable scope, not in derivedStateOf. derivedStateOf suppresses
    // recomposition when the result is structurally equal — same Message instances produce equal
    // ChatItem.Msg wrappers, so in-place status changes (sent→delivered→read) would be invisible.
    // Reading `revision` here makes MessageList recompose on every refresh(), which rebuilds items
    // and passes the new `revision` into every visible MessageRow so footers re-render.
    @Suppress("UNUSED_EXPRESSION") revision
    val conversation = state.conversation.value
    // Frozen once per conversation-open, not recomputed live as progressive read-marking (see
    // the scroll-driven read-marker effect below) shrinks the real unread count — the pill marks
    // "where you left off when you opened this", not a live counter that would shrink/flicker
    // under it while it's still on screen.
    val newMessagesBoundary =
        remember(conversation?.getUuid()) {
            val first =
                try {
                    conversation?.getFirstUnreadMessage()
                } catch (_: Exception) {
                    null
                }
            if (first != null) first.getUuid() to (conversation?.unreadCount() ?: 0) else null
        }
    val items =
        buildChatItems(state.messages.value, newMessagesBoundary?.first, newMessagesBoundary?.second ?: 0)
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val isTyping: Boolean =
        remember(conversation, revision) {
            if (conversation != null && conversation.getMode() == Conversational.MODE_SINGLE) {
                try {
                    val s =
                        conversation
                            .getAccount()
                            .xmppConnection
                            ?.getManager(ChatStateManager::class.java)
                            ?.getIncoming(conversation.getAddress())
                    s == Composing::class.java
                } catch (_: Exception) {
                    false
                }
            } else false
        }

    // EntityTime (XEP-0202) "it's late for them" indicator, ported from the old screen — shown
    // as a plain row at the bottom of the list (same slot as TypingBubble below), not its own
    // chat bubble. Independent of DND: DND already shows in the top bar subtitle, so it no
    // longer needs its own separate status message here like the old screen did. Gated on: 1:1
    // chat, not currently typing (typing takes priority and hides this), no message received in
    // the last 12 minutes (goes quiet once a conversation is actively happening), a successfully
    // resolved contact-side time, that time being in a different zone than this device's own,
    // and it being "night" for them.
    val quietLongEnough: Boolean =
        remember(conversation, revision) {
            conversation != null && EntityTimeManager.noRecentMessages(conversation)
        }
    val entityTime =
        remember(conversation?.getUuid()) { mutableStateOf<java.time.ZonedDateTime?>(null) }
    LaunchedEffect(conversation?.getUuid(), isTyping, quietLongEnough) {
        val c = conversation
        if (c == null || c.getMode() != Conversational.MODE_SINGLE || isTyping || !quietLongEnough) {
            return@LaunchedEffect
        }
        val connection = try {
            c.getAccount().xmppConnection
        } catch (_: Exception) {
            null
        } ?: return@LaunchedEffect
        val future =
            try {
                connection.getManager(EntityTimeManager::class.java).getZonedDateTime(c.getAddress())
            } catch (_: Exception) {
                null
            } ?: return@LaunchedEffect
        entityTime.value = future.awaitOrNull()
    }
    val localTimeForContact: java.time.ZonedDateTime? =
        entityTime.value?.takeIf {
            isTyping.not() &&
                quietLongEnough &&
                conversation?.getMode() == Conversational.MODE_SINGLE &&
                EntityTimeManager.isDifferentTimeZone(it) &&
                EntityTimeManager.isNightTime(it)
        }

    // Request older messages when the user approaches the (chronological) top.
    LaunchedEffect(listState, revision) {
        snapshotFlow {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= info.totalItemsCount - 4 && info.totalItemsCount > 0
            }
            .distinctUntilChanged()
            .collect { nearTop -> if (nearTop) listener.onLoadMoreMessages() }
    }

    // Notify when the newest message becomes visible so read markers can be sent.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }
            .distinctUntilChanged()
            .collect { atBottom -> if (atBottom) listener.onScrolledToBottom() }
    }

    // Progressive read-marking: mark read only what has actually been on screen (present in
    // visibleItemsInfo — genuinely rendered, not a recycled/off-screen slot), as it happens,
    // instead of blanket-marking the whole unread backlog the instant the conversation opens.
    // markedThroughIndex is a monotonic ratchet (lower index = newer, reverseLayout) so scrolling
    // back up never "unmarks" anything already confirmed seen.
    var markedThroughIndex by remember(conversation?.getUuid()) { mutableIntStateOf(Int.MAX_VALUE) }
    // items is a plain val, rebuilt fresh on every recomposition (see the comment above its
    // declaration — deliberately not remembered, so it reflects new messages as they arrive).
    // LaunchedEffect(conversation?.getUuid()) below only restarts on a conversation switch, so
    // without this, its coroutine would close over whatever `items` existed at the moment the
    // conversation was opened and never see anything that arrived afterward — the effect would
    // keep computing "lowest visible unread" against a permanently stale list, which is exactly
    // why marking only ever seemed to happen via the separate onScrolledToBottom() path (that one
    // never touches `items` at all) and never dynamically as new messages scrolled into view.
    val latestItems = rememberUpdatedState(items)
    // The typing bubble and "it's late for them" row are declared as their own item(key = ...)
    // entries ahead of itemsIndexed(items, ...) below, so whenever either is showing,
    // visibleItemsInfo's raw .index runs ahead of items' own indices by however many of those
    // leading rows are present right now — reading items[idx] directly was silently grabbing the
    // wrong chat item (or nothing) exactly when one of those rows happened to be up, which is
    // why marking looked like a coin flip rather than reliably working or reliably not.
    // rememberUpdatedState (not a plain val) for the same reason items itself needs it above:
    // this LaunchedEffect only restarts on a conversation switch, so isTyping/localTimeForContact
    // — which can flip independently of that — would otherwise be frozen at whatever they were
    // when the effect launched.
    val latestLeadingCount = rememberUpdatedState(
        (if (isTyping) 1 else 0) + (if (localTimeForContact != null) 1 else 0)
    )
    LaunchedEffect(conversation?.getUuid()) {
        snapshotFlow {
            val leadingCount = latestLeadingCount.value
            listState.layoutInfo.visibleItemsInfo.map { it.index - leadingCount }
        }
            .collect { visibleIndices ->
                val currentItems = latestItems.value
                val lowestVisibleUnread =
                    visibleIndices
                        .filter { idx ->
                            idx >= 0 &&
                                (currentItems.getOrNull(idx) as? ChatItem.Msg)?.message?.let {
                                    it.status == Message.STATUS_RECEIVED && !it.isRead()
                                } == true
                        }
                        .minOrNull()
                if (lowestVisibleUnread != null && lowestVisibleUnread < markedThroughIndex) {
                    val uuid = (currentItems[lowestVisibleUnread] as ChatItem.Msg).message.getUuid()
                    if (uuid != null) {
                        markedThroughIndex = lowestVisibleUnread
                        listener.onMarkReadUpTo(uuid)
                    }
                }
            }
    }

    // Single scroll executor: every scroll request — pin-to-bottom or audio auto-advance —
    // goes through this one LaunchedEffect, keyed on an incrementing nonce. A brand new request
    // always cancels whichever scroll was previously in flight (LaunchedEffect's own key-change
    // cancellation), instead of two independent coroutines each calling animateScrollToItem and
    // fighting each other mid-animation, which is what caused the erratic multi-scroll/snap-back
    // behavior. offsetFraction=0f anchors the item at the bottom; e.g. 0.70f lands it upper-center.
    var scrollRequest by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    var scrollNonce by remember { mutableIntStateOf(0) }
    fun requestScroll(index: Int, offsetFraction: Float) {
        scrollRequest = index to offsetFraction
        scrollNonce++
    }
    LaunchedEffect(scrollNonce) {
        val (index, offsetFraction) = scrollRequest ?: return@LaunchedEffect
        // LazyListState.scrollToItem's own KDoc (androidx.compose.foundation.lazy.LazyListState):
        // "positive offset refers to forward scroll, so in a top-to-bottom list, positive offset
        // will scroll the item further upward, taking it partly offscreen." reverseLayout=true
        // mirrors BOTH the layout order AND the scroll direction (see LazyColumn's own KDoc:
        // "reverse the direction of scrolling and layout"), so the mirrored equivalent here is:
        // positive offset pushes the item further off-screen at the BOTTOM (the viewport
        // "start" edge for a reversed list), not up. That is backwards from what we want, and
        // was previously landing auto-advanced messages below the visible screen instead of
        // upper-center. A NEGATIVE offset is what moves the item away from the start edge and
        // higher up the screen.
        val viewportHeight = listState.layoutInfo.viewportSize.height
        val offsetPx =
            if (offsetFraction <= 0f) 0
            else if (viewportHeight > 0) -(viewportHeight * offsetFraction).toInt()
            else -800 // fallback if layout not yet measured
        listState.animateScrollToItem(index, scrollOffset = offsetPx)
        if (index == 0) listener.onScrolledToBottom()
    }

    // On first opening a conversation with unread messages, land on the first unread one
    // (upper-third position, same offsetFraction used for auto-advance) instead of always
    // jumping straight to the bottom and silently skipping past everything unseen. Only kicks
    // in when the unread run doesn't already fit on screen together with the newest message —
    // if it fits, the natural bottom-start position already shows all of it, nothing to correct.
    // hasPositioned gates the "keep pinned to bottom" effect below so it can't fire a competing
    // scroll-to-bottom while this is still deciding; it hands off once done, either way.
    val hasPositioned = remember(conversation?.getUuid()) { mutableStateOf(false) }
    LaunchedEffect(conversation?.getUuid()) {
        val firstUnreadUuid = newMessagesBoundary?.first
        if (firstUnreadUuid != null) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            val targetIndex =
                items.indexOfFirst {
                    it is ChatItem.Msg && it.message.getUuid() == firstUnreadUuid
                }
            if (targetIndex > 0) {
                val info = listState.layoutInfo
                val target = info.visibleItemsInfo.find { it.index == targetIndex }
                val fullyVisible =
                    target != null &&
                        target.offset >= info.viewportStartOffset &&
                        (target.offset + target.size) <= info.viewportEndOffset
                if (!fullyVisible) {
                    requestScroll(targetIndex, 0.7f)
                }
            }
        }
        hasPositioned.value = true
    }

    // Keep pinned to the bottom when a new message arrives, or the typing indicator
    // appears/disappears, while we are (nearly) there. The typing bubble is its own list item
    // (added/removed above), which shifts every real message's index by one — without `isTyping`
    // as a key here, that shift never re-triggers this check, so the indicator can slide in
    // below the visible fold and just sit there unseen until something else happens to scroll.
    val newestKey = items.firstOrNull()?.key
    LaunchedEffect(newestKey, isTyping) {
        // A message that's also the first of a new day inserts *two* leading items — itself and
        // a fresh DatePill right after it — not just one, so the "still near the bottom"
        // tolerance needs to widen by one in that case. Without this, auto-scroll only ever
        // worked for a same-day arrival: the previous newest message's index shifts to 2, not 1,
        // and silently failed the plain `<= 1` check every time a message crossed midnight.
        val extraForDatePill = if (items.getOrNull(1) is ChatItem.DatePill) 1 else 0
        if (hasPositioned.value && listState.firstVisibleItemIndex <= 1 + extraForDatePill) {
            requestScroll(0, 0f)
        }
    }

    // Auto-advance to the next audio message when one finishes playing naturally. Keyed on
    // `revision` (not Unit) so the closure is re-registered with a fresh `items` every time the
    // list actually changes — `items` is a plain val recomputed per recomposition, so a
    // Unit-keyed effect would only ever capture the very first list and silently go stale for
    // any message that arrived after the screen first composed.
    val context = LocalContext.current
    // Holds the currently in-flight "start next playback" sequence, if any. On short messages,
    // a new completion can fire before the previous sequence's 300ms delay has elapsed —
    // cancelling the old one before starting a new one avoids a stale play() call for a message
    // that isn't the current target anymore.
    var autoAdvanceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    androidx.compose.runtime.DisposableEffect(revision) {
        AudioPlaybackController.onCompletion = onCompletion@{ completedUuid ->
            val completedIdx = items.indexOfFirst { it is ChatItem.Msg && it.message.getUuid() == completedUuid }
            if (completedIdx < 0) return@onCompletion
            // reverseLayout=true: index 0 is the bottom (newest). Lower index = newer = below.
            val next = items.take(completedIdx)
                .filterIsInstance<ChatItem.Msg>()
                .lastOrNull { it.message.mimeType?.startsWith("audio/") == true }
            if (next != null) {
                val activity = context as? eu.siacs.conversations.ui.XmppActivity
                val file = try { activity?.xmppConnectionService?.fileBackend?.getFile(next.message) } catch (_: Exception) { null }
                if (file != null && file.exists()) {
                    val nextIdx = items.indexOf(next)
                    autoAdvanceJob?.cancel()
                    autoAdvanceJob = scope.launch {
                        kotlinx.coroutines.delay(300)
                        (context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager)
                            .playSoundEffect(android.media.AudioManager.FX_KEY_CLICK)
                        AudioPlaybackController.play(next.message.getUuid()!!, file)
                        if (nextIdx >= 0) {
                            // Upper-center, not bottom — goes through the single scroll executor
                            // above so it can't race the pin-to-bottom effect.
                            requestScroll(nextIdx, 0.70f)
                        }
                    }
                }
            }
        }
        onDispose { AudioPlaybackController.onCompletion = null }
    }

    var highlightKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(highlightKey) {
        if (highlightKey != null) {
            kotlinx.coroutines.delay(1500)
            highlightKey = null
        }
    }

    // Scroll to a specific message when requested (e.g. from pinned banner tap, or "Show in
    // chat" in the media viewer). Must also match ChatItem.MediaGroup — a message that's part of
    // a grid tile is never a standalone ChatItem.Msg, so a Msg-only lookup silently finds nothing
    // for the common case of a grouped photo.
    val scrollToUuid = state.requestScrollToUuid.value
    LaunchedEffect(scrollToUuid) {
        if (scrollToUuid != null) {
            val idx = items.indexOfFirst { item ->
                when (item) {
                    is ChatItem.Msg -> item.message.getUuid() == scrollToUuid
                    is ChatItem.MediaGroup -> item.messages.any { it.getUuid() == scrollToUuid }
                    else -> false
                }
            }
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
                highlightKey = scrollToUuid
            }
            state.requestScrollToUuid.value = null
        }
    }

    // Resolves the message a reply refers to, by stanza id or uuid.
    val resolveReply: (String) -> Message? =
        remember(revision) {
            { id ->
                state.messages.value.lastOrNull { m ->
                    id == m.serverMsgId || id == m.getUuid() || id == m.remoteMsgId
                }
            }
        }

    val onReplyCardClick: (Message) -> Unit = { original ->
        val key = original.getUuid() ?: ""
        val index = items.indexOfFirst { it.key == key }
        if (index >= 0) {
            highlightKey = key
            scope.launch { listState.animateScrollToItem(index) }
        }
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            if (isTyping) {
                item(key = "typing-indicator") { TypingBubble(modifier = Modifier.animateItem()) }
            }
            val localTime = localTimeForContact
            if (localTime != null && conversation != null) {
                item(key = "local-time-indicator") {
                    LocalTimeForContactRow(
                        zonedDateTime = localTime,
                        contactName = conversation.getName()?.toString() ?: "",
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                // All three animateItem specs are null to prevent intermittent blank bubbles.
                //
                // fadeInSpec/fadeOutSpec = null: animateItem() triggers enter-fade whenever a slot
                // re-enters the composition window (scroll back after leaving lookahead range).
                // That renders items at alpha≈0 for ~300 ms — visually blank.
                //
                // placementSpec = null: the default placement spring animates an item from an
                // off-screen offset to its final position over several frames. For items that
                // contain an AndroidView (LinkifiedMessageText), the embedded TextView is created
                // fresh by the key()-wrapped factory during those same frames but hasn't been
                // through Android's measure/layout pass yet — its RenderNode has no valid
                // dimensions. Whether the RenderThread draws before or after the View layout pass
                // completes is a VSYNC race: the same message can appear blank on one scroll and
                // fully visible on the next. Removing the placement spring eliminates the race
                // window entirely. New messages still appear naturally at the bottom because the
                // LaunchedEffect(newestKey) pins the list there; placement animation is not needed
                // for the normal bottom-pinned conversation flow.
                val itemModifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = null,
                    fadeOutSpec = null,
                )
                when (item) {
                    is ChatItem.DatePill ->
                        DatePill(timestamp = item.timestamp, modifier = itemModifier)
                    is ChatItem.NewMessagesPill ->
                        NewMessagesPill(count = item.count, modifier = itemModifier)
                    is ChatItem.Msg ->
                        MessageRow(
                            item = item,
                            isNewest = index == 0,
                            highlighted = item.key == highlightKey,
                            isBeingEdited = state.correcting.value?.getUuid() == item.message.getUuid()
                                    || state.remoteEditingIds.value.contains(item.message.getUuid()),
                            revision = revision,
                            listener = listener,
                            onLongPress = onLongPress,
                            resolveReply = resolveReply,
                            onReplyCardClick = onReplyCardClick,
                            selectionActive = selectedUuids.isNotEmpty(),
                            selected = selectedUuids.contains(item.message.getUuid()),
                            onToggleSelected = {
                                item.message.getUuid()?.let { uuid -> onToggleSelected(uuid) }
                            },
                            modifier = itemModifier,
                        )
                    is ChatItem.MediaGroup -> {
                        val groupUuids = item.messages.mapNotNull { it.getUuid() }
                        MediaGroupRow(
                            item = item,
                            revision = revision,
                            highlighted = highlightKey != null && groupUuids.contains(highlightKey),
                            listener = listener,
                            onLongPressCell = { tapped, messages -> onLongPressGroupCell(tapped, messages) },
                            onOpenSelector = onOpenSelector,
                            selectionActive = selectedUuids.isNotEmpty(),
                            selectedUuids = selectedUuids,
                            selected = groupUuids.isNotEmpty() && groupUuids.all { selectedUuids.contains(it) },
                            onToggleSelected = {
                                val allSelected = groupUuids.all { selectedUuids.contains(it) }
                                groupUuids.forEach { uuid ->
                                    val isSelected = selectedUuids.contains(uuid)
                                    if (allSelected == isSelected) onToggleSelected(uuid)
                                }
                            },
                            onToggleSingle = onToggleSelected,
                            modifier = itemModifier,
                        )
                    }
                }
            }
        }

        val showScrollToBottom by
            remember { androidx.compose.runtime.derivedStateOf { listState.firstVisibleItemIndex > 2 } }
        AnimatedVisibility(
            visible = showScrollToBottom,
            enter = scaleIn(),
            exit = scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Box {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_double_arrow_down_24dp),
                        contentDescription = null,
                    )
                }
                val unread = state.unreadCount.intValue
                if (unread > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                    ) {
                        Text(
                            text = if (unread > 99) "99+" else unread.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingBubble(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier.fillMaxWidth().padding(start = 12.dp - TAIL_WIDTH, top = 6.dp, bottom = 1.dp)
    ) {
        Surface(
            shape =
                rememberBubbleShape(firstOfGroup = true, lastOfGroup = true, outgoing = false),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            val transition = rememberInfiniteTransition(label = "typing")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier.padding(
                        start = 16.dp + TAIL_WIDTH,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
            ) {
                repeat(3) { index ->
                    val alpha by
                        transition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 1f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation =
                                        tween(
                                            durationMillis = 600,
                                            delayMillis = index * 200,
                                            easing = LinearEasing,
                                        ),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "dot$index",
                        )
                    Box(
                        modifier =
                            Modifier.padding(horizontal = 2.dp)
                                .size(7.dp)
                                .graphicsLayer { this.alpha = alpha }
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                    CircleShape,
                                )
                    )
                }
            }
        }
    }
}

// Deliberately plain — no Surface/bubble shape, unlike DatePill/NewMessagesPill/TypingBubble.
// Just an icon and a line of text, same visual weight as a subtitle rather than a message.
@Composable
private fun LocalTimeForContactRow(
    zonedDateTime: java.time.ZonedDateTime,
    contactName: String,
    modifier: Modifier = Modifier,
) {
    val timeText =
        remember(zonedDateTime) {
            zonedDateTime.toLocalTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString()
        }
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_schedule_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.its_time_for_contact_compose, timeText, contactName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun formatDatePill(context: android.content.Context, timestamp: Long): String {
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().also { it.timeInMillis = timestamp }
    val todayStart = now.clone() as java.util.Calendar
    todayStart.set(java.util.Calendar.HOUR_OF_DAY, 0)
    todayStart.set(java.util.Calendar.MINUTE, 0)
    todayStart.set(java.util.Calendar.SECOND, 0)
    todayStart.set(java.util.Calendar.MILLISECOND, 0)
    val sevenDaysAgoStart = (todayStart.clone() as java.util.Calendar).also { it.add(java.util.Calendar.DAY_OF_YEAR, -6) }
    return when {
        timestamp >= todayStart.timeInMillis ->
            context.getString(R.string.today)
        timestamp >= todayStart.timeInMillis - DateUtils.DAY_IN_MILLIS ->
            context.getString(R.string.yesterday)
        timestamp >= sevenDaysAgoStart.timeInMillis ->
            // Within the last 7 days — show weekday name
            DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_WEEKDAY)
        then.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) ->
            // Same year — full date without year
            DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)
        else ->
            // Different year — full date with year
            DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
    }
}

@Composable
private fun DatePill(timestamp: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val label = formatDatePill(context, timestamp)
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

// Same visual language as DatePill (same shape/color/text style) — this just marks where the
// unread run started instead of a date boundary, and unlike DatePill it doesn't stay forever:
// it fades out on its own after a few seconds once the user has had a chance to notice it.
@Composable
private fun NewMessagesPill(count: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(5000)
        visible = false
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = context.resources.getQuantityString(R.plurals.new_messages_pill, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private val CORNER_LARGE: Dp = 20.dp
private val CORNER_SMALL: Dp = 5.dp
private val TAIL_WIDTH: Dp = 8.dp
private val TAIL_HEIGHT: Dp = 14.dp

/**
 * Bubble shape. Middle-of-group bubbles are plain rounded rects; the last bubble of a group
 * carries a small curled tail at its bottom corner on the sender's side. The tail occupies
 * [TAIL_WIDTH] inside the layout bounds, so callers compensate row padding and content padding
 * on that side to keep bubble bodies aligned within the group.
 */
@Composable
private fun rememberBubbleShape(
    firstOfGroup: Boolean,
    lastOfGroup: Boolean,
    outgoing: Boolean,
): androidx.compose.ui.graphics.Shape {
    val density = LocalDensity.current
    return remember(firstOfGroup, lastOfGroup, outgoing, density) {
        val top = if (firstOfGroup) CORNER_LARGE else CORNER_SMALL
        if (!lastOfGroup) {
            if (outgoing) {
                RoundedCornerShape(
                    topStart = CORNER_LARGE,
                    topEnd = top,
                    bottomStart = CORNER_LARGE,
                    bottomEnd = CORNER_SMALL,
                )
            } else {
                RoundedCornerShape(
                    topStart = top,
                    topEnd = CORNER_LARGE,
                    bottomStart = CORNER_SMALL,
                    bottomEnd = CORNER_LARGE,
                )
            }
        } else {
            with(density) {
                bubbleTailShape(
                    outgoing = outgoing,
                    groupTopCorner = top.toPx(),
                    largeCorner = CORNER_LARGE.toPx(),
                    tailWidth = TAIL_WIDTH.toPx(),
                    tailHeight = TAIL_HEIGHT.toPx(),
                )
            }
        }
    }
}

private fun bubbleTailShape(
    outgoing: Boolean,
    groupTopCorner: Float,
    largeCorner: Float,
    tailWidth: Float,
    tailHeight: Float,
): androidx.compose.ui.graphics.Shape =
    androidx.compose.foundation.shape.GenericShape { size, layoutDirection ->
        val rtl = layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
        val tailOnRight = outgoing != rtl
        val tipR = tailWidth * 0.5f
        val h = size.height
        // Draw the entire bubble+tail outline as one continuous clockwise path so there
        // is no subpath junction at the 0-dp corner where the tail meets the bubble body.
        if (tailOnRight) {
            val right = size.width - tailWidth
            moveTo(largeCorner, 0f)
            lineTo(right - groupTopCorner, 0f)
            arcTo(androidx.compose.ui.geometry.Rect(right - 2 * groupTopCorner, 0f, right, 2 * groupTopCorner), 270f, 90f, false)
            lineTo(right, h - tailHeight)
            cubicTo(right, h - tailHeight * 0.3f, size.width, h - tipR * 2f, size.width, h - tipR)
            arcTo(androidx.compose.ui.geometry.Rect(size.width - tipR * 2, h - tipR * 2, size.width, h), 0f, 90f, false)
            lineTo(right, h)
            lineTo(largeCorner, h)
            arcTo(androidx.compose.ui.geometry.Rect(0f, h - 2 * largeCorner, 2 * largeCorner, h), 90f, 90f, false)
            lineTo(0f, largeCorner)
            arcTo(androidx.compose.ui.geometry.Rect(0f, 0f, 2 * largeCorner, 2 * largeCorner), 180f, 90f, false)
        } else {
            val left = tailWidth
            moveTo(left + groupTopCorner, 0f)
            lineTo(size.width - largeCorner, 0f)
            arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * largeCorner, 0f, size.width, 2 * largeCorner), 270f, 90f, false)
            lineTo(size.width, h - largeCorner)
            arcTo(androidx.compose.ui.geometry.Rect(size.width - 2 * largeCorner, h - 2 * largeCorner, size.width, h), 0f, 90f, false)
            lineTo(left, h)
            lineTo(tipR, h)
            arcTo(androidx.compose.ui.geometry.Rect(0f, h - 2 * tipR, 2 * tipR, h), 90f, 90f, false)
            cubicTo(0f, h - tipR * 2f, left, h - tailHeight * 0.3f, left, h - tailHeight)
            lineTo(left, groupTopCorner)
            arcTo(androidx.compose.ui.geometry.Rect(left, 0f, left + 2 * groupTopCorner, 2 * groupTopCorner), 180f, 90f, false)
        }
        close()
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageRow(
    item: ChatItem.Msg,
    isNewest: Boolean,
    highlighted: Boolean,
    isBeingEdited: Boolean,
    // Message objects mutate internally (status, transfer progress); passing the revision
    // counter down defeats Compose skipping so bubbles re-render on every conversation update.
    revision: Int,
    listener: ConversationScreenListener,
    onLongPress: (Message) -> Unit,
    resolveReply: (String) -> Message?,
    onReplyCardClick: (Message) -> Unit,
    selectionActive: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = item.message
    val outgoing = message.status != Message.STATUS_RECEIVED
    val isGroupChat = message.getConversation().getMode() == Conversational.MODE_MULTI
    val showAvatarSlot = !outgoing && isGroupChat

    // Expressive "pop": the newest message springs in.
    val pop = remember(item.key) { Animatable(if (isNewest) 0.8f else 1f) }
    if (isNewest) {
        LaunchedEffect(item.key) {
            pop.animateTo(
                1f,
                animationSpec =
                    androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                    ),
            )
        }
    }

    // Avatar for incoming group-chat messages: load once per sender group (last bubble).
    val context = LocalContext.current
    val avatarBitmap = if (showAvatarSlot && item.lastOfGroup) {
        val avatarState = remember(item.key) { mutableStateOf<ImageBitmap?>(null) }
        val avatarSizePx = with(LocalDensity.current) { 32.dp.toPx() }.toInt()
        LaunchedEffect(item.key) {
            val activity = context as? XmppActivity ?: return@LaunchedEffect
            val bm = withContext(Dispatchers.IO) {
                try { activity.avatarService().get(message, avatarSizePx, false) }
                catch (_: Exception) { null }
            }
            if (bm != null) avatarState.value = bm.asImageBitmap()
        }
        avatarState
    } else null

    // The tail of a group's last bubble pokes into the screen margin so bubble bodies stay
    // aligned with the grouped bubbles above.
    val tailInset = if (item.lastOfGroup) TAIL_WIDTH else 0.dp
    Box(modifier = modifier.fillMaxWidth()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                onClick = { if (selectionActive) onToggleSelected() },
                onLongClick = { if (selectionActive) onToggleSelected() else onLongPress(message) },
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (outgoing) 48.dp else if (showAvatarSlot) 8.dp else 12.dp - tailInset,
                        end = if (outgoing) 12.dp - tailInset else 48.dp,
                        top = if (item.firstOfGroup) 6.dp else 1.dp,
                        bottom = 1.dp,
                    )
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        transformOrigin =
                            androidx.compose.ui.graphics.TransformOrigin(
                                if (outgoing) 1f else 0f,
                                1f,
                            )
                    },
            horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        ) {
            AnimatedVisibility(
                visible = selectionActive,
                enter = fadeIn(tween(180)) + androidx.compose.animation.expandHorizontally(tween(180)),
                exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkHorizontally(tween(120)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SelectionCheckCircle(selected = selected)
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (showAvatarSlot) {
                val bm = avatarBitmap?.value
                Box(
                    modifier = Modifier.size(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bm != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bm,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            MessageBubble(
                item = item,
                outgoing = outgoing,
                highlighted = highlighted,
                isBeingEdited = isBeingEdited,
                revision = revision,
                listener = listener,
                onLongPress = onLongPress,
                resolveReply = resolveReply,
                onReplyCardClick = onReplyCardClick,
            )
        }
        ReactionChips(
            message = message,
            outgoing = outgoing,
            revision = revision,
            listener = listener,
            modifier = Modifier
                .fillMaxWidth()
                // Outgoing is untouched — pulled up closer to the bubble (colors are distinct
                // enough — tertiaryContainer/secondaryContainer vs. the bubble's own
                // primaryContainer — that the breathing room isn't needed), plain offset(),
                // exactly as it was before this file's other reaction-chip changes. Only
                // incoming gets the layout-reclaim treatment: a plain offset() shifts where
                // something is drawn without shrinking the space it reserves in the parent
                // Column, so pulling it up into the bubble above just relocates the gap to
                // below the chip instead of removing it. Reporting a shorter measured height
                // while still placing the content at the shifted position reclaims that space.
                //
                // Incoming also needs to lift further than outgoing for a real (not just
                // magnitude) reason: MessageFooter is unconditionally right-aligned regardless
                // of direction. Outgoing's End-aligned chips land right on/next to that footer
                // content, so the overlap reads as clearly attached. Incoming's Start-aligned
                // chips overlap the bubble's bottom-left corner instead, which has no content
                // in it at all — the same geometric overlap looks emptier there because there's
                // nothing to visually anchor it to.
                .then(
                    if (outgoing) {
                        Modifier.offset(y = (-8).dp)
                    } else {
                        Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val liftPx = (-18).dp.roundToPx()
                            layout(placeable.width, (placeable.height + liftPx).coerceAtLeast(0)) {
                                placeable.placeRelative(0, liftPx)
                            }
                        }
                    }
                )
                .padding(
                    start = if (outgoing) 48.dp else if (showAvatarSlot) 8.dp + 32.dp + 6.dp else 15.dp,
                    end = if (outgoing) 10.dp else 48.dp,
                ),
        )
    }
    if (selectionActive) {
        // The row's own combinedClickable above only wins hit-testing where nothing else claims
        // the tap — images, links, reply cards, file rows etc. all have their own clickables
        // further down that would otherwise still fire their normal single-message action while
        // selecting. This transparent overlay sits on top of everything and claims every tap
        // itself instead.
        Box(
            modifier = Modifier
                .matchParentSize()
                .combinedClickable(
                    onClick = onToggleSelected,
                    onLongClick = onToggleSelected,
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                ),
        )
    }
    }
}

private val MEDIA_GRID_WIDTH: Dp = 234.dp
private val MEDIA_GRID_SINGLE_HEIGHT: Dp = 176.dp
private val MEDIA_GRID_HERO_HEIGHT: Dp = 132.dp
private const val MEDIA_GRID_MAX_CELLS = 4

/**
 * Picks which single message in a grid tile's batch should drive the tile's shared status icon —
 * the "weakest link", so the tile reads as one batch instead of just echoing whatever message
 * happens to occupy a fixed position in the group:
 * - Any genuine failure (not a user cancel) wins outright — the tile shows failed even if every
 *   other photo in it went through fine.
 * - Otherwise, anything still in flight (waiting/uploading/offered) keeps the whole tile showing
 *   "uploading" — one photo finishing first doesn't make the batch look done.
 * - Only once every message has actually sent does the tile show delivered/read, and even then
 *   only once *every* message has reached that level (the least-progressed one still gates it).
 */
private fun groupStatusRepresentative(messages: List<Message>): Message {
    val failed = messages.firstOrNull {
        it.status == Message.STATUS_SEND_FAILED && it.errorMessage != Message.ERROR_MESSAGE_CANCELLED
    }
    if (failed != null) return failed
    val inFlight = messages.firstOrNull {
        it.status == Message.STATUS_UNSEND ||
            it.status == Message.STATUS_WAITING ||
            it.status == Message.STATUS_OFFERED
    }
    if (inFlight != null) return inFlight
    val statusRank = { status: Int ->
        when (status) {
            Message.STATUS_SEND -> 0
            Message.STATUS_SEND_RECEIVED -> 1
            Message.STATUS_SEND_DISPLAYED -> 2
            else -> 0
        }
    }
    return messages.minByOrNull { statusRank(it.status) } ?: messages.last()
}

/**
 * A run of 2+ consecutive same-sender photos/videos collapsed into one grid tile, instead of a
 * separate bubble per message. Layout adapts to the count (see the design mockup this mirrors):
 * 2 side by side at single-photo height, 3 as a hero + two stacked, 4+ as an even 2x2 with the
 * 4th cell flat-dimmed and carrying a "+N" count once there are more than [MEDIA_GRID_MAX_CELLS].
 */
@Composable
private fun MediaGroupRow(
    item: ChatItem.MediaGroup,
    revision: Int,
    highlighted: Boolean = false,
    listener: ConversationScreenListener,
    // The specific cell that was long-pressed, plus the whole group it belongs to — the sheet
    // tailors Reply/Open/Share/Forward to that one message, while Reaction/Pin/Delete still act
    // on the whole group.
    onLongPressCell: (Message, List<Message>) -> Unit,
    // "+N" tile tapped while a selection is already in progress (from this group or elsewhere) —
    // hands off to the picker screen rather than the viewer, since you can't select from inside it.
    onOpenSelector: (List<Message>) -> Unit,
    selectionActive: Boolean,
    selectedUuids: List<String>,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onToggleSingle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages = item.messages
    val first = messages.first()
    val outgoing = first.status != Message.STATUS_RECEIVED
    val isGroupChat = first.getConversation().getMode() == Conversational.MODE_MULTI
    val showAvatarSlot = !outgoing && isGroupChat
    val context = LocalContext.current
    val avatarBitmap = if (showAvatarSlot && item.lastOfGroup) {
        val avatarState = remember(item.key) { mutableStateOf<ImageBitmap?>(null) }
        val avatarSizePx = with(LocalDensity.current) { 32.dp.toPx() }.toInt()
        LaunchedEffect(item.key) {
            val activity = context as? XmppActivity ?: return@LaunchedEffect
            val bm = withContext(Dispatchers.IO) {
                try { activity.avatarService().get(first, avatarSizePx, false) }
                catch (_: Exception) { null }
            }
            if (bm != null) avatarState.value = bm.asImageBitmap()
        }
        avatarState
    } else null
    val tailInset = if (item.lastOfGroup) TAIL_WIDTH else 0.dp
    val shape = rememberBubbleShape(item.firstOfGroup, item.lastOfGroup, outgoing)

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (outgoing) 48.dp else if (showAvatarSlot) 8.dp else 12.dp - tailInset,
                    end = if (outgoing) 12.dp - tailInset else 48.dp,
                    top = if (item.firstOfGroup) 6.dp else 1.dp,
                    bottom = 1.dp,
                ),
            horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
        ) {
            AnimatedVisibility(
                visible = selectionActive,
                enter = fadeIn(tween(180)) + androidx.compose.animation.expandHorizontally(tween(180)),
                exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkHorizontally(tween(120)),
            ) {
                // The outer whole-tile combinedClickable is gone now that selection is per-cell —
                // this leading checkmark is the one remaining whole-group toggle, so it needs its
                // own click handler.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        onClick = onToggleSelected,
                    ),
                ) {
                    SelectionCheckCircle(selected = selected)
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (showAvatarSlot) {
                val bm = avatarBitmap?.value
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (bm != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bm,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
            }
            val baseColor = if (outgoing) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surfaceContainerHigh
            val containerColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else baseColor,
                label = "mediaGroupHighlight",
            )
            Surface(
                shape = shape,
                color = containerColor,
                modifier = Modifier.width(MEDIA_GRID_WIDTH),
            ) {
                Column {
                    // A small margin on every side keeps the container visible as a frame around
                    // the grid (not just below it, near the footer) — each cell gets its own
                    // modest, uniform rounding. Since cells no longer touch the Surface's own
                    // edge directly, this can't reproduce the earlier bug where a cell's flat
                    // radius fought the bubble's real shape (20dp corners / the curved tail) —
                    // there's a real gap between the two now, so the two roundings never collide.
                    //
                    // Selection is per-cell here, not whole-tile: while selecting, tapping a
                    // specific photo selects just that one; the leading checkmark above is the
                    // only whole-group toggle left. The "+N" cell is the exception — selecting
                    // from it hands off to the picker screen instead, since a dimmed placeholder
                    // cell has no single message of its own to toggle.
                    Box(modifier = Modifier.padding(4.dp)) {
                        MediaGridContent(
                            messages = messages,
                            revision = revision,
                            selectionActive = selectionActive,
                            selectedUuids = selectedUuids,
                            onCellTap = { tapped ->
                                if (selectionActive) {
                                    tapped.getUuid()?.let { onToggleSingle(it) }
                                } else {
                                    listener.onOpenMediaGroup(messages, tapped)
                                }
                            },
                            onCellLongTap = { tapped -> onLongPressCell(tapped, messages) },
                            onOverflowSelect = { onOpenSelector(messages) },
                        )
                    }
                    Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp)) {
                        MessageFooter(
                            message = messages.last(),
                            outgoing = outgoing,
                            revision = revision,
                            statusMessage = remember(revision) { groupStatusRepresentative(messages) },
                        )
                    }
                }
            }
        }
    }
}

private val MEDIA_CELL_SHAPE = RoundedCornerShape(10.dp)

@Composable
private fun MediaGridContent(
    messages: List<Message>,
    revision: Int,
    selectionActive: Boolean,
    selectedUuids: List<String>,
    onCellTap: (Message) -> Unit,
    onCellLongTap: (Message) -> Unit,
    onOverflowSelect: () -> Unit,
) {
    // Every cell gets its own modest, uniform rounding — safe now that MediaGroupRow insets the
    // whole grid from the Surface's real edge with a margin, so a cell's corner never coincides
    // with (and can't mismatch) the bubble's own shape.
    when (messages.size) {
        2 -> Row(
            modifier = Modifier.height(MEDIA_GRID_SINGLE_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MediaGridCell(
                messages[0],
                Modifier.weight(1f).fillMaxHeight().clip(MEDIA_CELL_SHAPE),
                onCellTap,
                onCellLongTap,
                revision = revision,
                selectionActive = selectionActive,
                selected = messages[0].getUuid() in selectedUuids,
            )
            MediaGridCell(
                messages[1],
                Modifier.weight(1f).fillMaxHeight().clip(MEDIA_CELL_SHAPE),
                onCellTap,
                onCellLongTap,
                revision = revision,
                selectionActive = selectionActive,
                selected = messages[1].getUuid() in selectedUuids,
            )
        }
        3 -> Row(
            modifier = Modifier.height(MEDIA_GRID_HERO_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MediaGridCell(
                messages[0],
                Modifier.weight(1.3f).fillMaxHeight().clip(MEDIA_CELL_SHAPE),
                onCellTap,
                onCellLongTap,
                revision = revision,
                selectionActive = selectionActive,
                selected = messages[0].getUuid() in selectedUuids,
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MediaGridCell(
                    messages[1],
                    Modifier.weight(1f).fillMaxWidth().clip(MEDIA_CELL_SHAPE),
                    onCellTap,
                    onCellLongTap,
                    revision = revision,
                    selectionActive = selectionActive,
                    selected = messages[1].getUuid() in selectedUuids,
                )
                MediaGridCell(
                    messages[2],
                    Modifier.weight(1f).fillMaxWidth().clip(MEDIA_CELL_SHAPE),
                    onCellTap,
                    onCellLongTap,
                    revision = revision,
                    selectionActive = selectionActive,
                    selected = messages[2].getUuid() in selectedUuids,
                )
            }
        }
        else -> {
            val overflow = messages.size - 3
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MediaGridCell(
                        messages[0],
                        Modifier.weight(1f).aspectRatio(4f / 5f).clip(MEDIA_CELL_SHAPE),
                        onCellTap,
                        onCellLongTap,
                        revision = revision,
                        selectionActive = selectionActive,
                        selected = messages[0].getUuid() in selectedUuids,
                    )
                    MediaGridCell(
                        messages[1],
                        Modifier.weight(1f).aspectRatio(4f / 5f).clip(MEDIA_CELL_SHAPE),
                        onCellTap,
                        onCellLongTap,
                        revision = revision,
                        selectionActive = selectionActive,
                        selected = messages[1].getUuid() in selectedUuids,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    MediaGridCell(
                        messages[2],
                        Modifier.weight(1f).aspectRatio(4f / 5f).clip(MEDIA_CELL_SHAPE),
                        onCellTap,
                        onCellLongTap,
                        revision = revision,
                        selectionActive = selectionActive,
                        selected = messages[2].getUuid() in selectedUuids,
                    )
                    // The 4th cell is a real message (still tappable — it's genuinely visible,
                    // just dimmed), not a synthetic "more" tile. "+N" counts everything not
                    // fully visible: this dimmed cell plus whatever isn't shown at all. While a
                    // selection is in progress, tapping it hands off to the picker screen instead
                    // of toggling — a dimmed placeholder cell has no single message of its own to
                    // meaningfully "select" on its own.
                    MediaGridCell(
                        messages[3],
                        Modifier.weight(1f).aspectRatio(4f / 5f).clip(MEDIA_CELL_SHAPE),
                        onCellTap,
                        onCellLongTap,
                        revision = revision,
                        selectionActive = selectionActive,
                        selected = messages[3].getUuid() in selectedUuids,
                        overlayCount = if (overflow > 0) overflow + 1 else null,
                        isOverflow = overflow > 0,
                        onOverflowSelect = onOverflowSelect,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaGridCell(
    message: Message,
    modifier: Modifier,
    onTap: (Message) -> Unit,
    onLongTap: (Message) -> Unit,
    revision: Int,
    selectionActive: Boolean,
    selected: Boolean,
    overlayCount: Int? = null,
    isOverflow: Boolean = false,
    onOverflowSelect: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? XmppActivity
    val fileBackend = activity?.xmppConnectionService?.fileBackend
    val uuid = message.getUuid()
    val isVideo = message.mimeType?.startsWith("video/") == true
    val cachedBitmap = ThumbnailCache.get(uuid)
    if (cachedBitmap == null && fileBackend != null) {
        val sizePx = with(LocalDensity.current) { MEDIA_GRID_WIDTH.toPx() / 2 }.toInt()
        LaunchedEffect(uuid) {
            val bm = withContext(Dispatchers.IO) {
                try { fileBackend.getThumbnail(message, sizePx, false, false) } catch (_: Exception) { null }
            }
            if (bm != null) ThumbnailCache.put(uuid, bm.asImageBitmap())
        }
    }
    // transferable.getProgress() mutates in place; reading `revision` re-triggers this read —
    // same pattern MessageContent uses for the single-bubble upload/download indicator.
    val transferable = message.transferable
    val transferableProgress = remember(revision) { transferable?.getProgress() }
    val animatedProgress by animateFloatAsState(
        targetValue = (transferableProgress ?: 0) / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "gridCellTransferProgress",
    )
    val handleTap = { if (selectionActive && isOverflow) onOverflowSelect() else onTap(message) }
    Box(
        modifier = modifier.combinedClickable(
            onClick = handleTap,
            onLongClick = { if (selectionActive) handleTap() else onLongTap(message) },
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = ThumbnailCache.get(uuid)
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            // No decoded thumbnail yet — either it's still being uploaded/downloaded (real
            // progress available from `transferable`) or it's simply mid-decode from an
            // already-local file (indeterminate). Either way this cell is currently visible, so
            // it gets the same wavy spinner treatment as a single-message media bubble instead of
            // sitting as a flat, silent placeholder.
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            if (transferableProgress != null && transferableProgress > 0) {
                CircularWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                )
            } else {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                )
            }
        }
        if (isVideo) {
            Icon(
                painter = painterResource(R.drawable.ic_play_circle_24dp),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        if (overlayCount != null) {
            Box(
                modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overlayCount",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        // Per-cell selection affordance while selecting is in progress — the leading checkmark
        // before the grid toggles the whole group, this one toggles just this specific photo.
        if (selectionActive && !isOverflow) {
            Box(modifier = Modifier.matchParentSize().padding(4.dp), contentAlignment = Alignment.TopEnd) {
                SelectionCheckCircle(selected = selected)
            }
            if (selected) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.25f)))
            }
        }
    }
}

@Composable
internal fun SelectionCheckCircle(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.CircleShape)
                        .border(
                            1.5.dp,
                            MaterialTheme.colorScheme.outline,
                            androidx.compose.foundation.shape.CircleShape,
                        )
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ReactionChips(
    message: Message,
    outgoing: Boolean,
    revision: Int,
    listener: ConversationScreenListener,
    modifier: Modifier = Modifier,
) {
    val aggregated = remember(revision) { message.getAggregatedReactions() }
    val canAdd = !outgoing && Restrictions.reactionsPerUserRemaining(message)
    // Only render the row when there are actual reactions. The + button is shown inline when
    // reactions exist; for messages with no reactions the long-press sheet has "Add reaction".
    if (aggregated.reactions.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (outgoing) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        aggregated.reactions.forEach { entry ->
            val emoji = entry.key
            val count = entry.value
            val isOurs = emoji in aggregated.ourReactions
            // key() gives each emoji its own composable identity, so a chip that's brand new
            // this recomposition mounts fresh. AnimatedVisibility only plays its enter
            // transition on an actual false→true edge — a MutableTransitionState created
            // already-false-then-pushed-to-true gives it that edge on first composition;
            // `visible = true` alone never transitions, so nothing would have visibly played.
            key(emoji) {
                val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = scaleIn(
                        initialScale = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) + fadeIn(tween(150)),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        // "Ours" uses tertiaryContainer, not primaryContainer: primaryContainer is
                        // also the outgoing bubble's own background, so a reaction you added to
                        // your own message used to be visually identical to the bubble it
                        // overlaps — it read as melting into the bubble rather than as a distinct
                        // chip. tertiary is deliberately hue-shifted further from the seed color
                        // in Material You, so it stays visually distinct from both the outgoing
                        // (primaryContainer) and incoming (surfaceContainerHigh) bubble colors.
                        color =
                            if (isOurs) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.secondaryContainer,
                        modifier =
                            Modifier.combinedClickable(
                                onClick = {
                                    val next = aggregated.ourReactions.toMutableSet()
                                    if (isOurs) next.remove(emoji) else next.add(emoji)
                                    listener.onSendReactions(message, next)
                                },
                                onLongClick = { listener.onShowReactionDetails(message, emoji) },
                            ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(text = emoji, style = MaterialTheme.typography.bodyMedium)
                            // Two nested transitions, not one: an outer AnimatedVisibility for
                            // the number showing up at all (count crossing 1→2, previously just
                            // popped in unanimated since AnimatedContent has nothing to
                            // transition FROM on the frame it first mounts), and an inner
                            // AnimatedContent for the digit changing once it's already visible.
                            AnimatedVisibility(
                                visible = count > 1,
                                enter = scaleIn(
                                    initialScale = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                ) + fadeIn(tween(150)),
                                exit = fadeOut(tween(100)),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(Modifier.width(3.dp))
                                    AnimatedContent(
                                        targetState = count,
                                        transitionSpec = {
                                            if (targetState > initialState) {
                                                (slideInVertically { it } + fadeIn()) togetherWith
                                                    (slideOutVertically { -it } + fadeOut())
                                            } else {
                                                (slideInVertically { -it } + fadeIn()) togetherWith
                                                    (slideOutVertically { it } + fadeOut())
                                            }
                                        },
                                        label = "reactionCount",
                                    ) { animatedCount ->
                                        Text(
                                            text = animatedCount.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color =
                                                if (isOurs) MaterialTheme.colorScheme.onTertiaryContainer
                                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (canAdd) {
            Surface(
                onClick = { listener.onAddReaction(message) },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add_reaction_24dp),
                    contentDescription = stringResource(R.string.add_reaction),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(4.dp).size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    item: ChatItem.Msg,
    outgoing: Boolean,
    highlighted: Boolean,
    isBeingEdited: Boolean,
    revision: Int,
    listener: ConversationScreenListener,
    onLongPress: (Message) -> Unit,
    resolveReply: (String) -> Message?,
    onReplyCardClick: (Message) -> Unit,
) {
    val message = item.message
    val failed = message.status == Message.STATUS_SEND_FAILED
    val baseColor =
        when {
            failed -> MaterialTheme.colorScheme.errorContainer
            outgoing -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val containerColor by
        androidx.compose.animation.animateColorAsState(
            targetValue =
                if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else baseColor,
            label = "bubbleHighlight",
        )
    val contentColor =
        when {
            failed -> MaterialTheme.colorScheme.onErrorContainer
            outgoing -> if (isSystemInDarkTheme()) Color.White else Color.Black
            else -> MaterialTheme.colorScheme.onSurface
        }

    val hasTail = item.lastOfGroup
    val blurRadius by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isBeingEdited) 5.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.spring(),
        label = "editingBlur",
    )
    Box {
        Surface(
            shape =
                rememberBubbleShape(
                    firstOfGroup = item.firstOfGroup,
                    lastOfGroup = item.lastOfGroup,
                    outgoing = outgoing,
                ),
            color = containerColor,
            contentColor = contentColor,
            modifier = Modifier
                .widthIn(max = if (hasTail) 320.dp + TAIL_WIDTH else 320.dp)
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier),
        ) {
            Column(
                modifier =
                    Modifier.combinedClickable(
                            onClick = {
                                if (message.mimeType?.startsWith("audio/") != true) {
                                    listener.onOpenMessage(message)
                                }
                            },
                            onLongClick = { onLongPress(message) },
                        )
                        .padding(
                            start = if (hasTail && !outgoing) 12.dp + TAIL_WIDTH else 12.dp,
                            end = if (hasTail && outgoing) 12.dp + TAIL_WIDTH else 12.dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        )
            ) {
                val repliedToId = message.getRepliedTo()
                if (repliedToId != null) {
                    // Keyed on revision too, not just repliedToId: if the reply's target message
                    // hasn't been paged in yet (e.g. it's older than the initially-loaded
                    // window), resolveReply() returns null on the first composition. Without
                    // revision in the key, that null gets cached forever — the reply card never
                    // reappears even after the target is later loaded via onLoadMoreMessages().
                    val original = remember(repliedToId, revision) { resolveReply(repliedToId) }
                    if (original != null) {
                        ReplyCard(original = original, onClick = { onReplyCardClick(original) })
                    }
                }
                MessageContent(
                    message = message,
                    revision = revision,
                    contentColor = contentColor,
                    onLongPress = { onLongPress(message) },
                    listener = listener,
                )
                if (!isBeingEdited) {
                    MessageFooter(message = message, outgoing = outgoing, revision = revision)
                }
            }
        }
        // Editing indicator: rendered outside the blurred Surface so it stays crisp
        if (isBeingEdited) {
            val infiniteTransition = rememberInfiniteTransition(label = "editingPulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "editingAlpha",
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_edit_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = stringResource(R.string.editing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
    }
}

@Composable
private fun ReplyCard(original: Message, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        ) {
            Box(
                modifier =
                    Modifier.width(3.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
            )
            Column(
                modifier =
                    Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = UIHelper.getMessageDisplayName(original),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = MessageUtils.replyPreview(original),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val isVisualMedia = original.type == Message.TYPE_IMAGE ||
                ((original.type == Message.TYPE_FILE || original.type == Message.TYPE_PRIVATE_FILE) &&
                    original.getMimeType()?.startsWith("video/") == true)
            if (isVisualMedia && !original.isDeleted) {
                val activity = context as? XmppActivity
                val fileBackend = activity?.xmppConnectionService?.fileBackend
                if (fileBackend != null) {
                    val thumb =
                        remember(original.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
                    val sizePx = with(LocalDensity.current) { 40.dp.toPx() }.toInt()
                    LaunchedEffect(original.getUuid()) {
                        val bm =
                            withContext(Dispatchers.IO) {
                                try {
                                    fileBackend.getThumbnail(original, sizePx, false)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        if (bm != null) thumb.value = bm.asImageBitmap()
                    }
                    val bitmap = thumb.value
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier.padding(end = 6.dp)
                                    .size(40.dp)
                                    // approximates the squircle used by the View reply card
                                    .clip(RoundedCornerShape(28)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageContent(
    message: Message,
    revision: Int,
    contentColor: androidx.compose.ui.graphics.Color,
    onLongPress: () -> Unit,
    listener: ConversationScreenListener,
) {
    val context = LocalContext.current
    val activity = context as? XmppActivity
    val transferable = message.transferable
    // transferable.getProgress() mutates in place; reading `revision` re-triggers this read.
    val transferableProgress = remember(revision) { transferable?.getProgress() }
    val animatedProgressRaw by animateFloatAsState(
        targetValue = (transferableProgress ?: 0) / 100f,
        animationSpec = tween(durationMillis = 300),
        label = "transferProgress",
    )
    val animatedProgress = animatedProgressRaw.coerceIn(0f, 1f)

    when {
        message.isDeleted -> {
            Text(
                text = stringResource(R.string.file_deleted),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        transferable != null && transferable.getStatus() == Transferable.STATUS_DOWNLOADING -> {
            val fp = message.fileParams
            if (fp.width > 0 && fp.height > 0) {
                DownloadingMediaPlaceholder(
                    progress = animatedProgress,
                    aspectRatio = fp.width.toFloat() / fp.height.toFloat(),
                )
            } else {
                Column(modifier = Modifier.widthIn(min = 160.dp, max = 240.dp)) {
                    Text(
                        text = stringResource(
                            R.string.receiving_x_file,
                            UIHelper.getFileDescriptionString(context, message),
                            transferableProgress ?: 0,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    if ((transferableProgress ?: 0) > 0) {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                        )
                    }
                }
            }
        }
        transferable != null && transferable.getStatus() == Transferable.STATUS_CHECKING -> {
            val fileDescription = UIHelper.getFileDescriptionString(context, message)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.checking_x, fileDescription),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        (transferable != null &&
            (transferable.getStatus() == Transferable.STATUS_COMPRESSING ||
                transferable.getStatus() == Transferable.STATUS_UPLOADING)) ||
            // hasBubbleThumbnailPreview(), not isMediaCell() — this decides which composable draws
            // the bubble's resting content, a broader question than "is this eligible for grid
            // grouping / the in-app viewer" (isMediaCell()). A PDF still gets a real page preview
            // here, same as a photo, just with its baked-in watermark icon so it doesn't read as
            // one — tapping it still hands off externally regardless (that's onOpenMessage()'s own
            // isMediaCell() check, untouched by this).
            (transferable == null && hasBubbleThumbnailPreview(message)) -> {
            val fp = message.fileParams
            val isVideo = message.mimeType?.startsWith("video/") == true
            val hasKnownDimensions = fp.width > 0 && fp.height > 0
            val phase = when {
                transferable?.getStatus() == Transferable.STATUS_COMPRESSING -> MediaBubblePhase.COMPRESSING
                else -> MediaBubblePhase.MEDIA
            }
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(350)) + scaleIn(
                        initialScale = 0.88f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )) togetherWith fadeOut(animationSpec = tween(200))
                },
                label = "mediaBubblePhase",
            ) { animatedPhase ->
                when (animatedPhase) {
                    MediaBubblePhase.COMPRESSING ->
                        CompressingVideoPlaceholder(progress = animatedProgress)
                    // Uploading and sent share one persistent MediaThumbnailBubble instance —
                    // toggling `uploading` doesn't remount it, so its own internal overlays
                    // (upload scrim/spinner, play button) are the only things that animate.
                    // The base image/video itself never re-enters AnimatedContent's transition,
                    // which is what was making the whole bubble zoom instead of just the badge.
                    MediaBubblePhase.MEDIA ->
                        if (hasKnownDimensions) {
                            val uploading = transferable?.getStatus() == Transferable.STATUS_UPLOADING
                            MediaThumbnailBubble(
                                message = message,
                                isVideo = isVideo,
                                aspectRatio = fp.width.toFloat() / fp.height.toFloat(),
                                uploading = uploading,
                                // The spinner's own AnimatedVisibility content stays composed
                                // through its exit animation — hold progress at 1 once sent so
                                // it doesn't visibly reset to 0 while fading/scaling away.
                                progress = if (uploading) animatedProgress else 1f,
                                onOpen = if (uploading) null else { { listener.onOpenMessage(message) } },
                                onLongPress = if (uploading) null else onLongPress,
                            )
                        } else {
                            Column(modifier = Modifier.widthIn(min = 160.dp, max = 240.dp)) {
                                Text(
                                    text = stringResource(R.string.sending_file, transferableProgress ?: 0),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                                )
                            }
                        }
                }
            }
        }
        message.isFileOrImage &&
            !message.isDeleted &&
            message.mimeType?.startsWith("audio/") == true -> {
            AudioMessageContent(message)
        }
        (message.isFileOrImage || transferable != null) &&
            message.encryption != Message.ENCRYPTION_PGP &&
            message.encryption != Message.ENCRYPTION_DECRYPTION_FAILED &&
            (MessageUtils.unInitiatedButKnownSize(message) ||
                (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING)) -> {
            // A pending Jingle (P2P) file offer arrives as a bare TYPE_TEXT message —
            // setFileOffer() never flips message.type to TYPE_FILE/TYPE_IMAGE before the user
            // accepts — so isFileOrImage alone would skip this branch entirely for P2P offers,
            // even though transferable.getStatus() == STATUS_OFFER, falling through to the
            // final LinkifiedMessageText(body="") case: an empty bubble with no accept button.
            val fileDescription = UIHelper.getFileDescriptionString(context, message)
            when {
                MessageUtils.unInitiatedButKnownSize(message) ||
                    transferable?.getStatus() == Transferable.STATUS_OFFER ->
                    FileActionRow(
                        iconRes = R.drawable.ic_download_24dp,
                        label = stringResource(R.string.download_x_file, fileDescription),
                        onClick = { listener.onDownloadMessage(message) },
                    )
                transferable?.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE ->
                    FileActionRow(
                        iconRes = R.drawable.ic_download_24dp,
                        label = stringResource(R.string.check_x_filesize, fileDescription),
                        onClick = { listener.onDownloadMessage(message) },
                    )
                transferable?.getStatus() == Transferable.STATUS_CHECKING ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.checking_x, fileDescription),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                transferable?.getStatus() == Transferable.STATUS_FAILED ->
                    FileActionRow(
                        iconRes = R.drawable.ic_error_24dp,
                        label = stringResource(R.string.file_transmission_failed),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = { listener.onDownloadMessage(message) },
                    )
                transferable?.getStatus() == Transferable.STATUS_CANCELLED ->
                    FileActionRow(
                        iconRes = R.drawable.ic_cancel_24dp,
                        label = stringResource(R.string.file_transmission_cancelled),
                        onClick = { listener.onDownloadMessage(message) },
                    )
                else ->
                    FileActionRow(
                        iconRes = R.drawable.ic_attach_file_24dp,
                        label = fileDescription,
                    )
            }
        }
        message.isFileOrImage &&
            message.encryption != Message.ENCRYPTION_PGP &&
            message.encryption != Message.ENCRYPTION_DECRYPTION_FAILED -> {
            FileActionRow(
                iconRes = R.drawable.ic_attach_file_24dp,
                label = stringResource(R.string.open_x_file, UIHelper.getFileDescriptionString(context, message)),
                onClick = { listener.onOpenMessage(message) },
            )
        }
        message.isGeoUri -> {
            FileRow(
                iconRes = R.drawable.ic_location_pin_24dp,
                label = stringResource(R.string.location),
            )
        }
        message.treatAsDownloadable() -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { listener.onDownloadMessage(message) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_download_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = UIHelper.getMessagePreview(context, message).first.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        message.type == Message.TYPE_RTP_SESSION -> {
            FileRow(
                iconRes = R.drawable.ic_call_24dp,
                label = UIHelper.getMessagePreview(context, message).first.toString(),
            )
        }
        message.encryption == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE -> {
            Text(
                text = stringResource(R.string.not_encrypted_for_this_device),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message.encryption == Message.ENCRYPTION_AXOLOTL_FAILED -> {
            Text(
                text = stringResource(R.string.omemo_decryption_failed),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message.encryption == Message.ENCRYPTION_DECRYPTION_FAILED -> {
            Text(
                text = stringResource(R.string.decryption_failed),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message.bodyIsOnlyEmojis() && message.type != Message.TYPE_PRIVATE -> {
            val emojiText = message.body?.trim() ?: ""
            val scale = if (eu.siacs.conversations.utils.Emoticons.isEmoji(emojiText)) 3.0f else 2.0f
            Text(
                text = emojiText,
                fontSize = (16f * scale).sp,
                lineHeight = (16f * scale * 1.2f).sp,
            )
        }
        else -> {
            LinkifiedMessageText(
                message = message,
                revision = revision,
                contentColor = contentColor,
                onLongPress = onLongPress,
            )
        }
    }
}

private fun formatAudioTime(millis: Int): String {
    val totalSeconds = millis / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Inline audio player: play/pause button, seek bar and time, all inside the bubble. */
@Composable
private fun AudioMessageContent(message: Message) {
    val context = LocalContext.current
    val activity = context as? XmppActivity
    val file =
        remember(message.getUuid()) {
            try {
                activity?.xmppConnectionService?.fileBackend?.getFile(message)
            } catch (_: Exception) {
                null
            }
        }
    if (file == null || !file.exists()) {
        FileRow(
            iconRes = R.drawable.ic_mic_24dp,
            label = UIHelper.getFileDescriptionString(context, message),
        )
        return
    }

    val uuid = message.getUuid() ?: return
    val playing = AudioPlaybackController.activeUuid == uuid && AudioPlaybackController.isPlaying
    var tick by remember(uuid) { mutableIntStateOf(0) }
    // Also keyed on `playing`: the ticker below only advances while playing, so without this,
    // positionMs would freeze at its last ticked value instead of reflecting the reset to 0
    // that happens on natural completion (or the paused position on a manual pause).
    val positionMs = remember(uuid, tick, playing) { AudioPlaybackController.positionFor(uuid) }
    val durationMs =
        AudioPlaybackController.durations[uuid] ?: (message.fileParams?.runtime ?: 0)

    // Peer listen status (outgoing 1:1 voice messages): while the peer is LISTENING, this
    // ticker advances the locally-extrapolated position (wall-clock maps 1:1 to playback
    // position at 1x speed). If the estimate overruns the duration without a "listened"
    // confirmation arriving, we genuinely don't know where they are — flip to UNKNOWN.
    val isOutgoing = message.status != Message.STATUS_RECEIVED
    // In-memory state wins; the persisted terminal LISTENED is the across-restarts fallback.
    val peer =
        if (isOutgoing) {
            ListenStatusManager.peerStates[uuid]
                ?: if (message.listenStatus == Message.LISTEN_STATUS_LISTENED)
                    ListenStatusManager.PeerState(
                        ListenStatusManager.State.LISTENED, 0L, Long.MAX_VALUE
                    )
                else null
        } else null
    var peerTick by remember(uuid) { mutableIntStateOf(0) }
    LaunchedEffect(uuid, peer?.state) {
        while (ListenStatusManager.peerStates[uuid]?.state == ListenStatusManager.State.LISTENING) {
            peerTick++
            if (durationMs > 0 &&
                ListenStatusManager.estimatedListenedMs(uuid) > durationMs + 4000L
            ) {
                ListenStatusManager.markUnknown(uuid)
                break
            }
            kotlinx.coroutines.delay(250)
        }
    }
    val peerFraction =
        if (peer == null || durationMs <= 0) 0f
        else {
            @Suppress("UNUSED_EXPRESSION") peerTick
            when (peer.state) {
                ListenStatusManager.State.LISTENED, ListenStatusManager.State.UNKNOWN -> 1f
                else ->
                    (ListenStatusManager.estimatedListenedMs(uuid).toFloat() / durationMs)
                        .coerceIn(0f, 1f)
            }
        }
    // Local playback owns the track whenever it has any position; otherwise (idle outgoing
    // bubble) the track becomes the peer-progress display, moving entirely natively — same
    // slider, same thumb, only the fill value and colors are driven by the peer state.
    val localOwnsTrack = playing || positionMs > 0

    LaunchedEffect(uuid) {
        AudioPlaybackController.onRowEnteredComposition(uuid, file)
    }

    androidx.compose.runtime.DisposableEffect(uuid) {
        onDispose { AudioPlaybackController.onRowLeftComposition(uuid) }
    }

    LaunchedEffect(playing) {
        while (playing) {
            tick++
            kotlinx.coroutines.delay(250)
        }
    }

    // Spring-animated seek position — ticks land every 250ms, which without this reads as a
    // jump cut rather than smooth motion (especially obvious on short messages, and on the
    // reset to 0 after natural completion). Snap instantly while the user is actively dragging
    // so direct manipulation stays 1:1; animate the rest of the time. Animatable.animateTo
    // naturally re-targets mid-flight if ticks arrive back-to-back, so stacked updates stay
    // smooth instead of restarting the animation from scratch.
    val rawFraction =
        if (durationMs <= 0) 0f
        else if (localOwnsTrack) positionMs.toFloat() / durationMs
        else peerFraction
    val sliderInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    var isDraggingSlider by remember { mutableStateOf(false) }
    LaunchedEffect(sliderInteractionSource) {
        sliderInteractionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.DragInteraction.Start -> isDraggingSlider = true
                is androidx.compose.foundation.interaction.DragInteraction.Stop,
                is androidx.compose.foundation.interaction.DragInteraction.Cancel -> isDraggingSlider = false
            }
        }
    }
    val animatedFraction = remember(uuid) { Animatable(rawFraction) }
    LaunchedEffect(rawFraction, isDraggingSlider) {
        if (isDraggingSlider) {
            animatedFraction.snapTo(rawFraction)
        } else {
            animatedFraction.animateTo(
                rawFraction,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(232.dp)) {
        FilledIconButton(
            onClick = { AudioPlaybackController.toggle(uuid, file) },
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter =
                    painterResource(
                        if (playing) R.drawable.ic_pause_24dp else R.drawable.ic_play_arrow_24dp
                    ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
        // Thumb is short while idle, taller while playing — spring-animated between the two,
        // reusing SliderDefaults.Thumb's own thumbSize param rather than a custom control.
        val thumbHeight by
            androidx.compose.animation.core.animateDpAsState(
                targetValue = if (playing) 44.dp else 20.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "audioThumbHeight",
            )
        // Two-tone track: the filled left side is "what has been listened to" — white with a
        // small primary tint, whether that's local playback (incoming) or the peer's
        // extrapolated progress (outgoing). Pure white read as too high-contrast; a light touch
        // of tint softens it without going back to looking gray. UNKNOWN swaps the fill to a
        // white-with-error tint. The unfilled right side keeps the default muted inactive color
        // ("not listened").
        val listenedTint =
            androidx.compose.ui.graphics.lerp(
                Color.White, MaterialTheme.colorScheme.primary, 0.08f
            )
        val unknownTint =
            androidx.compose.ui.graphics.lerp(
                Color.White, MaterialTheme.colorScheme.error, 0.22f
            )
        val activeTrackColor by
            androidx.compose.animation.animateColorAsState(
                targetValue =
                    if (!localOwnsTrack && peer?.state == ListenStatusManager.State.UNKNOWN)
                        unknownTint
                    else listenedTint,
                animationSpec = spring(stiffness = 1600f, dampingRatio = 1.0f),
                label = "audioTrackColor",
            )
        androidx.compose.material3.Slider(
            value = animatedFraction.value,
            onValueChange = { fraction ->
                val target = (fraction * durationMs).toInt()
                AudioPlaybackController.seekTo(uuid, file, target)
                tick++
            },
            interactionSource = sliderInteractionSource,
            colors =
                androidx.compose.material3.SliderDefaults.colors(
                    activeTrackColor = activeTrackColor,
                ),
            thumb = { sliderState ->
                androidx.compose.material3.SliderDefaults.Thumb(
                    interactionSource = sliderInteractionSource,
                    sliderState = sliderState,
                    thumbSize = androidx.compose.ui.unit.DpSize(4.dp, thumbHeight),
                )
            },
            track = { sliderState ->
                // The default stop-indicator dot at the track's end reuses activeTrackColor,
                // which we've tinted near-white — against that, or against a light theme's
                // inactive track, the dot loses contrast and effectively disappears. Draw it
                // with a fixed, always-visible color instead, independent of listen-status tint.
                val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
                val dotRadiusPx =
                    with(androidx.compose.ui.platform.LocalDensity.current) {
                        (androidx.compose.material3.SliderDefaults.TrackStopIndicatorSize / 2).toPx()
                    }
                androidx.compose.material3.SliderDefaults.Track(
                    sliderState = sliderState,
                    colors =
                        androidx.compose.material3.SliderDefaults.colors(
                            activeTrackColor = activeTrackColor,
                        ),
                    drawStopIndicator = { offset ->
                        drawCircle(color = dotColor, radius = dotRadiusPx, center = offset)
                    },
                )
            },
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            text =
                formatAudioTime(
                    if (playing || positionMs > 0) positionMs else durationMs
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileRow(iconRes: Int, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CompressingVideoPlaceholder(progress: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_videocam_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.transcoding_video),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            if (progress > 0f) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = primary,
                    trackColor = primary.copy(alpha = 0.20f),
                )
            } else {
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = primary,
                    trackColor = primary.copy(alpha = 0.20f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadingMediaPlaceholder(progress: Float, aspectRatio: Float) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .aspectRatio(aspectRatio.coerceIn(0.25f, 4f))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        CircularWavyProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(56.dp),
            color = primary,
            trackColor = primary.copy(alpha = 0.20f),
        )
    }
}

private enum class MediaBubblePhase { COMPRESSING, MEDIA }

/** Image/video bubble shared by the uploading and sent states — same box, same aspect ratio,
 * same [ThumbnailCache]-backed bitmap throughout, so finishing an upload never remounts a fresh
 * composable that has to redecode a thumbnail it already had a frame earlier. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaThumbnailBubble(
    message: Message,
    isVideo: Boolean,
    aspectRatio: Float,
    uploading: Boolean,
    progress: Float,
    onOpen: (() -> Unit)?,
    onLongPress: (() -> Unit)?,
) {
    val context = LocalContext.current
    val activity = context as? XmppActivity
    val fileBackend = activity?.xmppConnectionService?.fileBackend
    val uuid = message.getUuid()

    val videoFile = if (uploading && isVideo) {
        remember(uuid) {
            try { fileBackend?.getFile(message) } catch (_: Exception) { null }
        }
    } else null

    val cachedBitmap = ThumbnailCache.get(uuid)
    if (cachedBitmap == null && fileBackend != null) {
        val sizePx = with(LocalDensity.current) { 280.dp.toPx() }.toInt()
        // For video, this bubble draws its own animated play affordance below, so the baked-in
        // overlay FileBackend normally adds would double up — suppressed. PDFs have no Compose-
        // native overlay of their own here, so they still need the baked-in "open PDF" watermark
        // to read as a document rather than a photo; only video opts out.
        LaunchedEffect(uuid) {
            val bm = withContext(Dispatchers.IO) {
                try {
                    fileBackend.getThumbnail(message, sizePx, false, !isVideo)
                } catch (_: Exception) {
                    null
                }
            }
            if (bm != null) ThumbnailCache.put(uuid, bm.asImageBitmap())
        }
    }

    val playerRef = remember(uuid) { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(uuid) {
        onDispose {
            val mp = playerRef.value
            playerRef.value = null
            mp?.release()
        }
    }

    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .heightIn(max = 310.dp)
            .aspectRatio(aspectRatio.coerceIn(0.25f, 4f))
            .clip(RoundedCornerShape(12.dp))
            .let { base ->
                if (onOpen != null) {
                    base.combinedClickable(onClick = onOpen, onLongClick = onLongPress ?: {})
                } else base
            },
        contentAlignment = Alignment.Center,
    ) {
        if (videoFile != null) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                val mp = MediaPlayer()
                                try {
                                    mp.setDataSource(videoFile.absolutePath)
                                    mp.setSurface(Surface(st))
                                    mp.setVolume(0f, 0f)
                                    mp.isLooping = true
                                    mp.prepareAsync()
                                    mp.setOnPreparedListener { it.start() }
                                    playerRef.value = mp
                                } catch (_: Exception) {
                                    mp.release()
                                }
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                val mp = playerRef.value
                                playerRef.value = null
                                mp?.release()
                                return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (cachedBitmap != null) {
            Image(
                bitmap = cachedBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }

        // Scrim and spinner are two separate AnimatedVisibilitys, not one: the scrim covers the
        // whole bubble, so scaling it down along with the spinner made the darkening itself
        // visibly shrink toward the center — a "zooming" darkening, not what was intended. Only
        // the spinner (the actual loading indicator) scales away; the scrim just fades.
        AnimatedVisibility(
            visible = uploading,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
            )
        }
        AnimatedVisibility(
            visible = uploading,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.6f, animationSpec = tween(200)),
        ) {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(56.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.30f),
            )
        }

        // Play affordance for a finished video — its own beat, delayed to land only once the
        // scrim above has fully cleared, never blended with it.
        AnimatedVisibility(
            visible = !uploading && isVideo,
            enter = fadeIn(tween(220, delayMillis = 260)) +
                scaleIn(
                    initialScale = 0.4f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play_arrow_24dp),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** Material 3 Expressive pill-shaped row for file/transfer affordances inside a bubble. */
@Composable
private fun FileActionRow(
    iconRes: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.MessageFooter(
    message: Message,
    outgoing: Boolean,
    revision: Int,
    // Drives the status icon/checkmark only — [message] itself still drives the time/size text.
    // Defaults to [message] so every single-message call site is unaffected; a grid tile passes
    // in its own "weakest link" representative instead (see groupStatusRepresentative) so the
    // tile's status reflects the whole batch rather than whichever message the footer happens to
    // be attached to.
    statusMessage: Message = message,
) {
    // Message is a mutated-in-place Java entity; reading `revision` here is what
    // makes Compose re-read message.status after an in-place status change.
    val status = remember(revision) { statusMessage.status }
    val context = LocalContext.current
    // Mirrors the legacy MessageAdapter footer, which joined the file size into the same
    // time/status line (e.g. "1.2 MiB · 14:03") instead of only showing it on the download row.
    val fileSize = remember(revision) {
        val transferable = message.transferable
        if (message.isFileOrImage || transferable != null || MessageUtils.unInitiatedButKnownSize(message)) {
            message.fileParams.size?.let { UIHelper.filesizeToString(it) }
        } else null
    }
    val timeText = DateUtils.formatDateTime(context, message.timeSent, DateUtils.FORMAT_SHOW_TIME)
    // Voice-message listen status, leftmost in the "status · size · time" line for the states
    // that still use text (PAUSED, and the incoming "have I listened to this yet" case).
    // LISTENING/LISTENED/UNKNOWN render the animated headphone badge (listenIconState below)
    // instead of text — NOT_LISTENED gets neither: no badge, no label, falls back to the plain
    // checkmark like any other outgoing message.
    val footerUuid = message.getUuid()
    val isAudio = message.mimeType?.startsWith("audio/") == true
    val outgoingPeerState: ListenStatusManager.State? =
        if (!isAudio || footerUuid == null || !outgoing ||
            message.conversation.getMode() != Conversational.MODE_SINGLE
        ) {
            null
        } else {
            ListenStatusManager.peerStates[footerUuid]?.state
                ?: if (message.listenStatus == Message.LISTEN_STATUS_LISTENED)
                    ListenStatusManager.State.LISTENED
                else null
        }
    val listenIconState: ListenStatusManager.State? = when (outgoingPeerState) {
        ListenStatusManager.State.LISTENING,
        ListenStatusManager.State.LISTENED,
        ListenStatusManager.State.UNKNOWN -> outgoingPeerState
        else -> null
    }
    val listenLabel: String? =
        if (!isAudio || footerUuid == null) {
            null
        } else if (outgoing) {
            if (outgoingPeerState == ListenStatusManager.State.PAUSED)
                stringResource(R.string.listen_status_paused)
            else null
        } else {
            if (ListenStatusManager.localListened[footerUuid] == true ||
                message.listenStatus == Message.LISTEN_STATUS_LISTENED
            ) null
            else stringResource(R.string.listen_status_not_listened)
        }
    // The "whispered"/"to X" label lives only here now (not inline in the body) so it reads the
    // same way for every private message, text or file/image, without crowding the message text.
    val privateLabel = if (!message.isPrivateMessage()) {
        null
    } else if (outgoing) {
        stringResource(R.string.private_message_to, message.counterpart?.resource ?: "")
    } else {
        stringResource(R.string.private_message)
    }
    val privateLabelColor = privateLabel?.let { Color(UIHelper.getColorForName(it)) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
    ) {
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
        val footerText = remember(listenLabel, privateLabel, privateLabelColor, fileSize, timeText, onSurfaceVariant) {
            val segments = buildList<Pair<String, Color?>> {
                listenLabel?.let { add(it to null) }
                privateLabel?.let { add(it to privateLabelColor) }
                fileSize?.let { add(it to null) }
                add(timeText to null)
            }
            buildAnnotatedString {
                segments.forEachIndexed { index, (text, color) ->
                    if (index > 0) append(" · ")
                    if (color != null) {
                        withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                            append(text)
                        }
                    } else {
                        append(text)
                    }
                }
            }
        }
        Text(
            text = footerText,
            style = MaterialTheme.typography.labelSmall,
            color = onSurfaceVariant,
        )
        if (message.edited()) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_edit_24dp),
                contentDescription = stringResource(R.string.correct_message),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
        if (message.encryption != Message.ENCRYPTION_NONE) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_lock_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
        }
        if (outgoing && statusMessage.type != Message.TYPE_RTP_SESSION) {
            val transferable = statusMessage.transferable
            // Waiting/uploading/p2p-offered/sent/delivered/read all morph into each other
            // continuously — and, for a voice message once the peer does anything with it
            // (listening, listened, or extrapolation losing track), that same morph continues
            // right on into the headphone glyph instead of a separate icon bolted on next to the
            // checkmark. See MessageStatusIcon for the full choreography (including how
            // STATUS_UNSEND is split between "still sending text" and "file genuinely
            // mid-upload", and how only a user-initiated cancel joins the morph story, not a
            // generic send/upload error). Only that generic error glyph falls through to a plain
            // crossfade below.
            val checkmarkPhase =
                voiceCheckmarkPhase(
                    checkmarkPhaseForStatus(status, transferable, statusMessage.errorMessage),
                    listenIconState,
                )
            if (checkmarkPhase != null) {
                Spacer(Modifier.width(4.dp))
                MessageStatusIcon(
                    phase = checkmarkPhase,
                    grayColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    successColor = LocalSuccessColors.current.success,
                    listenedColor = LocalSuccessColors.current.success,
                    unknownColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                val statusDrawable = MessageAdapter.getMessageStatusAsDrawable(statusMessage, status)
                if (statusDrawable != null) {
                    Spacer(Modifier.width(4.dp))
                    // Upload/failed/p2p icons aren't part of that story — a dots-to-checkmark
                    // morph makes no sense turning into an error glyph, so these just crossfade.
                    AnimatedContent(
                        targetState = statusDrawable,
                        transitionSpec = {
                            (fadeIn(tween(180)) +
                                scaleIn(initialScale = 0.6f, animationSpec = tween(180))) togetherWith
                                (fadeOut(tween(120)) +
                                    scaleOut(targetScale = 0.6f, animationSpec = tween(120)))
                        },
                        label = "messageStatusIconFallback",
                    ) { drawableRes ->
                        Icon(
                            painter = painterResource(drawableRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One entry of the long-press context sheet. */
private class SheetAction(
    val iconRes: Int,
    val label: String,
    val onClick: () -> Unit,
)

/** Which one-shot explainer MessageActionOnboardingSheet is showing — each maps to its own
 * OnboardingPreferences flag and its own title/body copy. */
private enum class OnboardingKind { EDIT, DELETE }

/**
 * Small explainer shown the first time a user edits or deletes a message — gated per-kind on
 * eu.siacs.conversations.utils.OnboardingPreferences so it only ever appears once per action.
 * A full-width "Got it" button is the only dismissal, deliberately more obvious than relying on
 * a swipe-down some users won't discover on their own.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionOnboardingSheet(kind: OnboardingKind, onDismiss: () -> Unit) {
    val iconRes = when (kind) {
        OnboardingKind.EDIT -> R.drawable.ic_edit_24dp
        OnboardingKind.DELETE -> R.drawable.ic_info_outline_24dp
    }
    val titleRes = when (kind) {
        OnboardingKind.EDIT -> R.string.onboarding_edit_title
        OnboardingKind.DELETE -> R.string.onboarding_delete_title
    }
    val bodyRes = when (kind) {
        OnboardingKind.EDIT -> R.string.onboarding_edit_body
        OnboardingKind.DELETE -> R.string.onboarding_delete_body
    }
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(bodyRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_got_it))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** The send-failure detail banner at the top of the context sheet — one per distinct error, used
 * both for a single failed message and, per distinct error, for a batch's failures. */
@Composable
private fun ErrorBanner(text: String) {
    Surface(
        shape = RoundedCornerShape(CORNER_LARGE),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_error_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// Mirrors the old ConversationFragment's static ackModeration field: once the user confirms the
// "this deletes for everyone" disclaimer, skip it for the next 5 minutes rather than showing it
// on every single moderation action in a row.
private var moderationDisclaimerAckedUntil: java.time.Instant = java.time.Instant.MIN

private fun isModerationDisclaimerAcked(): Boolean =
    moderationDisclaimerAckedUntil.isAfter(java.time.Instant.now())

private fun markModerationDisclaimerAcked() {
    moderationDisclaimerAckedUntil = java.time.Instant.now().plus(java.time.Duration.ofMinutes(5))
}

/**
 * Long-press message menu: an M3 Expressive bottom sheet whose actions are rendered as a
 * grouped list — large outer corners, tight inner corners — matching the bubble language.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MessageContextSheet(
    message: Message,
    groupMessages: List<Message>? = null,
    state: ConversationScreenState,
    listener: ConversationScreenListener,
    onSelect: (Message) -> Unit,
    // Group-tile "Select" tap — shows the "All Photos" vs "Select Photos" popup instead of
    // selecting immediately, since a single tap can't disambiguate "the whole batch" from "let me
    // pick which ones."
    onSelectGroup: (List<Message>) -> Unit = {},
    // "Select to delete" — opens the picker directly (no popup first; "Delete Files" below already
    // covers "delete everything", so this item exists specifically for picking a subset).
    onSelectToDelete: (List<Message>) -> Unit = {},
    onDeleteGroup: (List<Message>) -> Unit = {},
    // First time editing/deleting: instead of running the action immediately, hand off to
    // ConversationScreen() to show MessageActionOnboardingSheet, then run [action] once that's
    // dismissed. Every later edit/delete for that user runs [action] straight away.
    onNeedsOnboarding: (kind: OnboardingKind, action: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val onboardingPrefs = remember(context) { eu.siacs.conversations.utils.OnboardingPreferences(context) }
    val conversation = state.conversation.value
    val deleted = message.isDeleted
    val transferable = message.transferable
    val receiving = message.status == Message.STATUS_RECEIVED
            && (transferable is eu.siacs.conversations.xmpp.jingle.JingleFileTransferConnection
                || transferable is eu.siacs.conversations.http.HttpDownloadConnection)
    val waitingOrOffered = message.status == Message.STATUS_WAITING
            || message.status == Message.STATUS_UNSEND
            || message.status == Message.STATUS_OFFERED
    val cancelable = (transferable != null && !deleted) || (waitingOrOffered && message.needsUploading())
    val fileDescription = UIHelper.getFileDescriptionString(context, message)
    // Reply/Open/Share/Forward below are tailored to [message] — the specific cell that was
    // long-pressed, per-cell long-press being how this sheet is reached for a grid tile at all.
    // Reaction/Pin have no per-photo meaning (a reaction/pin attaches to one stanza, and the tile
    // only ever renders one footer, on its last message) so those act on the group's last message
    // instead when this sheet represents a whole tile — same message whose reaction chips and pin
    // state the tile actually displays.
    val group = groupMessages
    val groupRepresentative = group?.last() ?: message
    val actions = buildList {
        // Reply
        add(
            SheetAction(R.drawable.ic_reply_24dp, stringResource(R.string.reply)) {
                state.correcting.value = null
                state.replyingTo.value = message
            }
        )
        // Enters multi-select. For a single message that's immediate (checked right away, further
        // selection happens by tapping messages directly). For a grid tile it's ambiguous whether
        // "Select" means the whole batch or just some of it, so it defers to a small popup instead.
        add(
            SheetAction(R.drawable.ic_check_circle_24dp, stringResource(R.string.select)) {
                if (groupMessages != null) onSelectGroup(groupMessages) else onSelect(message)
            }
        )
        // Message privately — reach the sender of a group/channel message directly, without
        // going through the member list (which may not even be visible to non-moderators).
        // Deliberately offered for already-private messages too: replying at all (the plain
        // "Reply" action above) does NOT put the composer into private mode by itself — that's
        // governed solely by conversation.nextCounterpart at send time — so without this, a
        // reply to a whisper you received privately would go out to the whole room by default.
        val counterpart = message.counterpart
        if (conversation != null
            && conversation.getMode() == eu.siacs.conversations.entities.Conversational.MODE_MULTI
            && message.status == Message.STATUS_RECEIVED
            && message.type != Message.TYPE_STATUS
            && message.type != Message.TYPE_RTP_SESSION
            && !deleted
            && counterpart != null
            && !counterpart.resource.isNullOrEmpty()
            && conversation.mucOptions.allowPm()
        ) {
            add(
                SheetAction(
                    R.drawable.ic_person_24dp,
                    stringResource(R.string.send_private_message),
                ) {
                    listener.onPrivateMessage(message)
                }
            )
        }
        // Copy text
        val body = message.body
        if (!body.isNullOrBlank() && !message.isFileOrImage && !deleted) {
            add(
                SheetAction(
                    R.drawable.ic_description_24dp,
                    stringResource(android.R.string.copy),
                ) {
                    val clipboard =
                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("message", body)
                    )
                    android.widget.Toast.makeText(
                            context,
                            R.string.message_copied_to_clipboard,
                            android.widget.Toast.LENGTH_SHORT,
                        )
                        .show()
                }
            )
            // Copy link — first URL found in body
            val firstLink = de.gultsch.common.Linkify.getLinks(body).firstOrNull()
            if (firstLink != null) {
                val copyLinkLabel = when (firstLink.scheme) {
                    "xmpp" -> stringResource(R.string.copy_jabber_id)
                    "http", "https", "gemini" -> stringResource(R.string.copy_link)
                    "geo" -> stringResource(R.string.copy_geo_uri)
                    "tel" -> stringResource(R.string.copy_telephone_number)
                    "mailto" -> stringResource(R.string.copy_email_address)
                    else -> stringResource(R.string.copy_URI)
                }
                add(SheetAction(R.drawable.ic_link_24dp, copyLinkLabel) {
                    listener.onCopyLink(message)
                })
            }
        }
        // Correct/edit — allowed on any sent text message, not just the last one
        if (message.isEditable && !message.isFileOrImage && !deleted) {
            add(
                SheetAction(R.drawable.ic_edit_24dp, stringResource(R.string.correct_message)) {
                    val startEditing = {
                        state.replyingTo.value = null
                        state.correcting.value = message
                        state.setInput(message.body ?: "")
                        listener.onEditingStarted(message)
                    }
                    if (onboardingPrefs.hasSeenEditOnboarding) {
                        startEditing()
                    } else {
                        onNeedsOnboarding(OnboardingKind.EDIT, startEditing)
                    }
                }
            )
        }
        // Open file
        if (message.isFileOrImage && !deleted) {
            add(
                SheetAction(
                    R.drawable.ic_attach_file_24dp,
                    stringResource(R.string.open_x_file, fileDescription),
                ) {
                    listener.onOpenMessage(message)
                }
            )
        }
        // Download file (deleted locally but still on remote host)
        if (message.isFileOrImage && deleted && message.hasFileOnRemoteHost()) {
            add(
                SheetAction(
                    R.drawable.ic_download_24dp,
                    stringResource(R.string.download_x_file, fileDescription),
                ) {
                    listener.onDownloadMessage(message)
                }
            )
        } else if (!message.isFileOrImage && message.treatAsDownloadable()) {
            add(
                SheetAction(
                    R.drawable.ic_download_24dp,
                    stringResource(R.string.download_x_file, fileDescription),
                ) {
                    listener.onDownloadMessage(message)
                }
            )
        }
        // Copy URL (remote download URL for file attachments)
        if (message.encryption == Message.ENCRYPTION_NONE
            && (message.hasFileOnRemoteHost() || message.treatAsDownloadable())
        ) {
            add(SheetAction(R.drawable.ic_link_24dp, stringResource(R.string.copy_url)) {
                listener.onCopyUrl(message)
            })
        }
        // Save file to shared storage
        if (message.isFileOrImage && !deleted && !cancelable) {
            val path = message.getRelativeFilePath()
            if (path != null && !path.sharedStorage()) {
                add(SheetAction(R.drawable.ic_save_24dp, stringResource(R.string.save_file)) {
                    listener.onSaveFile(message)
                })
            }
        }
        // Share
        val shareable = (message.isFileOrImage && !deleted && !receiving)
                || (!message.isFileOrImage && !message.treatAsDownloadable() && transferable == null && !deleted)
        if (shareable) {
            add(SheetAction(R.drawable.ic_share_24dp, stringResource(R.string.share_with)) {
                listener.onShareMessage(message)
            })
            add(SheetAction(R.drawable.ic_forward_24dp, stringResource(R.string.forward_message)) {
                listener.onForwardMessage(message)
            })
        }
        // Add reaction
        if (groupRepresentative.status != Message.STATUS_SEND_FAILED
            && !groupRepresentative.isDeleted
            && Restrictions.reactionsPerUserRemaining(groupRepresentative)
        ) {
            add(
                SheetAction(R.drawable.ic_add_reaction_24dp, stringResource(R.string.add_reaction)) {
                    listener.onAddReaction(groupRepresentative)
                }
            )
        }
        // Send again (failed outgoing message) — STATUS_SEND_FAILED covers a user-initiated
        // cancel just as much as a genuine error (both set this same status, only the error
        // message differs), so this and the P2P retry below are already available after a
        // cancel too, with no separate handling needed for that case.
        if (message.status == Message.STATUS_SEND_FAILED && !deleted) {
            add(SheetAction(R.drawable.ic_refresh_24dp, stringResource(R.string.send_again)) {
                listener.onResendMessage(message)
            })
            // Retry directly peer-to-peer instead of via the server — only offered when there's
            // an actual choice to make: the file hasn't already reached the server, the
            // conversation is 1:1 with the peer currently online, and the account can normally
            // reach an HTTP upload service (i.e. server upload was a real alternative, not the
            // only option to begin with).
            val account = conversation?.getAccount()
            val connection = account?.getXmppConnection()
            val fileNotUploaded = message.isFileOrImage && !message.hasFileOnRemoteHost()
            val isPeerOnline = conversation != null &&
                conversation.getMode() == Conversational.MODE_SINGLE &&
                conversation.getContact().getPresences().isNotEmpty()
            val httpUploadAvailable = connection != null &&
                connection.getManager(eu.siacs.conversations.xmpp.manager.HttpUploadManager::class.java)
                    .getService() != null
            if (fileNotUploaded && isPeerOnline && httpUploadAvailable) {
                add(SheetAction(R.drawable.ic_p2p_24dp, stringResource(R.string.retry_with_p2p)) {
                    listener.onRetryAsP2P(message)
                })
            }
        }
        // Cancel in-progress upload/download
        if (cancelable) {
            add(SheetAction(R.drawable.ic_cancel_24dp, stringResource(R.string.cancel_transmission)) {
                listener.onCancelTransmission(message)
            })
        }
        // Pin / Unpin
        if (groupRepresentative.type != Message.TYPE_STATUS
            && groupRepresentative.type != Message.TYPE_RTP_SESSION
            && !groupRepresentative.isDeleted
        ) {
            if (groupRepresentative.isPinned) {
                add(SheetAction(R.drawable.ic_push_pin_24dp, stringResource(R.string.unpin_message)) {
                    listener.onUnpinMessage(groupRepresentative)
                })
            } else {
                add(SheetAction(R.drawable.ic_push_pin_24dp, stringResource(R.string.pin_message)) {
                    listener.onPinMessage(groupRepresentative)
                })
            }
        }
        // Select to delete — a whole grid tile only, hands off to the picker screen directly (no
        // "All Photos" popup first, since "Delete Files" right below already covers that case).
        if (group != null) {
            add(
                SheetAction(R.drawable.ic_check_circle_24dp, stringResource(R.string.select_to_delete)) {
                    onSelectToDelete(group)
                }
            )
        }
        // Delete — DeleteMessageSheet itself decides, per message, whether the "everyone" button
        // is a self-retraction or (for someone else's message, when we're a moderator) a XEP-0425
        // moderation request instead. See DeleteMessageSheet for that gating.
        //
        // When this sheet represents a whole grid tile (groupMessages != null), delete must act
        // on every message in the batch, not just the single representative message it was
        // opened with — a fixed "Delete files" label, not a per-type singular name that only
        // describes the one message this sheet happens to hold.
        val deleteLabel = when {
            group != null -> stringResource(R.string.delete_files)
            deleted -> stringResource(R.string.delete_leftover_message)
            message.isFileOrImage -> stringResource(R.string.delete_x_file, fileDescription)
            else -> stringResource(R.string.delete_message)
        }
        add(SheetAction(R.drawable.ic_delete_24dp, deleteLabel) {
            val startDelete = {
                if (group != null) onDeleteGroup(group) else state.deleteTarget.value = message
            }
            if (onboardingPrefs.hasSeenDeleteOnboarding) {
                startDelete()
            } else {
                onNeedsOnboarding(OnboardingKind.DELETE, startDelete)
            }
        })
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.message_options),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            // Show send-failure error detail at the top of the sheet. For a whole grid tile this
            // has to look at every message in the batch, not just whichever one was tapped to
            // open the sheet — a photo can fail while its neighbors succeed (each upload runs
            // independently; one failing never stops the rest), so the sheet groups failures by
            // identical error text and lists which photo numbers (1-based, batch order) hit each
            // one, rather than only ever surfacing the tapped photo's own status.
            if (group != null) {
                val failuresByError = group.withIndex()
                    .filter { (_, m) ->
                        m.status == Message.STATUS_SEND_FAILED &&
                            m.errorMessage != Message.ERROR_MESSAGE_CANCELLED &&
                            !m.errorMessage.isNullOrBlank()
                    }
                    .groupBy({ it.value.errorMessage!! }) { it.index + 1 }
                failuresByError.forEach { (error, photoNumbers) ->
                    ErrorBanner(
                        text = stringResource(
                            R.string.photo_n_failed,
                            photoNumbers.joinToString(", "),
                            error,
                        ),
                    )
                }
            } else {
                val errorMessage = if (message.status == Message.STATUS_SEND_FAILED) {
                    message.errorMessage
                } else null
                if (!errorMessage.isNullOrBlank()) {
                    ErrorBanner(text = errorMessage)
                }
            }
            actions.forEachIndexed { index, action ->
                val top = if (index == 0) CORNER_LARGE else CORNER_SMALL
                val bottom = if (index == actions.lastIndex) CORNER_LARGE else CORNER_SMALL
                Surface(
                    onClick = {
                        onDismiss()
                        action.onClick()
                    },
                    shape =
                        RoundedCornerShape(
                            topStart = top,
                            topEnd = top,
                            bottomStart = bottom,
                            bottomEnd = bottom,
                        ),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            painter = painterResource(action.iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(text = action.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// XEP-0424 retraction of a broadcast groupchat message must reference the room's own
// server-assigned stanza-id (XEP-0359) — the sender's local UUID means nothing to other
// occupants or the room's archive. That id only exists once the room has echoed this message
// back to us, which can lag a moment behind sending, or never happen at all if the room doesn't
// advertise urn:xmpp:sid:0. Offering (and silently no-op'ing) "delete for everyone" without it
// would wipe the local copy while doing nothing for anyone else — disabled until it's genuinely
// possible instead.
//
// A private message sent *within* a MUC is exempt from that requirement: it's relayed as a
// direct type='chat' stanza straight to the recipient (see MessageGenerator.generateRetraction),
// never reflected back through the room's own archive, so it never gets a serverMsgId — that
// generator already falls back to the message's own uuid for exactly this case, since nothing
// re-stamps a whisper's id in transit the way the room does for a real broadcast message. Without
// this carve-out, private messages could never be retracted at all.
internal fun isRetractable(message: Message): Boolean {
    val conversation = message.conversation as? Conversation
    val isMuc = conversation?.getMode() == Conversational.MODE_MULTI
    val isOwnMessage = message.status != Message.STATUS_RECEIVED
    return isOwnMessage && (!isMuc || message.isPrivateMessage() || message.serverMsgId != null)
}

// XEP-0425: a moderator can remove someone ELSE's message for everyone, even though they
// can't self-retract it. Applies in any MUC room the server advertises moderation support
// for — public channel or private group chat alike, since that's a real server capability
// check, not something tied to the room's privacy/anonymity settings. Owners/admins are
// supposed to always hold at least moderator role per XEP-0045, but OR in the affiliation
// directly (ranks(ADMIN) also covers OWNER) in case the client's tracked role lags.
internal fun isModeratable(message: Message): Boolean {
    val conversation = message.conversation as? Conversation
    val isMuc = conversation?.getMode() == Conversational.MODE_MULTI
    val isOwnMessage = message.status != Message.STATUS_RECEIVED
    val mucOptions = conversation?.mucOptions
    return !isOwnMessage
            && !message.isDeleted
            && message.status != Message.STATUS_SEND_FAILED
            && isMuc
            && mucOptions != null
            && mucOptions.moderation()
            && (mucOptions.self.ranks(im.conversations.android.xmpp.model.muc.Role.MODERATOR)
                || mucOptions.self.ranks(im.conversations.android.xmpp.model.muc.Affiliation.ADMIN))
            && message.serverMsgId != null
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteMessageSheet(
    message: Message,
    onDeleteForEveryone: () -> Unit,
    onDeleteForMyself: () -> Unit,
    onModerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canRetract = isRetractable(message)
    val canModerate = isModeratable(message)
    val moderateInstead = canModerate && !canRetract
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.delete_message_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            val everyoneLabel = if (moderateInstead) {
                stringResource(R.string.moderate_delete)
            } else {
                stringResource(R.string.delete_for_everyone)
            }
            val myselfLabel = stringResource(R.string.delete_for_myself)
            val cancelLabel = stringResource(R.string.cancel)
            val everyoneInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val myselfInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val cancelInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            // alpha21: overflowIndicator overload required; old one clips.
            // ButtonShapes, animateWidth, and ButtonDefaults.ContentPadding are @Composable in
            // alpha21 — they must live inside buttonGroupContent, not the outer ButtonGroup block.
            androidx.compose.material3.ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                customItem(
                    buttonGroupContent = {
                        androidx.compose.material3.Button(
                            onClick = { if (moderateInstead) onModerate() else onDeleteForEveryone() },
                            shapes = androidx.compose.material3.ButtonShapes(
                                shape = androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShape,
                                pressedShape = androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonPressShape,
                            ),
                            enabled = canRetract || canModerate,
                            interactionSource = everyoneInteractionSource,
                            modifier = Modifier.animateWidth(everyoneInteractionSource, androidx.compose.material3.ButtonDefaults.ContentPadding),
                        ) {
                            // The moderator label ("Delete as moderator" / "Удалить как модератор")
                            // runs longer than "Delete for everyone" in this same slim third-of-
                            // the-row button — allow it to wrap to a second line instead of
                            // truncating, rather than reflowing the whole sheet's layout for it.
                            Text(
                                text = everyoneLabel,
                                maxLines = if (moderateInstead) 2 else 1,
                                textAlign = TextAlign.Center,
                                style = if (moderateInstead) MaterialTheme.typography.labelMedium else LocalTextStyle.current,
                            )
                        }
                    },
                    menuContent = {},
                )
                customItem(
                    buttonGroupContent = {
                        androidx.compose.material3.Button(
                            onClick = onDeleteForMyself,
                            shapes = androidx.compose.material3.ButtonShapes(
                                shape = RoundedCornerShape(CORNER_SMALL),
                                pressedShape = androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonPressShape,
                            ),
                            interactionSource = myselfInteractionSource,
                            modifier = Modifier.animateWidth(myselfInteractionSource, androidx.compose.material3.ButtonDefaults.ContentPadding),
                        ) { Text(text = myselfLabel, maxLines = 1) }
                    },
                    menuContent = {},
                )
                customItem(
                    buttonGroupContent = {
                        androidx.compose.material3.Button(
                            onClick = onDismiss,
                            shapes = androidx.compose.material3.ButtonShapes(
                                shape = androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShape,
                                pressedShape = androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonPressShape,
                            ),
                            interactionSource = cancelInteractionSource,
                            modifier = Modifier.animateWidth(cancelInteractionSource, androidx.compose.material3.ButtonDefaults.ContentPadding),
                        ) { Text(text = cancelLabel, maxLines = 1) }
                    },
                    menuContent = {},
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Same shape as [DeleteMessageSheet] but for a whole grid tile's worth of messages at once.
 * "Everyone" only lights up when every message is uniformly eligible for the same path
 * (all self-retractable, or all moderatable) — a mixed batch only gets local delete, same
 * reasoning as the multi-select batch delete.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteGroupSheet(
    messages: List<Message>,
    onDeleteForEveryone: () -> Unit,
    onDeleteForMyself: () -> Unit,
    onModerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canRetract = messages.isNotEmpty() && messages.all(::isRetractable)
    val canModerate = messages.isNotEmpty() && messages.all(::isModeratable)
    val moderateInstead = canModerate && !canRetract
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.delete_files),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )
            val everyoneLabel = if (moderateInstead) {
                stringResource(R.string.moderate_delete)
            } else {
                stringResource(R.string.delete_for_everyone)
            }
            val myselfLabel = stringResource(R.string.delete_for_myself)
            val cancelLabel = stringResource(R.string.cancel)
            val everyoneInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val myselfInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val cancelInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            androidx.compose.material3.ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                customItem(
                    buttonGroupContent = {
                        androidx.compose.material3.Button(
                            onClick = { if (moderateInstead) onModerate() else onDeleteForEveryone() },
                            shapes = androidx.compose.material3.ButtonShapes(
                                shape = androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonShape,
                                pressedShape = androidx.compose.material3.ButtonGroupDefaults.connectedLeadingButtonPressShape,
                            ),
                            enabled = canRetract || canModerate,
                            interactionSource = everyoneInteractionSource,
                            modifier = Modifier.animateWidth(everyoneInteractionSource, androidx.compose.material3.ButtonDefaults.ContentPadding),
                        ) {
                            Text(text = everyoneLabel, maxLines = if (moderateInstead) 2 else 1)
                        }
                    },
                    menuContent = {},
                )
                customItem(
                    buttonGroupContent = {
                        androidx.compose.material3.Button(
                            onClick = onDeleteForMyself,
                            shapes = androidx.compose.material3.ButtonShapes(
                                shape = RoundedCornerShape(CORNER_SMALL),
                                pressedShape = androidx.compose.material3.ButtonGroupDefaults.connectedMiddleButtonPressShape,
                            ),
                            interactionSource = myselfInteractionSource,
                            modifier = Modifier.animateWidth(myselfInteractionSource, androidx.compose.material3.ButtonDefaults.ContentPadding),
                        ) { Text(text = myselfLabel, maxLines = 1) }
                    },
                    menuContent = {},
                )
                customItem(
                    buttonGroupContent = {
                        androidx.compose.material3.Button(
                            onClick = onDismiss,
                            shapes = androidx.compose.material3.ButtonShapes(
                                shape = androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonShape,
                                pressedShape = androidx.compose.material3.ButtonGroupDefaults.connectedTrailingButtonPressShape,
                            ),
                            interactionSource = cancelInteractionSource,
                            modifier = Modifier.animateWidth(cancelInteractionSource, androidx.compose.material3.ButtonDefaults.ContentPadding),
                        ) { Text(text = cancelLabel, maxLines = 1) }
                    },
                    menuContent = {},
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Small popup shown before "Select" actually enters selection mode on a grid tile — "All Photos"
 * selects the whole batch right away (same as tapping the tile's leading checkmark), "Select
 * Photos" opens [MediaSelectionActivity] to hand-pick a subset instead.
 */
@Composable
private fun SelectModePopupSheet(
    onAllPhotos: () -> Unit,
    onSelectPhotos: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.select),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 10.dp),
            )
            ExpressiveMenuItem(
                iconRes = R.drawable.ic_check_circle_24dp,
                label = stringResource(R.string.all_photos),
                onClick = onAllPhotos,
            )
            ExpressiveMenuItem(
                iconRes = R.drawable.ic_image_24dp,
                label = stringResource(R.string.select_photos),
                onClick = onSelectPhotos,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One-time (per 5-minute window) disclaimer before a moderator's delete reaches every occupant. */
@Composable
private fun ModerationDisclaimerDialog(
    onConfirm: (doNotShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var doNotShowAgain by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_message)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.moderation_explained),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { doNotShowAgain = !doNotShowAgain }
                        .padding(top = 16.dp),
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = { doNotShowAgain = it },
                    )
                    Text(
                        text = stringResource(R.string.do_not_show_this_warning_again),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(doNotShowAgain) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

/** Batch delete is local-only (same as the plain single-message "Delete for myself") — it never
 * attempts per-message retraction/moderation, since a mixed selection could have wildly different
 * eligibility per message. A confirmation is worth it here specifically because, unlike a single
 * delete, there's no per-item undo affordance once several go at once. */
@Composable
private fun DeleteSelectedMessagesDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.delete_n_messages, count, count)
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun ComposerBanner(state: ConversationScreenState, listener: ConversationScreenListener) {
    // Revision read keeps this banner in sync with nextCounterpart changes.
    val revision = state.revision.intValue
    val conversation = state.conversation.value
    val nextCounterpart = remember(conversation, revision) { conversation?.getNextCounterpart() }
    // "Private message" is a MUC-only concept, but nextCounterpart itself isn't — selectPresence()
    // (used e.g. by the P2P retry flow) sets the exact same field on a plain 1:1 conversation just
    // to target a specific device/resource, which is a normal multi-resource-presence thing, not
    // anything resembling a private message. Showing this banner for that case claimed the app was
    // "sending a private message" inside a completely ordinary 1:1 chat — misleading, and the X
    // button offered to "cancel" something that was never really that in the first place.
    val isMuc = conversation?.getMode() == Conversational.MODE_MULTI
    if (nextCounterpart != null && isMuc) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock_open_outline_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text =
                    stringResource(
                        R.string.send_private_message_to,
                        nextCounterpart.resource ?: "",
                    ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    conversation?.setNextCounterpart(null)
                    state.revision.intValue++
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_24dp),
                    contentDescription = stringResource(R.string.cancel),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
    val replyingTo = state.replyingTo.value
    val correcting = state.correcting.value
    if (replyingTo == null && correcting == null) return
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
    ) {
        Icon(
            painter =
                painterResource(
                    if (correcting != null) R.drawable.ic_edit_24dp else R.drawable.ic_reply_24dp
                ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (correcting != null) stringResource(R.string.send_corrected_message)
                    else UIHelper.getMessageDisplayName(replyingTo!!),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = MessageUtils.replyPreview(correcting ?: replyingTo!!),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (replyingTo != null && !replyingTo.isDeleted) {
            val isVisualMedia = replyingTo.type == Message.TYPE_IMAGE ||
                ((replyingTo.type == Message.TYPE_FILE || replyingTo.type == Message.TYPE_PRIVATE_FILE) &&
                    replyingTo.getMimeType()?.startsWith("video/") == true)
            if (isVisualMedia) {
                val fileBackend = (context as? XmppActivity)?.xmppConnectionService?.fileBackend
                if (fileBackend != null) {
                    val thumb = remember(replyingTo.getUuid()) { mutableStateOf<ImageBitmap?>(null) }
                    val sizePx = with(LocalDensity.current) { 36.dp.toPx() }.toInt()
                    LaunchedEffect(replyingTo.getUuid()) {
                        val bm = withContext(Dispatchers.IO) {
                            try { fileBackend.getThumbnail(replyingTo, sizePx, false) }
                            catch (_: Exception) { null }
                        }
                        if (bm != null) thumb.value = bm.asImageBitmap()
                    }
                    val bitmap = thumb.value
                    if (bitmap != null) {
                        Spacer(Modifier.width(8.dp))
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = {
                val wasEditing = state.correcting.value
                if (wasEditing != null) {
                    state.setInput("")
                    listener.onEditingStopped(wasEditing)
                }
                state.replyingTo.value = null
                state.correcting.value = null
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_close_24dp),
                contentDescription = stringResource(R.string.cancel),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Staged attachments waiting for the send button, with per-item remove. */
@Composable
private fun AttachmentPreviewStrip(state: ConversationScreenState) {
    val context = LocalContext.current
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        itemsIndexed(
            state.attachments,
            key = { _, attachment -> attachment.uuid.toString() },
        ) { _, attachment ->
            Box {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(72.dp),
                ) {
                    val isImage =
                        attachment.type == eu.siacs.conversations.ui.util.Attachment.Type.IMAGE
                    if (isImage) {
                        val thumb =
                            remember(attachment.uuid) { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(attachment.uuid) {
                            val bm =
                                withContext(Dispatchers.IO) {
                                    try {
                                        context.contentResolver.loadThumbnail(
                                            attachment.uri,
                                            android.util.Size(144, 144),
                                            null,
                                        )
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            if (bm != null) thumb.value = bm.asImageBitmap()
                        }
                        val bitmap = thumb.value
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (attachment.type ==
                                            eu.siacs.conversations.ui.util.Attachment.Type
                                                .RECORDING
                                        )
                                            R.drawable.ic_mic_24dp
                                        else R.drawable.ic_attach_file_24dp
                                    ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Surface(
                    onClick = { state.attachments.remove(attachment) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close_24dp),
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkifiedMessageText(
    message: Message,
    revision: Int,
    contentColor: androidx.compose.ui.graphics.Color,
    onLongPress: () -> Unit,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val xmppBg = MaterialTheme.colorScheme.tertiaryContainer
    val xmppFg = MaterialTheme.colorScheme.onTertiaryContainer
    val context = LocalContext.current
    val msgKey = message.getUuid() ?: System.identityHashCode(message)
    val annotated = remember(msgKey, revision, contentColor.value, linkColor.value, xmppBg.value, xmppFg.value) {
        buildAnnotatedBody(context, message, contentColor, linkColor, xmppBg, xmppFg)
    }
    androidx.compose.foundation.text.BasicText(
        text = annotated,
        style = androidx.compose.ui.text.TextStyle(
            fontSize = 16.sp,
            color = contentColor,
        ),
        modifier = Modifier.pointerInput(onLongPress) {
            detectTapGestures(onLongPress = { onLongPress() })
        },
    )
}

private fun buildAnnotatedBody(
    context: android.content.Context,
    message: Message,
    contentColor: androidx.compose.ui.graphics.Color,
    linkColor: androidx.compose.ui.graphics.Color,
    xmppBg: androidx.compose.ui.graphics.Color,
    xmppFg: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.text.AnnotatedString {
    val rawBody = message.body?.trim() ?: ""
    return try {
        val body = android.text.SpannableStringBuilder()
        val displayName = eu.siacs.conversations.utils.UIHelper.getMessageDisplayName(message)
        if (message.hasMeCommand() && displayName != null) {
            val replaced = displayName + " " + rawBody.removePrefix(eu.siacs.conversations.entities.Message.ME_COMMAND)
            body.append(replaced)
            body.setSpan(
                android.text.style.StyleSpan(android.graphics.Typeface.BOLD_ITALIC),
                0, displayName.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        } else {
            // The "whispered"/"to X" label lives only in MessageFooter now — shown there for
            // every private message (text and file/image alike) instead of inline here, so it
            // doesn't crowd the message text itself.
            body.append(rawBody)
        }
        val emojiMatcher = eu.siacs.conversations.utils.Emoticons.getEmojiPattern(body).matcher(body)
        while (emojiMatcher.find()) {
            if (emojiMatcher.start() < emojiMatcher.end()) {
                body.setSpan(
                    android.text.style.RelativeSizeSpan(1.2f),
                    emojiMatcher.start(), emojiMatcher.end(),
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        if (message.conversation.getMode() == eu.siacs.conversations.entities.Conversational.MODE_MULTI
            && message.status == eu.siacs.conversations.entities.Message.STATUS_RECEIVED
            && message.conversation is eu.siacs.conversations.entities.Conversation
        ) {
            val conv = message.conversation as eu.siacs.conversations.entities.Conversation
            val nick = conv.mucOptions?.getActualNick()
            if (!nick.isNullOrEmpty()) {
                val pattern = eu.siacs.conversations.services.NotificationService
                    .generateNickHighlightPattern(nick)
                val matcher = pattern.matcher(body)
                while (matcher.find()) {
                    body.setSpan(
                        android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                        matcher.start(), matcher.end(),
                        android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }
        eu.siacs.conversations.utils.StylingHelper.format(body, contentColor.toArgb())
        de.gultsch.common.Linkify.addLinks(body)
        spannableToAnnotated(body, linkColor, xmppBg, xmppFg, context)
    } catch (_: Exception) {
        androidx.compose.ui.text.AnnotatedString(rawBody)
    }
}

private fun spannableToAnnotated(
    spannable: android.text.SpannableStringBuilder,
    linkColor: androidx.compose.ui.graphics.Color,
    xmppBg: androidx.compose.ui.graphics.Color,
    xmppFg: androidx.compose.ui.graphics.Color,
    context: android.content.Context,
): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        append(spannable.toString())
        for (span in spannable.getSpans(0, spannable.length, Any::class.java)) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start < 0 || end < 0 || start >= end) continue
            when (span) {
                is android.text.style.StyleSpan -> {
                    val style = when (span.style) {
                        android.graphics.Typeface.BOLD ->
                            androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        android.graphics.Typeface.ITALIC ->
                            androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)
                        android.graphics.Typeface.BOLD_ITALIC ->
                            androidx.compose.ui.text.SpanStyle(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontStyle = FontStyle.Italic,
                            )
                        else -> null
                    }
                    if (style != null) addStyle(style, start, end)
                }
                is android.text.style.StrikethroughSpan ->
                    addStyle(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough), start, end)
                is android.text.style.TypefaceSpan ->
                    if (span.family == "monospace") addStyle(
                        androidx.compose.ui.text.SpanStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        start, end,
                    )
                is android.text.style.ForegroundColorSpan ->
                    addStyle(androidx.compose.ui.text.SpanStyle(color = androidx.compose.ui.graphics.Color(span.foregroundColor)), start, end)
                is android.text.style.RelativeSizeSpan ->
                    addStyle(androidx.compose.ui.text.SpanStyle(fontSize = (16f * span.sizeChange).sp), start, end)
                is android.text.style.URLSpan -> {
                    val url = span.url
                    val isXmpp = url.startsWith("xmpp:")
                    val baseStyle = if (isXmpp) {
                        // XMPP URIs get chip-like styling: tertiary container background
                        // signals "this opens inside the app" vs a browser link.
                        androidx.compose.ui.text.SpanStyle(
                            color = xmppFg,
                            background = xmppBg,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        androidx.compose.ui.text.SpanStyle(
                            color = linkColor,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        )
                    }
                    addStyle(baseStyle, start, end)
                    addLink(
                        androidx.compose.ui.text.LinkAnnotation.Clickable(
                            tag = url,
                            styles = androidx.compose.ui.text.TextLinkStyles(style = baseStyle),
                            linkInteractionListener = { openUrl(context, url) },
                        ),
                        start, end,
                    )
                }
            }
        }
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    val uri = try {
        de.gultsch.common.MiniUri.asMiniUri(url)
    } catch (_: Exception) {
        return
    }
    val asXmpp = when {
        uri is de.gultsch.common.MiniUri.Xmpp -> uri
        uri is de.gultsch.common.MiniUri.Transformable && uri.transform() is de.gultsch.common.MiniUri.Xmpp ->
            uri.transform() as de.gultsch.common.MiniUri.Xmpp
        else -> null
    }
    if (asXmpp != null && asXmpp.isAddress) {
        if (context is ConversationsActivity && context.onXmppUriClicked(asXmpp)) return
        try {
            context.startActivity(
                android.content.Intent(context, eu.siacs.conversations.ui.YuriLauncherActivity::class.java)
                    .apply { data = asXmpp.asUri() }
            )
        } catch (_: android.content.ActivityNotFoundException) {}
        return
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
    if ("web+ap" == uri.scheme) {
        if (intent.resolveActivity(context.packageManager) == null) {
            val https = android.net.Uri.parse(
                "https://${uri.authority}/${com.google.common.base.Joiner.on('/').join(uri.pathSegments)}"
            )
            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, https)) } catch (_: Exception) {}
            return
        }
    }
    if ("geo" == uri.scheme) intent.setClass(context, eu.siacs.conversations.ui.ShowLocationActivity::class.java)
    else intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT
    try {
        context.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, eu.siacs.conversations.R.string.no_application_found_to_open_link, android.widget.Toast.LENGTH_SHORT).show()
    }
}


@Composable
private fun RecordingBar(
    recording: RecordingUiState.Active,
    listener: ConversationScreenListener,
) {
    val minutes = (recording.elapsedMs / 60000).toInt()
    val seconds = ((recording.elapsedMs % 60000) / 1000).toInt()
    val timerText = String.format("%02d:%02d", minutes, seconds)
    val infiniteTransition = rememberInfiniteTransition(label = "recDot")
    val dotAlpha by if (!recording.paused) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "recDotAlpha",
        )
    } else {
        androidx.compose.runtime.remember { mutableStateOf(0.2f) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(48.dp),
    ) {
        IconButton(onClick = { listener.onCancelRecording() }) {
            Icon(
                painter = painterResource(R.drawable.ic_delete_24dp),
                contentDescription = stringResource(R.string.cancel),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier =
                Modifier.size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = dotAlpha))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = timerText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { listener.onPauseRecording() }) {
            Icon(
                painter =
                    painterResource(
                        if (recording.paused) R.drawable.ic_play_arrow_24dp
                        else R.drawable.ic_pause_24dp
                    ),
                contentDescription = null,
            )
        }
        FilledIconButton(
            onClick = { listener.onSendRecording() },
            shape = RoundedCornerShape(16.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_send_24dp),
                contentDescription = stringResource(R.string.send_message),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun InputBar(state: ConversationScreenState, listener: ConversationScreenListener) {
    val context = LocalContext.current
    val conversation = state.conversation.value
    val revision = state.revision.intValue

    val isReadOnlyChannel = remember(conversation, revision) {
        try {
            // A private-message target (set by "Send private message" on a received message —
            // available to any occupant the room's allowpm setting permits, independent of their
            // own participating() rank) must win over the read-only placeholder, or tapping that
            // action in a moderated/announcement channel visibly does nothing: nextCounterpart
            // gets set correctly, but this bar would still show "read only channel" and never
            // render the composer/banner that's supposed to reflect it. Mirrors the priority
            // order the old ConversationFragment.updateChatMsgHint() already used.
            conversation != null &&
                conversation.getNextCounterpart() == null &&
                conversation.getMode() == Conversational.MODE_MULTI &&
                !conversation.mucOptions.participating()
        } catch (_: Exception) {
            false
        }
    }

    if (isReadOnlyChannel) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lock_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.read_only_channel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }

    var attachMenuOpen by remember { mutableStateOf(false) }
    val text = state.inputText.value
    val hasText = text.isNotBlank()
    val correcting = state.correcting.value != null
    val hasAttachments = state.attachments.isNotEmpty()
    val recording = state.recordingState.value
    // Only the coarse "are we recording at all" flag drives the transition below — using the
    // full RecordingUiState.Active (elapsedMs ticks every timer tick) as AnimatedContent's
    // targetState made it compare unequal on every tick, re-triggering the enter/exit
    // transition constantly (visible as the whole bar blinking). The live timer value is read
    // separately inside the content lambda so it recomposes normally without retriggering it.
    var lastActiveRecording by remember { mutableStateOf<RecordingUiState.Active?>(null) }
    if (recording is RecordingUiState.Active) {
        lastActiveRecording = recording
    }

    // SharedTransitionLayout wraps the whole bar so the attach (paperclip) icon can share
    // identity between its collapsed toggle position and its slot in the expanded toolbar
    // below, instead of the two independently fading in/out as unrelated icons.
    SharedTransitionLayout {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
        ComposerBanner(state, listener)
        if (hasAttachments) {
            AttachmentPreviewStrip(state)
        }
        // Mic → recording bar container transform. Fade is handled manually per-branch (see
        // below) instead of AnimatedContent's built-in fade, so the composer row's crossfade
        // can be synced to the mic icon's own rotation progress; only the width interpolation
        // stays automatic, keeping the M3E DefaultSpatial token (380f/0.8f).
        AnimatedContent(
            targetState = recording is RecordingUiState.Active,
            transitionSpec = {
                (EnterTransition.None togetherWith ExitTransition.None).using(
                    SizeTransform(clip = false) { _, _ ->
                        spring(stiffness = 380f, dampingRatio = 0.8f)
                    }
                )
            },
            label = "micToRecordingTransform",
        ) { isRecording ->
        if (isRecording) {
            // Simple fade, unrelated to any rotation.
            val barAlpha by
                this.transition.animateFloat(
                    label = "recordingBarAlpha",
                    transitionSpec = { spring(stiffness = 380f, dampingRatio = 0.8f) },
                ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
            lastActiveRecording?.let {
                Box(modifier = Modifier.graphicsLayer { alpha = barAlpha }) {
                    RecordingBar(it, listener)
                }
            }
        } else {
        // Captured here, before nested AnimatedContents shadow `this`. The mic icon rotates
        // away when this row exits (recording starting) and rotates into place when it enters
        // (recording ending/cancelled) — symmetric with the mic's own text-morph rotation. The
        // row's own crossfade is synced to that rotation's progress instead of a plain fade, so
        // the turn is actually visible (held opaque/hidden until ~90% done) rather than getting
        // faded out from under it.
        val recordingRowTransition = this.transition
        val recordingRotation by
            recordingRowTransition.animateFloat(
                label = "recordingRotation",
                transitionSpec = { spring(stiffness = 380f, dampingRatio = 0.8f) },
            ) { state -> if (state == EnterExitState.Visible) 0f else 90f }
        val isRowExiting = recordingRowTransition.targetState == EnterExitState.PostExit
        val recordingRotationProgress = (recordingRotation / 90f).coerceIn(0f, 1f)
        val rowHoldUntil = 0.9f
        val rowAlpha =
            if (isRowExiting) {
                // Stay visible while it spins away; only fade in the final stretch so the
                // motion isn't cut off by the disappearance.
                if (recordingRotationProgress < rowHoldUntil) 1f
                else 1f - (recordingRotationProgress - rowHoldUntil) / (1f - rowHoldUntil)
            } else {
                // Mirror image: reveal quickly at the start, then stay visible for the rest
                // of the turn so you actually watch it spin into place, instead of it staying
                // hidden until the spin is basically already done.
                val arrived = 1f - recordingRotationProgress
                val revealBy = 1f - rowHoldUntil
                (arrived / revealBy).coerceIn(0f, 1f)
            }
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = rowAlpha },
        ) {
            // Attach button → toolbar container transform.
            // AnimatedContent + SizeTransform give a spring-driven horizontal expand from the
            // button position: the button's container grows rightward into a pill-shaped
            // toolbar with M3 Expressive spring physics, then shrinks back on dismiss.
            AnimatedContent(
                targetState = attachMenuOpen,
                transitionSpec = {
                    // M3 Expressive DefaultSpatial spring (380f/0.8f) throughout.
                    ContentTransform(
                        targetContentEnter =
                            expandHorizontally(
                                expandFrom = Alignment.Start,
                                animationSpec = spring(stiffness = 380f, dampingRatio = 0.8f),
                            ) + fadeIn(),
                        initialContentExit =
                            shrinkHorizontally(
                                shrinkTowards = Alignment.Start,
                                animationSpec = spring(stiffness = 380f, dampingRatio = 0.8f),
                            ) + fadeOut(),
                        sizeTransform =
                            SizeTransform(clip = false) { _, _ ->
                                spring(stiffness = 380f, dampingRatio = 0.8f)
                            },
                    )
                },
                label = "attachTransform",
            ) { isOpen ->
                if (isOpen) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.height(48.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    attachMenuOpen = false
                                    listener.onAttachImage()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_image_24dp),
                                    contentDescription =
                                        stringResource(R.string.attachment_choice_gallery),
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachMenuOpen = false
                                    listener.onTakePhoto()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_camera_alt_24dp),
                                    contentDescription =
                                        stringResource(R.string.attachment_choice_camera),
                                )
                            }
                            IconButton(
                                onClick = {
                                    attachMenuOpen = false
                                    listener.onAttachFile()
                                }
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_attach_file_24dp),
                                    contentDescription =
                                        stringResource(R.string.attachment_choice_file),
                                    modifier =
                                        Modifier.sharedElement(
                                            rememberSharedContentState(key = "attach_paperclip"),
                                            animatedVisibilityScope = this@AnimatedContent,
                                        ),
                                )
                            }
                            IconButton(onClick = { attachMenuOpen = false }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close_24dp),
                                    contentDescription = stringResource(R.string.cancel),
                                )
                            }
                        }
                    }
                } else {
                    IconButton(onClick = { attachMenuOpen = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_attach_file_24dp),
                            contentDescription = stringResource(R.string.attach_file),
                            modifier =
                                Modifier.sharedElement(
                                    rememberSharedContentState(key = "attach_paperclip"),
                                    animatedVisibilityScope = this@AnimatedContent,
                                ),
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            ) {
              Box {
                // Native EditText instead of BasicTextField: autocorrect-revert on some IMEs
                // (reported: Samsung Keyboard) was landing edits in the wrong place against
                // Compose's plain-String text field, producing duplicated words. EditText's
                // Editable/InputConnection is the same mature, decades-tested implementation
                // every other Android messenger's chat input uses, so IME edit commands
                // (autocorrect, autocorrect-revert, composing regions) apply correctly.
                val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
                val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
                val hintText =
                    if (correcting) stringResource(R.string.send_corrected_message)
                    else conversation?.let { UIHelper.getMessageHint(context, it) }
                        ?: stringResource(R.string.send_message)
                // Placeholder text lives here instead of EditText's native hint: the hint shares
                // the field's own maxLines (6) and wraps to a second line under width pressure —
                // always a wrapping risk on its own, and it also fought the attach-button shared-
                // element transition's bounds animation when it happened mid-transition: the row
                // would grow taller, snap the paperclip out of its animated position with no
                // transition of its own, then spring back, 2-3 times in a row. Single-line +
                // ellipsis here means the field's width can never affect its height, tap or not.
                if (text.isEmpty()) {
                    Text(
                        text = hintText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                AndroidView(
                    factory = { ctx ->
                        EditText(ctx).apply {
                            background = null
                            setPadding(0, 0, 0, 0)
                            maxLines = 6
                            inputType = InputType.TYPE_CLASS_TEXT or
                                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            textSize = 16f
                            var lastText = ""
                            var programmatic = false
                            // Index of the space we auto-inserted after a period, if the very
                            // next keystroke is still pending — lets us tell "typed a real
                            // sentence" from "typed another dot" (see below).
                            var autoSpaceIndex: Int? = null
                            addTextChangedListener(object : TextWatcher {
                                override fun beforeTextChanged(
                                    s: CharSequence?, start: Int, count: Int, after: Int,
                                ) = Unit
                                override fun onTextChanged(
                                    s: CharSequence?, start: Int, before: Int, count: Int,
                                ) = Unit
                                override fun afterTextChanged(s: Editable?) {
                                    if (programmatic || s == null) return
                                    val newText = s.toString()
                                    // Same "auto-space after a typed period" heuristic as
                                    // before, but applied directly on the live Editable —
                                    // afterTextChanged fires after IME composing regions
                                    // actually settle, so this now fires reliably per
                                    // keystroke regardless of keyboard/language.
                                    val typedDot =
                                        newText.length == lastText.length + 1 &&
                                            newText == "$lastText."
                                    if (typedDot) {
                                        val pendingAutoSpace = autoSpaceIndex
                                        if (pendingAutoSpace != null &&
                                            pendingAutoSpace == lastText.length - 1 &&
                                            lastText.getOrNull(pendingAutoSpace) == ' '
                                        ) {
                                            // A dot right after a space we just auto-inserted
                                            // means the user is typing "...", not a new sentence
                                            // — collapse the space so the dots stay together.
                                            programmatic = true
                                            s.delete(pendingAutoSpace, pendingAutoSpace + 1)
                                            programmatic = false
                                            autoSpaceIndex = null
                                        } else {
                                            val charBefore = lastText.lastOrNull()
                                            if (charBefore != null && charBefore.isLetter()) {
                                                programmatic = true
                                                s.insert(newText.length, " ")
                                                programmatic = false
                                                autoSpaceIndex = newText.length
                                            } else {
                                                autoSpaceIndex = null
                                            }
                                        }
                                    } else {
                                        autoSpaceIndex = null
                                    }
                                    lastText = s.toString()
                                    state.setInput(lastText)
                                    listener.onInputChanged(lastText)
                                }
                            })
                        }
                    },
                    update = { editText ->
                        editText.setTextColor(onSurfaceColor)
                        if (editText.text?.toString() != text) {
                            editText.setText(text)
                            editText.setSelection((editText.text?.length ?: 0))
                        }
                        // setTextCursorDrawable exists since API 29; minSdk is 33, always available.
                        val cursorWidthPx = (2 * editText.resources.displayMetrics.density).toInt()
                        editText.textCursorDrawable = GradientDrawable().apply {
                            setColor(primaryColor)
                            setSize(cursorWidthPx, -1)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                )
              }
            }

            // Morphing send button: rounded square at rest, springs to a pill once there
            // is something to send. M3 Expressive DefaultSpatial spring (380f/0.8f).
            val corner by
                androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (hasText) 24.dp else 14.dp,
                    animationSpec =
                        androidx.compose.animation.core.spring(
                            stiffness = 380f,
                            dampingRatio = 0.8f,
                        ),
                    label = "sendCorner",
                )
            val showMic = !hasText && !correcting && !hasAttachments
            FilledIconButton(
                onClick = {
                    when {
                        hasAttachments -> listener.onCommitAttachments()
                        showMic -> listener.onStartRecording()
                        hasText -> listener.onSendTextMessage(text.trim())
                    }
                },
                enabled = showMic || hasText || hasAttachments,
                shape = RoundedCornerShape(corner),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor =
                            if (showMic) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primary,
                        contentColor =
                            if (showMic) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier = Modifier.size(48.dp),
            ) {
                // Always the plain round send icon, even when the conversation is encrypted —
                // OMEMO still applies to the message, only the icon no longer changes for it.
                val iconRes = when {
                    showMic -> R.drawable.ic_mic_24dp
                    correcting -> R.drawable.ic_done_24dp
                    else -> R.drawable.ic_send_24dp
                }
                // Only the mic icon ever turns; send/done just fade, no rotation. Exiting mic
                // (typing starts) keeps the unchanged M3 Expressive DefaultSpatial spring;
                // entering mic (text cleared, i.e. send → mic) uses a slower, high-bouncy
                // preset spring so that turn is clearly visible as it settles into place.
                AnimatedContent(
                    targetState = iconRes,
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    label = "micSendIconMorph",
                ) { targetIcon ->
                    val isExiting = targetIcon != iconRes
                    val isMic = targetIcon == R.drawable.ic_mic_24dp
                    val rotation by
                        this.transition.animateFloat(
                            label = "iconRotation",
                            transitionSpec = {
                                if (isMic && !isExiting) {
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    )
                                } else {
                                    spring(stiffness = 380f, dampingRatio = 0.8f)
                                }
                            },
                        ) { state ->
                            if (!isMic) {
                                0f
                            } else if (isExiting) {
                                if (state == EnterExitState.PostExit) 90f else 0f
                            } else {
                                // Starts at +90 (to the right) and decreases to 0 — rotationZ is
                                // positive-clockwise, so a decreasing value here is what actually
                                // produces counter-clockwise motion, mirroring the mic's own
                                // clockwise exit.
                                if (state == EnterExitState.PreEnter) 90f else 0f
                            }
                        }
                    // Plain, immediate fade for send/done, which never rotate.
                    val plainAlpha by
                        this.transition.animateFloat(
                            label = "iconPlainAlpha",
                            transitionSpec = { tween(durationMillis = 200) },
                        ) { state ->
                            if (isExiting) {
                                if (state == EnterExitState.PostExit) 0f else 1f
                            } else {
                                if (state == EnterExitState.PreEnter) 0f else 1f
                            }
                        }
                    // Mic's own fade is driven by its own live rotation progress rather than a
                    // guessed delay — a spring has no fixed duration, so a timer can't reliably
                    // track when it settles. Exiting: stay visible while it spins away, only
                    // fade in the final stretch. Entering: mirror image — reveal quickly at the
                    // start, then stay visible for the rest so the turn into place is actually
                    // watchable, instead of staying hidden until the spin is basically done.
                    val holdUntil = 0.9f
                    val iconAlpha =
                        if (!isMic) {
                            plainAlpha
                        } else if (isExiting) {
                            val progress = (rotation / 90f).coerceIn(0f, 1f)
                            if (progress < holdUntil) 1f
                            else 1f - (progress - holdUntil) / (1f - holdUntil)
                        } else {
                            val arrived = 1f - (rotation / 90f).coerceIn(0f, 1f)
                            val revealBy = 1f - holdUntil
                            (arrived / revealBy).coerceIn(0f, 1f)
                        }
                    Icon(
                        painter = painterResource(targetIcon),
                        contentDescription =
                            stringResource(
                                if (targetIcon == R.drawable.ic_mic_24dp)
                                    R.string.attachment_choice_recording
                                else R.string.send_message
                            ),
                        modifier =
                            Modifier.graphicsLayer {
                                rotationZ = rotation + recordingRotation
                                alpha = iconAlpha
                            },
                    )
                }
            }
        }
        } // end else (not recording)
        } // end AnimatedContent content
        }
    }
    } // end SharedTransitionLayout
}
