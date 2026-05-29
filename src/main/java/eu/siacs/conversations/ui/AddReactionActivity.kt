package eu.siacs.conversations.ui

import android.os.Bundle
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.google.common.base.Strings
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityAddReactionBinding

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
        binding.emojiPicker.setOnEmojiPickedListener { emojiViewItem -> addReaction(emojiViewItem.emoji) }
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
}
