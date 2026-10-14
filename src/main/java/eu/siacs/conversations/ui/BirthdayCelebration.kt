package eu.siacs.conversations.ui

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import eu.siacs.conversations.FeatureFlag
import eu.siacs.conversations.R
import eu.siacs.conversations.utils.BirthdayStore
import eu.siacs.conversations.utils.FeatureFlagPreferences
import java.time.LocalDate
import java.time.Month
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A one-off birthday greeting: confetti bursts out and, while it flies, a new screen fades in and
 * a short Russian message appears word by word. It shows on October 15 for the one account listed
 * in [ADDRESSES]; the [FeatureFlag.BIRTHDAY_CELEBRATION] flag shows it on every launch, any day,
 * for anyone (for trying it out). Once each time the app is opened.
 */
object BirthdayCelebration {

    /** Bare addresses (lower case) that get the greeting on the day. */
    // The server is on-chat.ru (with a hyphen); the dictated "onchat.ru" spelling is kept too in
    // case the account really lives there.
    private val ADDRESSES = setOf("natasha@on-chat.ru", "natasha@onchat.ru")

    // Once per opening of the app, not once per process: the service keeps the process alive after
    // the user leaves, so a process-wide flag meant "never again until the phone restarts the app".
    // A fresh activity instance is a fresh opening.
    private var lastActivity: java.lang.ref.WeakReference<XmppActivity>? = null

    @JvmStatic
    fun maybeShow(activity: XmppActivity) {
        if (lastActivity?.get() === activity) return
        val service = activity.xmppConnectionService ?: return
        val flagOn = FeatureFlagPreferences(activity).isEnabled(FeatureFlag.BIRTHDAY_CELEBRATION)
        val today = LocalDate.now()
        val isTheDay = today.month == Month.OCTOBER && today.dayOfMonth == 15
        val isHer = service.accounts.any { it.getJid().asBareJid().toString().lowercase() in ADDRESSES }
        // The personal greeting (Natasha, or the feature flag for trying it out) wins; otherwise
        // anyone whose own birthday is today gets the same screen with a standard, politely warm
        // text in the phone's language.
        val personal = flagOn || (isTheDay && isHer)
        var name: String? = null
        if (!personal) {
            val store = BirthdayStore(activity)
            val celebrating = service.accounts.firstOrNull { account ->
                val uuid = account.getUuid() ?: return@firstOrNull false
                store.get(uuid, account.getJid().asBareJid().toString())?.isOn(today) == true
            } ?: return
            name = celebrating.getDisplayName()?.takeIf { it.isNotBlank() }
        }
        lastActivity = java.lang.ref.WeakReference(activity)
        val arguments = Bundle().apply {
            putBoolean(BirthdayDialogFragment.ARG_PERSONAL, personal)
            putString(BirthdayDialogFragment.ARG_NAME, name)
        }
        // Let the chat list appear first, then the confetti goes off over it.
        activity.window.decorView.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            val manager = activity.supportFragmentManager
            if (manager.isStateSaved || manager.findFragmentByTag(BirthdayDialogFragment.TAG) != null) return@postDelayed
            BirthdayDialogFragment().apply { this.arguments = arguments }
                .show(manager, BirthdayDialogFragment.TAG)
        }, 1200)
    }
}

class BirthdayDialogFragment : DialogFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, 0)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val personal = arguments?.getBoolean(ARG_PERSONAL, true) ?: true
            val name = arguments?.getString(ARG_NAME)
            setContent {
                ImpulseExpressiveTheme {
                    BirthdayScreen(personal = personal, name = name, onDone = { dismissAllowingStateLoss() })
                }
            }
        }

    companion object {
        const val TAG = "BirthdayDialog"
        const val ARG_PERSONAL = "personal"
        const val ARG_NAME = "name"
    }
}

@Composable
private fun BirthdayScreen(personal: Boolean, name: String?, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var started by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    // The personal text is Russian only, by design; everyone else gets the standard one in the
    // phone's language.
    val lines = if (personal) listOf(
        stringResource(R.string.birthday_title) to MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        stringResource(R.string.birthday_line_1) to MaterialTheme.typography.titleMedium,
        stringResource(R.string.birthday_line_2) to MaterialTheme.typography.titleMedium,
        stringResource(R.string.birthday_signature) to MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
    ) else listOf(
        (if (name != null) stringResource(R.string.birthday_generic_title_named, name)
        else stringResource(R.string.birthday_generic_title)) to
            MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
        stringResource(R.string.birthday_generic_line_1) to MaterialTheme.typography.titleMedium,
        stringResource(R.string.birthday_generic_line_2) to MaterialTheme.typography.titleMedium,
        stringResource(R.string.birthday_generic_line_3) to MaterialTheme.typography.titleMedium,
        stringResource(R.string.birthday_generic_signature) to MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium),
    )
    val thanks = stringResource(if (personal) R.string.birthday_thanks else R.string.birthday_generic_thanks)
    val wordMs = 120
    val startMs = 1100
    val totalWords = lines.sumOf { it.first.split(' ').size }
    LaunchedEffect(Unit) {
        started = true
        kotlinx.coroutines.delay((startMs + totalWords * wordMs + 600).toLong())
        showButton = true
    }
    // The new screen fades in while the confetti is still flying.
    val backdrop by animateFloatAsState(if (started) 1f else 0f, tween(900), label = "birthdayBackdrop")
    val cakePop by animateFloatAsState(
        if (started) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 300f), label = "birthdayCake")
    val gradient = Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary, scheme.secondary))

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().alpha(backdrop)
                .background(Brush.verticalGradient(listOf(scheme.primaryContainer, scheme.surface, scheme.tertiaryContainer)))
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = scheme.surface,
                modifier = Modifier.size(96.dp).graphicsLayer { scaleX = cakePop; scaleY = cakePop },
            ) {
                Box(contentAlignment = Alignment.Center) { Text("🎂", fontSize = 52.sp) }
            }
            Spacer(Modifier.height(24.dp))
            var index = 0
            lines.forEach { (text, style) ->
                RevealLine(text, style, gradient, firstWord = index, startMs = startMs, wordMs = wordMs)
                index += text.split(' ').size
                Spacer(Modifier.height(14.dp))
            }
            Spacer(Modifier.height(18.dp))
            AnimatedVisibility(visible = showButton, enter = fadeIn() + scaleIn(spring(stiffness = 380f, dampingRatio = 0.7f))) {
                Button(onClick = onDone) { Text(thanks) }
            }
        }
        ConfettiLayer(Modifier.fillMaxSize())
    }
}

/** A line whose words slide in from the left one after another, each fading from transparent. */
@Composable
private fun RevealLine(text: String, style: TextStyle, brush: Brush, firstWord: Int, startMs: Int, wordMs: Int) {
    var go by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { go = true }
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        text.split(' ').forEachIndexed { i, word ->
            val delay = startMs + (firstWord + i) * wordMs
            val progress by animateFloatAsState(
                targetValue = if (go) 1f else 0f,
                animationSpec = tween(520, delayMillis = delay, easing = FastOutSlowInEasing),
                label = "word",
            )
            Text(
                text = "$word ",
                style = style.merge(TextStyle(brush = brush, textAlign = TextAlign.Center)),
                modifier = Modifier.graphicsLayer {
                    alpha = progress
                    translationX = (1f - progress) * -36.dp.toPx()
                },
            )
        }
    }
}

// ─── Confetti ────────────────────────────────────────────────────────────────

private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var phase: Float, val spin: Float, val w: Float, val h: Float, val color: Color, val round: Boolean,
)

private val CONFETTI_COLORS = listOf(
    Color(0xFFFF5252), Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF2196F3),
    Color(0xFFE040FB), Color(0xFFFF9800), Color(0xFF00BCD4), Color(0xFFFFEB3B),
)

/** Two cannons in the bottom corners, a second burst a moment later, then a light rain from the
 * top. Plain Canvas physics: gravity, air drag and a flutter (the paper turns edge-on). */
@Composable
private fun ConfettiLayer(modifier: Modifier = Modifier) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val particles = remember { ArrayList<Particle>() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(size) {
        if (size.width == 0) return@LaunchedEffect
        val rnd = Random(System.nanoTime())
        val w = size.width.toFloat()
        val h = size.height.toFloat()
        val scale = w / 1080f
        fun piece(x: Float, y: Float, vx: Float, vy: Float) = Particle(
            x, y, vx, vy, rnd.nextFloat() * 6.28f, 4f + rnd.nextFloat() * 8f,
            (10f + rnd.nextFloat() * 10f) * scale.coerceAtLeast(0.6f),
            (16f + rnd.nextFloat() * 14f) * scale.coerceAtLeast(0.6f),
            CONFETTI_COLORS[rnd.nextInt(CONFETTI_COLORS.size)], rnd.nextInt(5) == 0,
        )
        fun burst(fromLeft: Boolean, count: Int) {
            repeat(count) {
                // 35..75 degrees above the horizontal, towards the middle of the screen.
                val angle = Math.toRadians((35 + rnd.nextInt(40)).toDouble())
                val speed = (1100f + rnd.nextFloat() * 1500f) * scale.coerceAtLeast(0.6f)
                val dir = if (fromLeft) 1f else -1f
                particles += piece(
                    if (fromLeft) w * 0.04f else w * 0.96f, h * 1.02f,
                    (cos(angle) * speed * dir).toFloat(), (-sin(angle) * speed).toFloat(),
                )
            }
        }
        burst(true, 80); burst(false, 80)
        var secondBurst = false
        var elapsed = 0f
        var rainClock = 0f
        var last = androidx.compose.runtime.withFrameNanos { it }
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            elapsed += dt
            if (!secondBurst && elapsed > 1.1f) {
                secondBurst = true
                burst(true, 50); burst(false, 50)
            }
            if (elapsed in 1.5f..7f) {
                rainClock += dt
                while (rainClock > 0.05f) {
                    rainClock -= 0.05f
                    particles += piece(rnd.nextFloat() * w, -30f, (rnd.nextFloat() - 0.5f) * 160f, 250f + rnd.nextFloat() * 350f)
                }
            }
            val drag = Math.pow(0.55, dt.toDouble()).toFloat()
            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                p.vy += 1500f * scale.coerceAtLeast(0.6f) * dt
                p.vx *= drag
                if (p.vy > 700f * scale.coerceAtLeast(0.6f)) p.vy = 700f * scale.coerceAtLeast(0.6f) // terminal speed
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.phase += p.spin * dt
                if (p.y > h + 60f && p.vy > 0) iterator.remove()
            }
            tick = now
            if (elapsed > 7f && particles.isEmpty()) break
        }
    }
    Canvas(modifier.onSizeChanged { size = it }) {
        tick // redraw every frame
        for (p in particles) {
            val flutter = abs(cos(p.phase))
            if (p.round) {
                drawCircle(p.color, radius = p.w * 0.5f, center = Offset(p.x, p.y))
            } else {
                rotate(degrees = (p.phase * 57.3f) % 360f, pivot = Offset(p.x, p.y)) {
                    val height = p.h * (0.25f + 0.75f * flutter)
                    drawRect(p.color, Offset(p.x - p.w / 2, p.y - height / 2), Size(p.w, height))
                }
            }
        }
    }
}
