package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.persistance.FileBackend
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded
import eu.siacs.conversations.ui.util.Attachment
import eu.siacs.conversations.ui.util.ViewUtil
import eu.siacs.conversations.xmpp.Jid
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Every photo, video, voice message and file of one chat in one place, without the messages around them. A
 * Compose screen (Expressive, not glass): filter chips on top (voice messages are off to begin with), a grid of
 * previews, a tap opens the built-in viewer (other files open in the app that handles them), and a long press
 * starts selecting several, which can be shared, saved, deleted, or (for one) shown in the chat where it was sent.
 */
class MediaBrowserActivity : XmppActivity(), OnMediaLoaded {

    enum class FilterType { IMAGES, VIDEOS, AUDIO, DOCUMENTS, OTHER }

    private val all = mutableStateOf<List<Attachment>>(emptyList())
    private var loaded by mutableStateOf(false)
    private val filters = mutableStateOf(DEFAULT_FILTERS)
    private val selection = mutableStateListOf<String>()
    private var title by mutableStateOf("")
    private var deleteTarget by mutableStateOf<List<File>?>(null)

    private val viewerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uuid = result.data?.getStringExtra(MediaViewerActivity.EXTRA_RESULT_SHOW_UUID)
            if (result.resultCode == RESULT_OK && uuid != null) showInChat(uuid)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArray(EXTRA_SELECTION)?.let { selection.addAll(it) }
        savedInstanceState?.getStringArray(EXTRA_APPLIED_FILTER)?.let { names ->
            val restored = names.mapNotNull { runCatching { FilterType.valueOf(it) }.getOrNull() }.toSet()
            if (restored.isNotEmpty()) filters.value = restored
        }
        setContent {
            ImpulseExpressiveTheme {
                MediaBrowserScreen(
                    title = title,
                    loaded = loaded,
                    attachments = visible(),
                    filters = filters.value,
                    selection = selection,
                    canSave = canSave(),
                    onBack = { finish() },
                    onToggleFilter = { toggleFilter(it) },
                    onClick = { a, bounds -> open(a, bounds) },
                    onLongClick = { toggleSelection(it) },
                    onToggleSelection = { toggleSelection(it) },
                    onClearSelection = { selection.clear() },
                    onSelectAll = { selection.clear(); selection.addAll(visible().map { it.uuid.toString() }) },
                    onShare = { share() },
                    onSave = { save() },
                    onDelete = { askDelete() },
                    onShowInChat = { selection.firstOrNull()?.let { showInChat(it) } },
                    loadPreview = { a, size -> preview(a, size) },
                    deleteTarget = deleteTarget,
                    onConfirmDelete = { files -> deleteTarget = null; deleteFiles(files) },
                    onCancelDelete = { deleteTarget = null },
                )
            }
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        bundle.putStringArray(EXTRA_SELECTION, selection.toTypedArray())
        bundle.putStringArray(EXTRA_APPLIED_FILTER, filters.value.map { it.name }.toTypedArray())
        super.onSaveInstanceState(bundle)
    }

    override fun refreshUiReal() {}

    override fun onBackendConnected() {
        val (accountUuid, jid) = gallery()
        val account = xmppConnectionService.findAccountByUuid(accountUuid)
        title = account?.getRoster()?.getContact(jid)?.getDisplayName() ?: jid.toString()
        reload()
    }

    private fun gallery(): Pair<String, Jid> {
        val account = intent?.getStringExtra("account")
        val jid = intent?.getStringExtra("jid")
        check(account != null && jid != null) { "Activity started with incomplete extras" }
        return account to Jid.of(jid)
    }

    private fun reload() {
        val service = xmppConnectionService ?: return
        val (account, jid) = gallery()
        service.getAttachments(account, jid, 0, this)
    }

    override fun onMediaLoaded(attachments: List<Attachment>) {
        runOnUiThread {
            all.value = attachments
            loaded = true
            // What is no longer there cannot stay selected.
            val present = attachments.map { it.uuid.toString() }.toSet()
            selection.removeAll { it !in present }
        }
    }

    // ─── What is shown ───

    private fun matches(a: Attachment, type: FilterType): Boolean {
        val mime = a.mime.orEmpty()
        return when (type) {
            FilterType.IMAGES -> mime.startsWith("image/")
            FilterType.VIDEOS -> mime.startsWith("video/")
            FilterType.AUDIO -> mime.startsWith("audio/")
            FilterType.DOCUMENTS -> eu.siacs.conversations.ui.adapter.MediaAdapter.ALL_DOCUMENT_MIMES.contains(a.mime)
            FilterType.OTHER -> FilterType.entries.filter { it != FilterType.OTHER }.none { matches(a, it) }
        }
    }

    private fun visible(): List<Attachment> {
        val applied = filters.value
        return all.value.filter { a -> applied.any { matches(a, it) } }
    }

    private fun toggleFilter(type: FilterType) {
        val current = filters.value
        filters.value = if (type in current) {
            // At least one kind stays on.
            if (current.size > 1) current - type else current
        } else current + type
    }

    private fun toggleSelection(a: Attachment) {
        val key = a.uuid.toString()
        if (!selection.remove(key)) selection.add(key)
    }

    private fun selected(): List<Attachment> =
        all.value.filter { it.uuid.toString() in selection }

    private fun canSave(): Boolean {
        val service = xmppConnectionService ?: return false
        if (selection.isEmpty()) return false
        val locations = service.fileBackend.inferStorageLocation(selected())
        return locations.any { it != null && !it.sharedStorage() }
    }

    // ─── Opening ───

    private fun preview(a: Attachment, size: Int): android.graphics.Bitmap? {
        val service = xmppConnectionService ?: return null
        if (!a.renderThumbnail()) return null
        return service.fileBackend.getPreviewForUri(a, size, false)
    }

    private fun open(a: Attachment, bounds: android.graphics.Rect?) {
        if (selection.isNotEmpty()) {
            toggleSelection(a)
            return
        }
        val mime = a.mime.orEmpty()
        if (mime.startsWith("image/") || mime.startsWith("video/")) {
            openInViewer(a, bounds)
        } else {
            ViewUtil.view(this, a)
        }
    }

    /** The built-in viewer, growing out of the preview that was tapped. */
    private fun openInViewer(a: Attachment, bounds: android.graphics.Rect?) {
        val service = xmppConnectionService ?: return
        val (accountUuid, jid) = gallery()
        val account = service.findAccountByUuid(accountUuid) ?: return
        val conversation = service.find(account, jid) ?: return
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) { service.databaseBackend.getMessage(conversation, a.uuid.toString()) }
            if (message == null) {
                ViewUtil.view(this@MediaBrowserActivity, a)
                return@launch
            }
            val intent = MediaViewerActivity.launch(this@MediaBrowserActivity, message)
            val options = if (bounds != null) {
                ActivityOptionsCompat.makeScaleUpAnimation(window.decorView, bounds.left, bounds.top, bounds.width(), bounds.height())
            } else null
            viewerLauncher.launch(intent, options)
        }
    }

    /** The chat, scrolled to the message this file came in (the context around it). */
    private fun showInChat(uuid: String) {
        val service = xmppConnectionService ?: return
        val (accountUuid, jid) = gallery()
        val account = service.findAccountByUuid(accountUuid) ?: return
        val conversation = service.find(account, jid) ?: return
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) { service.databaseBackend.getMessage(conversation, uuid) }
            if (message != null) switchToConversationAtMessage(conversation, message, false)
        }
    }

    // ─── Selection actions ───

    private fun share() {
        val attachments = selected()
        if (attachments.isEmpty()) return
        val mimes = attachments.map { ViewUtil.nullToWildcard(it.mime) }.toSet()
        val uris = attachments.map {
            FileBackend.getUriForUri(this, it.uri).buildUpon().appendQueryParameter("uuid", it.uuid.toString()).build()
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.type = if (mimes.size == 1) mimes.first() else ViewUtil.WILDCARD
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, null))
    }

    private fun save() {
        val service = xmppConnectionService ?: return
        val locations = service.fileBackend.inferStorageLocation(selected())
        if (locations.isEmpty()) return
        selection.clear()
        Futures.addCallback(
            service.fileBackend.saveInternalToExternal(locations),
            object : FutureCallback<List<Void?>> {
                override fun onSuccess(result: List<Void?>) {
                    val n = result.size
                    Toast.makeText(applicationContext, resources.getQuantityString(R.plurals.attachments_saved, n, n), Toast.LENGTH_LONG).show()
                }

                override fun onFailure(t: Throwable) {
                    Log.e(Config.LOGTAG, "could not save attachments", t)
                    Toast.makeText(applicationContext, R.string.could_not_save_files, Toast.LENGTH_LONG).show()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun askDelete() {
        val files = selected().filter { it.uri.scheme == "file" }.mapNotNull { a -> a.uri.path?.let { File(it) } }
        if (files.isNotEmpty()) deleteTarget = files
    }

    private fun deleteFiles(files: List<File>) {
        val service = xmppConnectionService ?: return
        selection.clear()
        Futures.addCallback(
            service.deleteMediaFiles(files),
            object : FutureCallback<Void?> {
                override fun onSuccess(result: Void?) {
                    reload()
                }

                override fun onFailure(t: Throwable) {
                    Log.e(Config.LOGTAG, "could not delete files", t)
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    companion object {
        private const val EXTRA_SELECTION = "selection"
        private const val EXTRA_APPLIED_FILTER = "applied-filter"

        /** Everything but voice messages: they were most of what filled this screen. */
        val DEFAULT_FILTERS: Set<FilterType> = FilterType.entries.toSet() - FilterType.AUDIO

        @JvmStatic
        fun launch(context: Context, contact: Contact) =
            launch(context, contact.getAccount(), contact.getAddress().asBareJid().toString())

        @JvmStatic
        fun launch(context: Context, conversation: Conversation) =
            launch(context, conversation.getAccount(), conversation.getAddress().asBareJid().toString())

        private fun launch(context: Context, account: Account, jid: String) {
            context.startActivity(
                Intent(context, MediaBrowserActivity::class.java).putExtra("account", account.getUuid()).putExtra("jid", jid),
            )
        }
    }
}
