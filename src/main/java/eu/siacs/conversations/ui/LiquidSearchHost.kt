package eu.siacs.conversations.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.glass.LiquidGlassStyle
import eu.siacs.conversations.ui.glass.LiquidMenuItem
import eu.siacs.conversations.ui.glass.LiquidMenuOverlay
import eu.siacs.conversations.ui.glass.LiquidMorphContainer
import eu.siacs.conversations.ui.glass.rememberGlassStack
import eu.siacs.conversations.ui.glass.rememberLiquidMenuState
import kotlinx.coroutines.delay

/**
 * The chat list with the search in the liquid glass experiment. This is its own version of
 * [ConversationsSearchHost], not a skin on top of it: the glass search pill, the QR bubble and the
 * avatar bubble float over the list ([LiquidTopRow]); tapping the pill makes the pill itself grow
 * into the full-screen search (one glass container changing size, shape and contents on a spring),
 * and the QR bubble grows into its menu the same way. The result list, the callbacks and the
 * menu state are the same as the Material version's.
 */
@Composable
fun LiquidConversationsSearchHost(
    host: SearchHostState,
    actions: SearchHostActions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val textFieldState = rememberTextFieldState()
    val backdrop = rememberLayerBackdrop()
    // The glass pieces of this screen look through the list and through each other.
    val glass = rememberGlassStack(backdrop)
    val qrMenu = rememberLiquidMenuState()
    val fluidHost = eu.siacs.conversations.ui.glass.rememberFluidHost(eu.siacs.conversations.ui.glass.rememberMergingGlass(), reachDp = 6f)
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current

    var searchOpen by remember { mutableStateOf(false) }
    val searchProgress = remember { Animatable(0f, 0.001f) }
    var pillBounds by remember { mutableStateOf(Rect.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    // The same spring the app uses for container transforms, a little looser so it overshoots.
    LaunchedEffect(searchOpen) {
        host.expanded = searchOpen
        searchProgress.animateTo(if (searchOpen) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 380f))
    }
    val close: () -> Unit = {
        focusManager.clearFocus()
        keyboard?.hide()
        textFieldState.clearText()
        searchOpen = false
    }
    BackHandler(enabled = searchOpen) { close() }

    val submit: (String) -> Unit = { query ->
        val q = query.trim()
        if (q.isNotEmpty()) {
            actions.onSearchMessages(q)
            close()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .onGloballyPositioned {
                origin = it.positionInWindow()
                rootSize = it.size
            },
    ) {
        // The list is what shows through the glass.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }

        // The list runs up under the status bar, blurred there.
        eu.siacs.conversations.ui.glass.GlassStatusBarBlur(backdrop, Modifier.align(Alignment.TopCenter))
        eu.siacs.conversations.ui.glass.GlassNavigationBarBlur(backdrop, Modifier.align(Alignment.BottomCenter))
        LiquidTopRow(
            glass = glass,
            host = host,
            actions = actions,
            onSearch = { searchOpen = true },
            qrMenu = qrMenu,
            searchProgress = { searchProgress.value },
            onSearchBounds = { pillBounds = it },
            modifier = Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars),
            fluidHost = fluidHost,
        )

        // Derived, so this only changes when the container appears or is gone. Reading the animated
        // value directly here would rebuild this whole function, the chat list inside it included,
        // on every frame of the animation: that was most of the stutter when opening the search.
        val showSearch by remember { derivedStateOf { searchOpen || searchProgress.value > 0.001f } }
        if (showSearch) {
            LiquidMorphContainer(
                progressX = { searchProgress.value },
                from = { pillBounds.translate(-origin) },
                // The pill grows down and out to fill the screen from where it is.
                target = { overlay, _ ->
                    val a = pillBounds.translate(-origin)
                    Rect(0f, a.top, overlay.width.toFloat(), overlay.height.toFloat())
                },
                glass = glass,
                // Nearly solid when open, and the blur and the lens stop once it is: bending the
                // whole screen on every frame was what made opening the search stutter.
                style = LiquidGlassStyle.Sheet,
                cornerTo = 0.dp,
                glassUntil = 0.8f,
            ) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(searchOpen) {
                    if (searchOpen) {
                        delay(120) // let it start growing, then bring up the keyboard
                        runCatching { focusRequester.requestFocus() }
                        keyboard?.show()
                    }
                }
                val width = with(density) { rootSize.width.toDp() }
                val height = with(density) { rootSize.height.toDp() }
                // Laid out at the final size from the first frame, so nothing reflows while the
                // container is still growing around it.
                Box(Modifier.requiredSize(width, height)) {
                    // What the pill looked like, fading out as the container opens.
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 18.dp)
                            .graphicsLayer { alpha = 1f - (searchProgress.value * 3f).coerceIn(0f, 1f) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search_24dp),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    // The search itself, fading in.
                    Column(
                        Modifier.fillMaxSize()
                            .graphicsLayer { alpha = ((searchProgress.value - 0.25f) / 0.5f).coerceIn(0f, 1f) },
                    ) {
                        SearchInputRow(textFieldState, focusRequester, onBack = close, onSubmit = submit)
                        HorizontalDivider()
                        val query = textFieldState.text.toString().trim()
                        val suggestions = remember(query, host.menuRevision.intValue) {
                            if (query.isEmpty()) emptyList() else actions.suggest(query)
                        }
                        SuggestionList(
                            suggestions = suggestions,
                            onClick = {
                                actions.onSuggestion(it)
                                close()
                            },
                        )
                    }
                }
            }
        }

        LiquidMenuOverlay(qrMenu, glass, fluid = fluidHost) {
            if (host.showScanQr) LiquidMenuItem(
                R.drawable.ic_qr_code_scanner_24dp, stringResource(R.string.scan_qr_code),
            ) { qrMenu.open = false; actions.onScanQr() }
            if (host.showShowQr) LiquidMenuItem(
                R.drawable.ic_qr_code_24dp, stringResource(R.string.show_qr_code),
            ) { qrMenu.open = false; actions.onShowQr() }
            if (host.showEasyInvite) LiquidMenuItem(
                R.drawable.ic_join_24dp, stringResource(R.string.server_invite),
            ) { qrMenu.open = false; actions.onEasyInvite() }
        }
    }
}

/** Back arrow, the text field, and a clear button once there is text. */
@Composable
private fun SearchInputRow(
    textFieldState: TextFieldState,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back_24dp), contentDescription = null)
        }
        BasicTextField(
            state = textFieldState,
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = KeyboardActionHandler { onSubmit(textFieldState.text.toString()) },
            decorator = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (textFieldState.text.isEmpty()) {
                        Text(
                            stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        if (textFieldState.text.isNotEmpty()) {
            IconButton(onClick = { textFieldState.clearText() }) {
                Icon(painterResource(R.drawable.ic_close_24dp), contentDescription = null)
            }
        }
    }
}
