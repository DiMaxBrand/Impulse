package eu.siacs.conversations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.ui.glass.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun rememberReplyStatus(conversation: Conversation?, own: Boolean): ReplyStatuses.Status? {
    val context = LocalContext.current
    if (conversation == null || conversation.getMode() != 0) return null
    val account = conversation.getAccount().getUuid() ?: return null
    val status = ReplyStatuses.get(context, account, conversation.getAddress().asBareJid().toString(), own)
    var now by remember(status?.stamp) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status?.stamp) {
        now = System.currentTimeMillis()
        if (status != null && status.expires > now) {
            delay(status.expires - now + 1)
            now = System.currentTimeMillis()
        }
    }
    return status?.takeIf { it.active(now) }
}

@Composable
private fun statusLabel(code: String) = stringResource(when (code) {
    "working" -> R.string.reply_status_working
    "checking" -> R.string.reply_status_checking
    else -> R.string.reply_status_waiting
})

@Composable
internal fun ReplyStatusText(code: String, modifier: Modifier = Modifier) {
    val time = if (AppearanceState.fullEffects) {
        val phase = rememberInfiniteTransition(label = "replyStatusDots")
        phase.animateFloat(0f, 6.283185f,
            infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "dots").value
    } else 0f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(statusLabel(code), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(4.dp))
        repeat(3) { index ->
            Text(".", Modifier.graphicsLayer {
                translationY = if (AppearanceState.fullEffects) -2.dp.toPx() *
                    sin(time - index * 0.9f).coerceAtLeast(0f) else 0f
            }, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun ReplyStatusComposerChip(state: ConversationScreenState, listener: ConversationScreenListener) {
    val conversation = state.conversation.value ?: return
    if (conversation.getMode() != 0) return
    val status = rememberReplyStatus(conversation, true)
    val hint = state.inputText.value == "..." && state.correcting.value == null
    if (status == null && !hint) return
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            .onGloballyPositioned { state.replyStatusAnchor.value = it.boundsInWindow() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { state.replyStatusPickerVisible.value = true }) {
                if (status != null) ReplyStatusText(status.code)
                else Text(stringResource(R.string.reply_status_set))
                Icon(painterResource(R.drawable.ic_expand_more_24dp), null)
            }
            if (status != null) IconButton(onClick = { listener.onSetReplyStatus("clear", true) }) {
                Icon(painterResource(R.drawable.ic_close_24dp), stringResource(R.string.reply_status_clear))
            }
        }
    }
}

@Composable
internal fun ReplyStatusChoices(state: ConversationScreenState, listener: ConversationScreenListener) {
    val current = rememberReplyStatus(state.conversation.value, true)
    var clear by remember { mutableStateOf(current?.clearOnSend ?: true) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_schedule_24dp), null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.reply_status_title), style = MaterialTheme.typography.titleLarge)
        }
        listOf("working", "checking", "waiting").forEach { code ->
            TextButton(onClick = { listener.onSetReplyStatus(code, clear) }, modifier = Modifier.fillMaxWidth()) {
                ReplyStatusText(code)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reply_status_clear_on_send), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium)
            Switch(checked = clear, onCheckedChange = { clear = it })
        }
        Text(stringResource(R.string.reply_status_expiry), style = MaterialTheme.typography.bodySmall)
        if (current != null) TextButton(onClick = { listener.onSetReplyStatus("clear", true) }) {
            Text(stringResource(R.string.reply_status_clear))
        }
    }
}

/** Material expands the selected attachment entry; glass is a separate non-merging upper plane. */
@Composable
internal fun ReplyStatusOverlay(state: ConversationScreenState, listener: ConversationScreenListener, glass: GlassStack?) {
    val open = state.replyStatusPickerVisible.value
    val p = remember { Animatable(0f) }
    LaunchedEffect(open, AppearanceState.fullEffects) {
        if (AppearanceState.fullEffects) p.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 380f))
        else p.snapTo(if (open) 1f else 0f)
    }
    BackHandler(enabled = open) { state.replyStatusPickerVisible.value = false }
    if (!open && p.value < 0.001f) return
    val density = LocalDensity.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }
        .clickable { state.replyStatusPickerVisible.value = false }) {
        val margin = with(density) { 8.dp.toPx() }
        val width = with(density) { minOf(maxWidth - 16.dp, 330.dp).toPx() }
        val height = with(density) { 340.dp.toPx() }.coerceAtMost(constraints.maxHeight - margin * 2)
        val anchor = state.replyStatusAnchor.value.translate(-origin)
        val fallback = Rect(margin, constraints.maxHeight - height - margin, margin + width, constraints.maxHeight - margin)
        val target = if (glass == null && state.attachCardOpen.value && !state.replyStatusContainer.value.isEmpty) {
            val container = state.replyStatusContainer.value.translate(-origin)
            val top = (container.bottom - height).coerceAtLeast(margin)
            Rect(container.left, top, container.right, top + height)
        } else {
                val top = if (glass != null && anchor.bottom + height + margin < constraints.maxHeight)
                    anchor.bottom + margin else (anchor.top - height - margin).coerceAtLeast(margin)
                Rect(anchor.left.coerceIn(margin, (constraints.maxWidth - width - margin).coerceAtLeast(margin)),
                    top, anchor.left.coerceIn(margin, (constraints.maxWidth - width - margin).coerceAtLeast(margin)) + width, top + height)
            }
        val to = if (anchor.isEmpty) fallback else target
        val from = if (anchor.isEmpty) Rect(to.left, to.bottom - 48.dp.value * density.density, to.left + width, to.bottom) else anchor
        fun mix(a: Float, b: Float) = a + (b - a) * p.value
        val modifier = Modifier.offset { IntOffset(mix(from.left, to.left).roundToInt(), mix(from.top, to.top).roundToInt()) }
            .size(with(density) { mix(from.width, to.width).coerceAtLeast(1f).toDp() },
                with(density) { mix(from.height, to.height).coerceAtLeast(1f).toDp() })
            .clickable(enabled = false) {}
        val content: @Composable () -> Unit = {
            Box(Modifier.requiredSize(with(density) { to.width.toDp() }, with(density) { to.height.toDp() })) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp)
                    .graphicsLayer { alpha = (1f - p.value * 3f).coerceIn(0f, 1f) },
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_schedule_24dp), null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.reply_status_title), Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_expand_more_24dp), null)
                }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = ((p.value - 0.2f) / 0.8f).coerceIn(0f, 1f) }) {
                    ReplyStatusChoices(state, listener)
                }
            }
        }
        if (glass != null) LiquidGlassPanel(glass, modifier,
            style = LiquidGlassStyle.Menu.copy(blur = 26.dp, frost = null, surfaceAlpha = 0.22f),
            clipContent = true) { content() }
        else Surface(modifier = modifier, shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer, shadowElevation = 8.dp) { content() }
    }
}
