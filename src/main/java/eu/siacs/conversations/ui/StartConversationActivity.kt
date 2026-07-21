package eu.siacs.conversations.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.Pair
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterables
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import de.gultsch.common.MiniUri
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityStartConversationBinding
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Contact
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.ListItem
import eu.siacs.conversations.services.QuickConversationsService
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.interfaces.OnBackendConnected
import eu.siacs.conversations.ui.util.JidDialog
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil
import eu.siacs.conversations.ui.util.PendingItem
import eu.siacs.conversations.ui.util.SoftKeyboardUtils
import eu.siacs.conversations.utils.AccountUtils
import eu.siacs.conversations.utils.CharSequences
import eu.siacs.conversations.xmpp.Jid
import eu.siacs.conversations.xmpp.OnUpdateBlocklist
import eu.siacs.conversations.xmpp.manager.BookmarkManager
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager
import eu.siacs.conversations.services.AbstractQuickConversationsService
import im.conversations.android.model.Bookmark
import im.conversations.android.model.DynamicTag
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class StartConversationActivity :
    XmppActivity(),
    XmppConnectionService.OnConversationUpdate,
    XmppConnectionService.OnRosterUpdate,
    OnUpdateBlocklist,
    CreatePrivateGroupChatDialog.CreateConferenceDialogListener,
    JoinConferenceDialog.JoinConferenceDialogListener,
    CreatePublicChannelDialog.CreatePublicChannelDialogListener {

    data class Invite(
        val uri: MiniUri.Xmpp,
        val account: String?,
        val scanned: Boolean,
        val forceDialog: Boolean,
    )

    companion object {
        private const val PREF_KEY_CONTACT_INTEGRATION_CONSENT = "contact_list_integration_consent"
        const val EXTRA_INVITE_URI = "eu.siacs.conversations.invite_uri"

        @JvmStatic
        fun populateAccountSpinner(
            context: Context,
            accounts: List<String>?,
            spinner: AutoCompleteTextView,
        ) {
            if (accounts.isNullOrEmpty()) {
                val adapter =
                    ArrayAdapter(
                        context,
                        R.layout.item_autocomplete,
                        listOf(context.getString(R.string.no_accounts)),
                    )
                adapter.setDropDownViewResource(R.layout.item_autocomplete)
                spinner.setAdapter(adapter)
                spinner.isEnabled = false
            } else {
                val adapter = ArrayAdapter(context, R.layout.item_autocomplete, accounts)
                adapter.setDropDownViewResource(R.layout.item_autocomplete)
                spinner.setAdapter(adapter)
                spinner.isEnabled = true
                spinner.setText(Iterables.getFirst(accounts, null), false)
            }
        }

        @JvmStatic
        fun launch(context: Context) {
            val intent = Intent(context, StartConversationActivity::class.java)
            context.startActivity(intent)
        }

        @JvmStatic
        private fun createLauncherIntent(context: Context): Intent {
            val intent = Intent(context, StartConversationActivity::class.java)
            intent.action = Intent.ACTION_MAIN
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            return intent
        }

        @JvmStatic
        private fun isViewIntent(i: Intent?): Boolean {
            return i != null &&
                (Intent.ACTION_VIEW == i.action ||
                    Intent.ACTION_SENDTO == i.action ||
                    i.hasExtra(EXTRA_INVITE_URI))
        }

        @JvmStatic
        fun isValidJid(input: String?): Boolean {
            return try {
                val jid = Jid.ofUserInput(input)
                !jid.isDomainJid
            } catch (e: IllegalArgumentException) {
                false
            }
        }

        @JvmStatic
        fun shareAsChannel(context: Context, address: String) {
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_TEXT, "xmpp:$address?join")
            shareIntent.type = "text/plain"
            try {
                context.startActivity(
                    Intent.createChooser(shareIntent, context.getText(R.string.share_uri_with))
                )
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_application_to_share_uri, Toast.LENGTH_SHORT).show()
            }
        }

        @JvmStatic
        fun getSelectedAccount(context: Context, spinner: AutoCompleteTextView?): Account? {
            if (spinner == null || !spinner.isEnabled) {
                return null
            }
            if (context is XmppActivity) {
                val jid =
                    try {
                        Jid.of(spinner.text.toString())
                    } catch (e: IllegalArgumentException) {
                        return null
                    }
                val service = context.xmppConnectionService ?: return null
                return service.findAccountByJid(jid)
            }
            return null
        }

        @JvmStatic
        fun addInviteUri(to: Intent, activity: BaseActivity) {
            val source = activity.intent
            if (source == null || !source.hasExtra(EXTRA_INVITE_URI)) {
                return
            }
            val uri = MiniUri.getXmppUriOrNull(source.getStringExtra(EXTRA_INVITE_URI))
            if (uri == null) {
                return
            }
            Log.d(Config.LOGTAG, "dragging on invite uri: " + uri.asUri())
            to.putExtra(EXTRA_INVITE_URI, uri.asUri().toString())
        }

        @JvmStatic
        fun startOrConversationsActivity(baseActivity: BaseActivity, account: Account?): Intent {
            val currentIntent = baseActivity.intent
            val invite: MiniUri.Xmpp? =
                if (currentIntent != null) {
                    MiniUri.getXmppUriOrNull(currentIntent.getStringExtra(EXTRA_INVITE_URI)) as? MiniUri.Xmpp
                } else null
            val intent: Intent
            if (invite == null || account == null) {
                intent = Intent(baseActivity, ConversationsActivity::class.java)
            } else {
                intent = Intent(baseActivity, StartConversationActivity::class.java)
                Log.d(Config.LOGTAG, "dragging on invite uri: " + invite.asUri())
                intent.putExtra(EXTRA_INVITE_URI, invite.asUri().toString())
                intent.putExtra(EXTRA_ACCOUNT, account.jid.asBareJid().toString())
            }
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            return intent
        }
    }

    private val REQUEST_SYNC_CONTACTS = 0x28cf
    private val REQUEST_CREATE_CONFERENCE = 0x39da
    private val pendingViewIntent = PendingItem<Intent>()
    private val mInitialSearchValue = PendingItem<String>()
    private val oneShotKeyboardSuppress = AtomicBoolean()
    private val contacts: MutableList<ListItem> = ArrayList()
    private val conferences: MutableList<ListItem> = ArrayList()
    private val composeState = StartConversationListState()
    private val mActivatedAccounts = ArrayList<String>()
    private var mSearchEditText: EditText? = null
    private val mRequestedContactsPermission = AtomicBoolean(false)
    private val mOpenedFab = AtomicBoolean(false)
    private var mHideOfflineContacts = false
    private var createdByViewIntent = false
    private var mMenuSearchView: MenuItem? = null
    private var mPostponedActivityResult: Pair<Int, Intent>? = null
    private lateinit var binding: ActivityStartConversationBinding

    private val mOnActionExpandListener =
        object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                mSearchEditText?.post {
                    updateSearchViewHint()
                    mSearchEditText?.requestFocus()
                    if (oneShotKeyboardSuppress.compareAndSet(true, false)) {
                        return@post
                    }
                    val imm =
                        getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT)
                }
                composeState.fabExpanded.value = false
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                SoftKeyboardUtils.hideSoftKeyboard(this@StartConversationActivity)
                mSearchEditText?.setText("")
                filter(null)
                return true
            }
        }

    private val mOnTagClickedListener =
        Consumer<DynamicTag> { dynamicTag ->
            val searchView = mMenuSearchView
            if (searchView != null && dynamicTag is DynamicTag.RosterGroup) {
                val name = dynamicTag.name()
                searchView.expandActionView()
                mSearchEditText?.setText("")
                mSearchEditText?.append(name)
                filter(name)
            }
        }

    private val mSearchDone =
        TextView.OnEditorActionListener { _, _, _ ->
            val pos = composeState.currentTab.intValue
            if (pos == 0) {
                if (contacts.size == 1) {
                    openConversationForContact(contacts[0] as Contact)
                    return@OnEditorActionListener true
                } else if (contacts.isEmpty() && conferences.size == 1) {
                    openConversationsForBookmark(conferences[0] as Bookmark)
                    return@OnEditorActionListener true
                }
            } else {
                if (conferences.size == 1) {
                    openConversationsForBookmark(conferences[0] as Bookmark)
                    return@OnEditorActionListener true
                } else if (conferences.isEmpty() && contacts.size == 1) {
                    openConversationForContact(contacts[0] as Contact)
                    return@OnEditorActionListener true
                }
            }
            SoftKeyboardUtils.hideSoftKeyboard(this@StartConversationActivity)
            true
        }

    override fun onRosterUpdate() {
        this.refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_start_conversation)
        Activities.setStatusAndNavigationBarColors(this, binding.root)
        setSupportActionBar(binding.toolbar)
        configureActionBar(getSupportActionBar())

        StartConversationListHelper.setup(
            binding.startConversationCompose,
            composeState,
            startConversationScreenListener,
            !AbstractQuickConversationsService.isPlayStoreFlavor(),
            AbstractQuickConversationsService.isQuicksy(),
        )

        val preferences = getPreferences()

        mHideOfflineContacts =
            AbstractQuickConversationsService.isConversations() && preferences.getBoolean("hide_offline", false)

        val startSearching =
            preferences.getBoolean("start_searching", getResources().getBoolean(R.bool.start_searching))

        val intent: Intent?
        if (savedInstanceState == null) {
            intent = getIntent()
        } else {
            createdByViewIntent = savedInstanceState.getBoolean("created_by_view_intent", false)
            val search = savedInstanceState.getString("search")
            if (search != null) {
                mInitialSearchValue.push(search)
            }
            intent = savedInstanceState.getParcelable("intent")
        }

        if (isViewIntent(intent)) {
            pendingViewIntent.push(intent)
            createdByViewIntent = true
            setIntent(createLauncherIntent(this))
        } else if (startSearching && mInitialSearchValue.peek() == null) {
            mInitialSearchValue.push("")
        }
        mRequestedContactsPermission.set(
            savedInstanceState != null &&
                savedInstanceState.getBoolean("requested_contacts_permission", false)
        )
        mOpenedFab.set(savedInstanceState != null && savedInstanceState.getBoolean("opened_fab", false))
    }

    private val startConversationScreenListener =
        object : StartConversationScreenListener {
            override fun onContactClick(contact: Contact) {
                openConversationForContact(contact)
            }

            override fun onConferenceClick(bookmark: Bookmark) {
                openConversationsForBookmark(bookmark)
            }

            override fun onContactDetails(contact: Contact) {
                switchToContactDetails(contact)
            }

            override fun onContactShowQr(contact: Contact) {
                val uri = MiniUri.Xmpp(contact.getAddress())
                showQrCode(uri)
            }

            override fun onContactToggleBlock(contact: Contact) {
                BlockContactDialog.show(this@StartConversationActivity, contact)
            }

            override fun onContactDelete(contact: Contact) {
                deleteContact(contact)
            }

            override fun onConferenceShare(bookmark: Bookmark) {
                shareAsChannel(this@StartConversationActivity, bookmark.getAddress().asBareJid().toString())
            }

            override fun onConferenceDelete(bookmark: Bookmark) {
                deleteConference(bookmark)
            }

            override fun onTagClicked(tag: DynamicTag) {
                mOnTagClickedListener.accept(tag)
            }

            override fun onFabDiscoverChannels() {
                if (AbstractQuickConversationsService.isPlayStoreFlavor()) {
                    throw IllegalStateException("Channel discovery is not available on Google Play flavor")
                }
                startActivity(Intent(this@StartConversationActivity, ChannelDiscoveryActivity::class.java))
            }

            override fun onFabCreatePublicChannel() {
                showPublicChannelDialog()
            }

            override fun onFabJoinPublicChannel() {
                showJoinConferenceDialog(prefilledJidFromSearch())
            }

            override fun onFabCreatePrivateGroupChat() {
                showCreatePrivateGroupChatDialog()
            }

            override fun onFabCreateContact() {
                showCreateContactDialog(prefilledJidFromSearch(), null)
            }

            override fun onQuicksyRefresh() {
                Log.d(Config.LOGTAG, "user requested to refresh")
                if (AbstractQuickConversationsService.isQuicksy() && xmppConnectionService != null) {
                    xmppConnectionService.getQuickConversationsService().considerSyncBackground(true)
                }
            }
        }

    private fun prefilledJidFromSearch(): String? {
        val searchString = mSearchEditText?.text?.toString()
        return if (isValidJid(searchString)) Jid.of(searchString).toString() else null
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        val pendingIntent = pendingViewIntent.peek()
        savedInstanceState.putParcelable("intent", pendingIntent ?: getIntent())
        savedInstanceState.putBoolean("requested_contacts_permission", mRequestedContactsPermission.get())
        savedInstanceState.putBoolean("opened_fab", mOpenedFab.get())
        savedInstanceState.putBoolean("created_by_view_intent", createdByViewIntent)
        val searchView = mMenuSearchView
        if (searchView != null && searchView.isActionViewExpanded) {
            savedInstanceState.putString("search", mSearchEditText?.text?.toString())
        }
        super.onSaveInstanceState(savedInstanceState)
    }

    override fun onStart() {
        super.onStart()
        if (pendingViewIntent.peek() == null) {
            if (askForContactsPermissions()) {
                return
            }
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_POST_NOTIFICATION)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (xmppConnectionServiceBound) {
            processViewIntent(intent)
        } else {
            pendingViewIntent.push(intent)
        }
        setIntent(createLauncherIntent(this))
    }

    protected fun openConversationForContact(contact: Contact) {
        val conversation =
            xmppConnectionService.findOrCreateConversation(
                contact.getAccount(), contact.getAddress(), false, true
            )
        SoftKeyboardUtils.hideSoftKeyboard(this)
        switchToConversation(conversation)
    }

    protected fun deleteContact(contact: Contact) {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setNegativeButton(R.string.cancel, null)
        builder.setTitle(R.string.action_delete_contact)
        builder.setMessage(
            JidDialog.style(this, R.string.remove_contact_text, contact.getAddress().toString())
        )
        builder.setPositiveButton(R.string.delete) { _, _ ->
            xmppConnectionService.deleteContactOnServer(contact)
            filter(mSearchEditText?.text?.toString())
        }
        builder.create().show()
    }

    protected fun deleteConference(bookmark: Bookmark) {
        val conversation = xmppConnectionService.find(bookmark)
        val hasConversation = conversation != null
        val builder = MaterialAlertDialogBuilder(this)
        builder.setNegativeButton(R.string.cancel, null)
        builder.setTitle(R.string.delete_bookmark)
        if (hasConversation) {
            builder.setMessage(
                JidDialog.style(this, R.string.remove_bookmark_and_close, bookmark.getAddress().toString())
            )
        } else {
            builder.setMessage(
                JidDialog.style(this, R.string.remove_bookmark, bookmark.getAddress().toString())
            )
        }
        builder.setPositiveButton(if (hasConversation) R.string.delete_and_close else R.string.delete) { _, _ ->
            val account = bookmark.getAccount()
            xmppConnectionService.deleteBookmark(account, bookmark)
            if (conversation != null) {
                xmppConnectionService.archiveConversation(conversation)
            }
            filter(mSearchEditText?.text?.toString())
        }
        builder.create().show()
    }

    @SuppressLint("InflateParams")
    protected fun showCreateContactDialog(prefilledJid: String?, invite: Invite?) {
        val ft = getSupportFragmentManager().beginTransaction()
        val prev = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG)
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)
        val dialog =
            EnterJidDialog.newInstance(
                mActivatedAccounts,
                getString(R.string.add_contact),
                getString(R.string.add),
                prefilledJid,
                invite?.account,
                invite == null || !invite.uri.hasOmemoFingerprints(),
                true,
                true,
            )

        dialog.setOnEnterJidDialogPositiveListener { accountJid, contactJid ->
            if (!xmppConnectionServiceBound) {
                return@setOnEnterJidDialogPositiveListener false
            }

            val account = xmppConnectionService.findAccountByJid(accountJid)
            if (account == null) {
                return@setOnEnterJidDialogPositiveListener true
            }

            val contact = account.getRoster().getContact(contactJid)
            if (invite != null && invite.uri.getName() != null) {
                contact.setServerName(invite.uri.getName())
            }
            if (contact.isSelf()) {
                switchToConversation(contact)
                true
            } else if (contact.showInRoster()) {
                throw EnterJidDialog.JidError(getString(R.string.contact_already_exists))
            } else {
                val preAuth = invite?.uri?.getParameter(MiniUri.Xmpp.PARAMETER_PRE_AUTH)
                xmppConnectionService.createContact(contact, preAuth)
                if (invite != null && invite.uri.hasOmemoFingerprints()) {
                    xmppConnectionService.verifyFingerprints(contact, invite.uri.getOmemoFingerprints())
                }
                switchToConversationDoNotAppend(contact, invite?.uri?.getBody())
                true
            }
        }
        dialog.show(ft, FRAGMENT_TAG_DIALOG)
    }

    @SuppressLint("InflateParams")
    protected fun showJoinConferenceDialog(prefilledJid: String?) {
        val ft = getSupportFragmentManager().beginTransaction()
        val prev = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG)
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)
        val joinConferenceFragment = JoinConferenceDialog.newInstance(prefilledJid, mActivatedAccounts)
        joinConferenceFragment.show(ft, FRAGMENT_TAG_DIALOG)
    }

    private fun showCreatePrivateGroupChatDialog() {
        val ft = getSupportFragmentManager().beginTransaction()
        val prev = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG)
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)
        val createConferenceFragment = CreatePrivateGroupChatDialog.newInstance(mActivatedAccounts)
        createConferenceFragment.show(ft, FRAGMENT_TAG_DIALOG)
    }

    private fun showPublicChannelDialog() {
        val ft = getSupportFragmentManager().beginTransaction()
        val prev = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG)
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)
        val dialog = CreatePublicChannelDialog.newInstance(mActivatedAccounts)
        dialog.show(ft, FRAGMENT_TAG_DIALOG)
    }

    protected fun switchToConversation(contact: Contact) {
        val conversation =
            xmppConnectionService.findOrCreateConversation(
                contact.getAccount(), contact.getAddress(), false, true
            )
        switchToConversation(conversation)
    }

    protected fun switchToConversationDoNotAppend(contact: Contact, body: String?) {
        val conversation =
            xmppConnectionService.findOrCreateConversation(
                contact.getAccount(), contact.getAddress(), false, true
            )
        switchToConversationDoNotAppend(conversation, body)
    }

    override fun invalidateOptionsMenu() {
        val isExpanded = mMenuSearchView != null && mMenuSearchView!!.isActionViewExpanded
        val text = mSearchEditText?.text?.toString() ?: ""
        if (isExpanded) {
            mInitialSearchValue.push(text)
            oneShotKeyboardSuppress.set(true)
        }
        super.invalidateOptionsMenu()
    }

    private fun updateSearchViewHint() {
        val searchEditText = mSearchEditText ?: return
        if (composeState.currentTab.intValue == 0) {
            searchEditText.setHint(R.string.search_contacts)
            searchEditText.contentDescription = getString(R.string.search_contacts)
        } else {
            searchEditText.setHint(R.string.search_group_chats)
            searchEditText.contentDescription = getString(R.string.search_group_chats)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        getMenuInflater().inflate(R.menu.start_conversation, menu)
        AccountUtils.showHideMenuItems(menu)
        val menuHideOffline = menu.findItem(R.id.action_hide_offline)
        if (AbstractQuickConversationsService.isQuicksy()) {
            menuHideOffline.isVisible = false
        } else {
            menuHideOffline.isVisible = true
            menuHideOffline.isChecked = mHideOfflineContacts
        }
        val searchView = menu.findItem(R.id.action_search)
        mMenuSearchView = searchView
        searchView.setOnActionExpandListener(mOnActionExpandListener)
        val searchActionView = searchView.actionView
        val searchEditText = searchActionView?.findViewById<EditText>(R.id.search_field)
        mSearchEditText = searchEditText
        searchEditText?.addTextChangedListener(TextChangeListener(this::filter))
        searchEditText?.setOnEditorActionListener(mSearchDone)
        val initialSearchValue = mInitialSearchValue.pop()
        if (initialSearchValue != null) {
            searchView.expandActionView()
            searchEditText?.append(initialSearchValue)
            filter(initialSearchValue)
        }
        updateSearchViewHint()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false
        }
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_hide_offline -> {
                mHideOfflineContacts = !item.isChecked
                getPreferences().edit().putBoolean("hide_offline", mHideOfflineContacts).apply()
                mSearchEditText?.let { filter(it.text.toString()) }
                invalidateOptionsMenu()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SEARCH && !event.isLongPress) {
            openSearch()
            return true
        }
        val c = event.unicodeChar
        if (c > 32) {
            val searchEditText = mSearchEditText
            if (searchEditText != null && !searchEditText.isFocused) {
                openSearch()
                searchEditText.append(c.toChar().toString())
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun openSearch() {
        mMenuSearchView?.expandActionView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val intent = data
        if (resultCode == RESULT_OK && intent != null) {
            if (xmppConnectionServiceBound) {
                mPostponedActivityResult = null
                if (requestCode == REQUEST_CREATE_CONFERENCE) {
                    val account = extractAccount(intent)
                    val name = intent.getStringExtra(ChooseContactActivity.EXTRA_GROUP_CHAT_NAME)
                    val addresses = ChooseContactActivity.extractJabberIds(intent)
                    if (account == null || addresses.isEmpty()) {
                        return
                    }
                    val future = xmppConnectionService.createAdhocConference(account, name, addresses)
                    Futures.addCallback(future, adhocCallback, ContextCompat.getMainExecutor(this))
                }
            } else {
                mPostponedActivityResult = Pair(requestCode, intent)
            }
        }
        super.onActivityResult(requestCode, requestCode, intent)
    }

    private fun askForContactsPermissions(): Boolean {
        if (!AbstractQuickConversationsService.isContactListIntegration(this)) {
            return false
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return false
        }
        if (mRequestedContactsPermission.compareAndSet(false, true)) {
            val permissionBuilder = ImmutableList.Builder<String>()
            permissionBuilder.add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionBuilder.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val permission = permissionBuilder.build().toTypedArray()
            val consent =
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                    .getString(PREF_KEY_CONTACT_INTEGRATION_CONSENT, null)
            val requiresConsent =
                (AbstractQuickConversationsService.isQuicksy() || AbstractQuickConversationsService.isPlayStoreFlavor()) &&
                    "agreed" != consent
            if (requiresConsent && "declined" == consent) {
                Log.d(Config.LOGTAG, "not asking for contacts permission because consent has been declined")
                return false
            }
            if (requiresConsent || shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                val builder = MaterialAlertDialogBuilder(this)
                val requestPermission = AtomicBoolean(false)
                if (AbstractQuickConversationsService.isQuicksy()) {
                    builder.setTitle(R.string.quicksy_wants_your_consent)
                    builder.setMessage(Html.fromHtml(getString(R.string.sync_with_contacts_quicksy_static)))
                } else {
                    builder.setTitle(R.string.sync_with_contacts)
                    builder.setMessage(
                        getString(R.string.sync_with_contacts_long, getString(R.string.app_name))
                    )
                }
                val confirmButtonText =
                    if (requiresConsent) R.string.agree_and_continue else R.string.next
                builder.setPositiveButton(confirmButtonText) { _, _ ->
                    if (requiresConsent) {
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                            .edit()
                            .putString(PREF_KEY_CONTACT_INTEGRATION_CONSENT, "agreed")
                            .apply()
                    }
                    if (requestPermission.compareAndSet(false, true)) {
                        requestPermissions(permission, REQUEST_SYNC_CONTACTS)
                    }
                }
                if (requiresConsent) {
                    builder.setNegativeButton(R.string.decline) { _, _ ->
                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                            .edit()
                            .putString(PREF_KEY_CONTACT_INTEGRATION_CONSENT, "declined")
                            .apply()
                    }
                } else {
                    builder.setOnDismissListener {
                        if (requestPermission.compareAndSet(false, true)) {
                            requestPermissions(permission, REQUEST_SYNC_CONTACTS)
                        }
                    }
                }
                builder.setCancelable(requiresConsent)
                val dialog = builder.create()
                dialog.setCanceledOnTouchOutside(requiresConsent)
                dialog.setOnShowListener {
                    val tv = dialog.findViewById<TextView>(android.R.id.message)
                    tv?.movementMethod = LinkMovementMethod.getInstance()
                }
                dialog.show()
            } else {
                requestPermissions(permission, REQUEST_SYNC_CONTACTS)
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == REQUEST_SYNC_CONTACTS && xmppConnectionServiceBound) {
                if (AbstractQuickConversationsService.isQuicksy()) {
                    setRefreshing(true)
                }
                xmppConnectionService.loadPhoneContacts()
                xmppConnectionService.startContactObserver()
            }
        }
    }

    private fun configureHomeButton() {
        val actionBar = getSupportActionBar() ?: return
        actionBar.setDisplayHomeAsUpEnabled(!isTaskRoot)
    }

    override fun onBackendConnected() {
        if (AbstractQuickConversationsService.isContactListIntegration(this) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED)
        ) {
            xmppConnectionService.getQuickConversationsService().considerSyncBackground(false)
        }
        val postponed = mPostponedActivityResult
        if (postponed != null) {
            onActivityResult(postponed.first, RESULT_OK, postponed.second)
            mPostponedActivityResult = null
        }
        mActivatedAccounts.clear()
        mActivatedAccounts.addAll(AccountUtils.getEnabledAccounts(xmppConnectionService))
        configureHomeButton()
        val intent = pendingViewIntent.pop()
        if (intent != null && processViewIntent(intent)) {
            filter(null)
        } else {
            val searchEditText = mSearchEditText
            if (searchEditText != null) {
                filter(searchEditText.text.toString())
            } else {
                filter(null)
            }
        }
        val fragment = getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG_DIALOG)
        if (fragment is OnBackendConnected) {
            Log.d(Config.LOGTAG, "calling on backend connected on dialog")
            fragment.onBackendConnected()
        }
        if (AbstractQuickConversationsService.isQuicksy()) {
            setRefreshing(xmppConnectionService.getQuickConversationsService().isSynchronizing())
        }
        if (AbstractQuickConversationsService.isConversations() &&
            AccountUtils.hasEnabledAccounts(xmppConnectionService) &&
            contacts.isEmpty() &&
            conferences.isEmpty() &&
            mOpenedFab.compareAndSet(false, true)
        ) {
            composeState.fabExpanded.value = true
        }
    }

    protected fun processViewIntent(intent: Intent): Boolean {
        val inviteUri = MiniUri.getOrNull(intent.getStringExtra(EXTRA_INVITE_URI))
        Log.d(Config.LOGTAG, "inviteUri: $inviteUri")
        if (inviteUri is MiniUri.Xmpp && inviteUri.isAddress()) {
            val invite = Invite(inviteUri, intent.getStringExtra(EXTRA_ACCOUNT), false, false)
            return handleJid(invite)
        }
        val action = intent.action ?: return false
        when (action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val uri = MiniUri.getOrNull(intent.data)
                return if (uri is MiniUri.Xmpp && uri.isAddress()) {
                    val invite =
                        Invite(
                            uri,
                            intent.getStringExtra(EXTRA_ACCOUNT),
                            intent.getBooleanExtra("scanned", false),
                            intent.getBooleanExtra("force_dialog", false),
                        )
                    handleJid(invite)
                } else {
                    false
                }
            }
        }
        return false
    }

    private fun handleJid(invite: Invite): Boolean {
        val contacts = xmppConnectionService.findContacts(invite.uri.asJid(), invite.account)
        if (invite.uri.isAction(MiniUri.Xmpp.ACTION_JOIN)) {
            val muc = xmppConnectionService.findFirstMuc(invite.uri.asJid())
            return if (muc != null && !invite.forceDialog) {
                switchToConversationDoNotAppend(muc, invite.uri.getBody())
                true
            } else {
                showJoinConferenceDialog(invite.uri.asJid().asBareJid().toString())
                false
            }
        } else if (contacts.isEmpty()) {
            showCreateContactDialog(invite.uri.asJid().toString(), invite)
            return false
        } else if (contacts.size == 1) {
            val contact = contacts[0]
            if (!invite.scanned && invite.uri.hasOmemoFingerprints()) {
                displayVerificationWarningDialog(contact, invite)
            } else {
                if (invite.uri.hasOmemoFingerprints()) {
                    if (xmppConnectionService.verifyFingerprints(contact, invite.uri.getOmemoFingerprints())) {
                        Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show()
                    }
                }
                if (invite.account != null) {
                    xmppConnectionService.getShortcutService().report(contact)
                }
                switchToConversationDoNotAppend(contact, invite.uri.getBody())
            }
            return true
        } else {
            val searchView = mMenuSearchView
            if (searchView != null) {
                searchView.expandActionView()
                mSearchEditText?.setText("")
                mSearchEditText?.append(invite.uri.asJid().toString())
                filter(invite.uri.asJid().toString())
            } else {
                mInitialSearchValue.push(invite.uri.asJid().toString())
            }
            return true
        }
    }

    private fun displayVerificationWarningDialog(contact: Contact, invite: Invite) {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.verify_omemo_keys)
        val view = getLayoutInflater().inflate(R.layout.dialog_verify_fingerprints, null)
        val isTrustedSource = view.findViewById<CheckBox>(R.id.trusted_source)
        val warning = view.findViewById<TextView>(R.id.warning)
        warning.text =
            JidDialog.style(
                this,
                R.string.verifying_omemo_keys_trusted_source,
                contact.getAddress().asBareJid().toString(),
                contact.getDisplayName(),
            )
        builder.setView(view)
        builder.setPositiveButton(R.string.confirm) { _, _ ->
            if (isTrustedSource.isChecked && invite.uri.hasOmemoFingerprints()) {
                xmppConnectionService.verifyFingerprints(contact, invite.uri.getOmemoFingerprints())
            }
            switchToConversationDoNotAppend(contact, invite.uri.getBody())
        }
        builder.setNegativeButton(R.string.cancel) { _, _ -> this@StartConversationActivity.finish() }
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnCancelListener { this@StartConversationActivity.finish() }
        dialog.show()
    }

    protected fun filter(needle: String?) {
        if (xmppConnectionServiceBound) {
            filterContacts(needle)
            filterConferences(needle)
        }
    }

    protected fun filterContacts(needle: String?) {
        contacts.clear()
        val accounts = xmppConnectionService.getAccounts()
        val showOffline = !mHideOfflineContacts
        for (account in accounts) {
            if (account.isEnabled()) {
                for (contact in account.getRoster().getContacts()) {
                    val s = contact.getShownStatus()
                    if (contact.showInContactList() &&
                        contact.match(needle) &&
                        (showOffline || s.compareTo(im.conversations.android.xmpp.model.stanza.Presence.Availability.OFFLINE) < 0)
                    ) {
                        contacts.add(contact)
                    }
                }
            }
        }
        contacts.sort()
        composeState.updateContacts(contacts)
    }

    protected fun filterConferences(needle: String?) {
        conferences.clear()
        for (account in xmppConnectionService.getAccounts()) {
            if (account.isEnabled()) {
                for (bookmark in account.getXmppConnection().getManager(BookmarkManager::class.java).getBookmarks()) {
                    if (bookmark.match(needle)) {
                        conferences.add(bookmark)
                    }
                }
            }
        }
        conferences.sort()
        composeState.updateConferences(conferences)
    }

    override fun OnUpdateBlocklist(status: OnUpdateBlocklist.Status) {
        refreshUi()
    }

    override fun refreshUiReal() {
        mSearchEditText?.let { filter(it.text.toString()) }
        configureHomeButton()
        if (AbstractQuickConversationsService.isQuicksy()) {
            setRefreshing(xmppConnectionService.getQuickConversationsService().isSynchronizing())
        }
    }

    override fun onCreateDialogPositiveClick(
        spinner: AutoCompleteTextView,
        name: String,
        membersOnly: Boolean,
    ) {
        if (!xmppConnectionServiceBound) {
            return
        }
        val account = getSelectedAccount(this, spinner) ?: return
        if (membersOnly) {
            val intent = Intent(getApplicationContext(), ChooseContactActivity::class.java)
            intent.putExtra(ChooseContactActivity.EXTRA_SHOW_ENTER_JID, false)
            intent.putExtra(ChooseContactActivity.EXTRA_SELECT_MULTIPLE, true)
            intent.putExtra(ChooseContactActivity.EXTRA_GROUP_CHAT_NAME, name.trim())
            intent.putExtra(ChooseContactActivity.EXTRA_ACCOUNT, account.getJid().asBareJid().toString())
            intent.putExtra(ChooseContactActivity.EXTRA_TITLE_RES_ID, R.string.choose_participants)
            startActivityForResult(intent, REQUEST_CREATE_CONFERENCE)
        } else {
            mToast = Toast.makeText(this, R.string.creating_group_chat, Toast.LENGTH_LONG)
            mToast?.show()
            val future =
                account.getXmppConnection().getManager(MultiUserChatManager::class.java)
                    .createPublicGroup(name.trim())
            Futures.addCallback(future, adhocCallback, ContextCompat.getMainExecutor(this))
        }
    }

    override fun onJoinDialogPositiveClick(
        dialog: Dialog,
        spinner: AutoCompleteTextView,
        layout: TextInputLayout,
        jid: AutoCompleteTextView,
    ) {
        if (!xmppConnectionServiceBound) {
            return
        }
        val account = getSelectedAccount(this, spinner) ?: return
        val input = CharSequences.nullToEmpty(jid.text).trim()
        val asUri = MiniUri.getXmppUriOrNull(input)
        val conferenceJid: Jid
        if (asUri != null && asUri.isAddress() && asUri.isAction(MiniUri.Xmpp.ACTION_JOIN)) {
            conferenceJid = asUri.asJid()
        } else {
            conferenceJid =
                try {
                    Jid.ofUserInput(input)
                } catch (e: IllegalArgumentException) {
                    layout.error = getString(R.string.invalid_jid)
                    return
                }
        }
        val existingBookmark =
            account.getXmppConnection().getManager(BookmarkManager::class.java).getBookmark(conferenceJid)
        if (existingBookmark != null) {
            openConversationsForBookmark(existingBookmark)
        } else {
            val nick = Bookmark.nickOfAddress(account, conferenceJid)
            // ImmutableBookmark is annotation-processor-generated, so it isn't visible from
            // Kotlin — go through the small Java bridge instead of calling the builder directly.
            val bookmark =
                eu.siacs.conversations.xmpp.manager.BookmarkFactory.create(
                    account, conferenceJid.asBareJid(), null, true, nick, null,
                )
            xmppConnectionService.createBookmark(account, bookmark)
            val conversation =
                xmppConnectionService.findOrCreateConversation(account, conferenceJid, true, true, true)
            switchToConversation(conversation)
        }
        dialog.dismiss()
    }

    override fun onConversationUpdate() {
        refreshUi()
    }

    private fun setRefreshing(refreshing: Boolean) {
        composeState.refreshing.value = refreshing
    }

    override fun onCreatePublicChannel(account: Account, name: String, address: Jid) {
        mToast = Toast.makeText(this, R.string.creating_channel, Toast.LENGTH_LONG)
        mToast?.show()
        val future =
            account.getXmppConnection().getManager(MultiUserChatManager::class.java)
                .createPublicChannel(address, name)
        Futures.addCallback(
            future,
            object : FutureCallback<Conversation> {
                override fun onSuccess(conversation: Conversation) {
                    hideToast()
                    switchToConversation(conversation)
                }

                override fun onFailure(t: Throwable) {
                    Log.d(Config.LOGTAG, "could not create channel", t)
                    replaceToast(getString(R.string.unable_to_set_channel_configuration))
                    // creation failed. this most likely means it existed. we can join it anyway
                    // if we ever decide not to join it the createPublicChannel call needs to
                    // archive it
                    val conversation = xmppConnectionService.find(account, address)
                    if (conversation != null) {
                        switchToConversation(conversation)
                    }
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
}
