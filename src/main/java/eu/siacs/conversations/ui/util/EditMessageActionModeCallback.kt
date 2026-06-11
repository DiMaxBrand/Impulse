package eu.siacs.conversations.ui.util

import android.content.ClipboardManager
import android.content.Context
import android.text.TextUtils
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import eu.siacs.conversations.R
import eu.siacs.conversations.ui.widget.EditMessage

class EditMessageActionModeCallback(private val editMessage: EditMessage) : ActionMode.Callback {

    private val clipboardManager: ClipboardManager =
        editMessage.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val inflater = mode.menuInflater
        inflater.inflate(R.menu.edit_message_actions, menu)
        val pasteAsQuote = menu.findItem(R.id.paste_as_quote)
        val primaryClip = clipboardManager.primaryClip
        if (primaryClip != null && primaryClip.itemCount > 0) {
            val mimeType: String
            try {
                mimeType = primaryClip.description.getMimeType(0)
            } catch (e: Exception) {
                pasteAsQuote.isVisible = false
                return true
            }
            pasteAsQuote.isVisible =
                mimeType.startsWith("text/")
                        && !TextUtils.isEmpty(primaryClip.getItemAt(0).text)
        } else {
            pasteAsQuote.isVisible = false
        }
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId == R.id.paste_as_quote) {
            val primaryClip = clipboardManager.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                editMessage.insertAsQuote(primaryClip.getItemAt(0).text.toString())
                return true
            }
        }
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {}
}
