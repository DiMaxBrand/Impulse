package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.google.common.base.Strings
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityAddReactionBinding

/** Also doubles as a plain "pick any emoji" screen (see [pickerIntent]) -- Quick Reactions' custom
 * third slot uses the exact same picker the hint text already promises ("a screen with virtually
 * any emoji") instead of a second bespoke picker, just without a message to send the pick to: the
 * picked emoji is returned via [EXTRA_PICKED_EMOJI] instead of being applied directly. */
class AddReactionActivity : XmppActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityAddReactionBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_add_reaction)
        Activities.setStatusAndNavigationBarColors(this, binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationIcon(R.drawable.ic_clear_24dp)
        binding.toolbar.setNavigationOnClickListener { finish() }
        setTitle(R.string.add_reaction_title)
        binding.emojiPicker.setOnEmojiPickedListener { emojiViewItem -> onEmojiPicked(emojiViewItem.emoji) }
    }

    private fun onEmojiPicked(emoji: String) {
        if (getIntent()?.getBooleanExtra(EXTRA_PICK_ONLY, false) == true) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PICKED_EMOJI, emoji))
            finish()
            return
        }
        addReaction(emoji)
    }

    private fun addReaction(emoji: String) {
        val intent = getIntent()
        val conversation = if (intent == null) null else intent.getStringExtra("conversation")
        val message = if (intent == null) null else intent.getStringExtra("message")
        if (Strings.isNullOrEmpty(conversation) || Strings.isNullOrEmpty(message)) {
            Toast.makeText(this, R.string.could_not_add_reaction, Toast.LENGTH_LONG).show()
            return
        }
        val c = xmppConnectionService.findConversationByUuid(conversation)
        val m = if (c == null) null else c.findMessageWithUuid(message)
        if (m == null) {
            Toast.makeText(this, R.string.could_not_add_reaction, Toast.LENGTH_LONG).show()
            return
        }
        val aggregated = m.aggregatedReactions
        if (aggregated.ourReactions.contains(emoji)) {
            this.sendReactions(m, aggregated.ourReactions)
        } else {
            val reactionBuilder = ImmutableSet.builder<String>()
            reactionBuilder.addAll(aggregated.ourReactions)
            reactionBuilder.add(emoji)
            this.sendReactions(m, reactionBuilder.build())
        }
        finish()
    }

    override fun refreshUiReal() {}

    override fun onBackendConnected() {}

    companion object {
        private const val EXTRA_PICK_ONLY = "pick_only"
        const val EXTRA_PICKED_EMOJI = "picked_emoji"

        /** No conversation/message needed -- the picked emoji comes back via [EXTRA_PICKED_EMOJI]
         * in the activity result instead of being sent as a reaction. */
        fun pickerIntent(context: Context): Intent =
            Intent(context, AddReactionActivity::class.java).putExtra(EXTRA_PICK_ONLY, true)
    }
}
