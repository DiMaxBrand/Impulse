package eu.siacs.conversations.ui.activity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.ActionBarActivity
import eu.siacs.conversations.ui.ImpulseExpressiveTheme
import eu.siacs.conversations.ui.IncomingCallContent
import eu.siacs.conversations.ui.IncomingCallState

/**
 * Developer Options preview of the incoming call screen: the real [IncomingCallContent]
 * composable fed fake state, so the avatar morph, the slider and the first-time hint can be tried
 * without needing a second person to ring. Answer/decline only show a toast, then the slider
 * resets.
 */
class IncomingCallPreviewActivity : ActionBarActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ImpulseExpressiveTheme { PreviewScreen(onClose = { finish() }) } }
    }

    @Composable
    private fun PreviewScreen(onClose: () -> Unit) {
        var round by remember { mutableIntStateOf(0) }
        var hint by remember { mutableIntStateOf(1) }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Box(Modifier.fillMaxSize()) {
                // New state per round so a committed slider starts fresh.
                key(round, hint) {
                    val state = remember {
                        IncomingCallState().apply {
                            setVisible(true)
                            setSliderVisible(true)
                            setHintVisible(hint == 1)
                            setStatusText("Incoming call")
                            setAvatar(fakeAvatar())
                            setAudioChoices(2)
                            onAccept = Runnable {
                                // Like the real thing: the call goes live and the handle
                                // becomes the hang-up button.
                                setSliderVisible(false)
                                setStatusText("Connected")
                                setEstablished(true)
                                setDurationText("00:42")
                            }
                            onDecline = Runnable { finishRound(false) { round++ } }
                            onHangUp = Runnable { finishRound(true) { round++ } }
                            onToggleMic = Runnable { setMicOn(!micOn) }
                        }
                    }
                    Box(Modifier.fillMaxSize().padding(top = 72.dp)) {
                        IncomingCallContent(state)
                    }
                }
                Column(
                    modifier =
                        Modifier.statusBarsPadding().padding(horizontal = 8.dp).align(Alignment.TopStart)
                ) {
                    TextButton(onClick = onClose) { Text("Close") }
                }
                TextButton(
                    onClick = { hint = 1 - hint },
                    modifier =
                        Modifier.statusBarsPadding().padding(horizontal = 8.dp).align(Alignment.TopEnd),
                ) {
                    Text(if (hint == 1) "Hint: on" else "Hint: off")
                }
            }
        }
    }

    private fun finishRound(answered: Boolean, restart: () -> Unit) {
        Toast.makeText(this, if (answered) "Hung up" else "Declined", Toast.LENGTH_SHORT).show()
        window.decorView.postDelayed(restart, 700)
    }

    private fun fakeAvatar(): Bitmap {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        size.toFloat(),
                        size.toFloat(),
                        0xFFFFB74D.toInt(),
                        0xFFE91E63.toInt(),
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        val text =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = size * 0.5f
                textAlign = Paint.Align.CENTER
            }
        canvas.drawText("A", size / 2f, size / 2f + text.textSize * 0.35f, text)
        return bitmap
    }
}
