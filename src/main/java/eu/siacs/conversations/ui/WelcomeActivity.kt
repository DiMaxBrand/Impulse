package eu.siacs.conversations.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.siacs.conversations.R

class ImpulseWelcomeActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            MaterialExpressiveTheme(
                colorScheme = dynamicOrStaticColorScheme(context, androidx.compose.foundation.isSystemInDarkTheme()),
                motionScheme = MotionScheme.expressive(),
            ) {
                WelcomeScreen(
                    onComplete = {
                        startActivity(Intent(this, NotificationSetupActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }
}

private data class WelcomeSlide(
    val title: Int,
    val body: Int,
    val icon: Int,
    val demoVideo: Int? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeScreen(onComplete: () -> Unit) {
    val slides = listOf(
        WelcomeSlide(R.string.welcome_intro_title, R.string.welcome_intro_body, R.drawable.ic_chat_24dp),
        WelcomeSlide(
            R.string.welcome_edit_title,
            R.string.welcome_edit_body,
            R.drawable.ic_edit_24dp,
            demoVideo = R.raw.welcome_edit_demo,
        ),
        WelcomeSlide(R.string.welcome_pin_title, R.string.welcome_pin_body, R.drawable.ic_push_pin_24dp),
        WelcomeSlide(R.string.welcome_menu_title, R.string.welcome_menu_body, R.drawable.ic_more_vert_24dp),
        WelcomeSlide(
            R.string.welcome_glass_title,
            R.string.welcome_glass_body,
            R.drawable.ic_palette_24dp,
            demoVideo = R.raw.welcome_glass_demo,
        ),
    )
    var page by remember { mutableIntStateOf(0) }
    val last = page == slides.lastIndex
    val slide = slides[page]
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(1.dp))
        AnimatedContent(targetState = slide, label = "welcome_slide") { current ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (current.demoVideo != null) {
                    LoopingWelcomeDemo(current.demoVideo)
                } else {
                    Box(
                        modifier = Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(current.icon), null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text(stringResource(current.title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(current.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                slides.indices.forEach { index ->
                    Box(Modifier.width(if (index == page) 24.dp else 8.dp).height(8.dp).clip(CircleShape).background(if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant))
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { if (page == 0) onComplete() else page-- }) {
                    Text(stringResource(if (page == 0) R.string.welcome_skip else R.string.back))
                }
                Button(onClick = { if (last) onComplete() else page++ }, colors = ButtonDefaults.buttonColors()) {
                    Text(stringResource(if (last) R.string.welcome_get_started else R.string.next))
                }
            }
        }
    }
}

/** A muted, control-free preview: it restarts automatically rather than behaving like media. */
@Composable
private fun LoopingWelcomeDemo(videoRes: Int) {
    val context = LocalContext.current
    AndroidView(
        factory = { viewContext ->
            VideoView(viewContext).apply {
                setVideoURI(Uri.parse("android.resource://${context.packageName}/$videoRes"))
                setOnPreparedListener { player ->
                    player.setVolume(0f, 0f)
                    player.isLooping = true
                    start()
                }
            }
        },
        modifier = Modifier.width(195.dp).height(260.dp).clip(MaterialTheme.shapes.extraLarge),
    )
}
