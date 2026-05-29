package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.NonNull
import androidx.databinding.DataBindingUtil
import com.google.common.base.Strings
import com.google.common.collect.Collections2
import com.google.common.collect.Iterables
import com.google.common.collect.Ordering
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityMucUsersBinding
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.entities.MucOptions
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.ui.adapter.UserAdapter
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper
import java.util.Collections
import java.util.Locale
import java.util.Objects

class MucUsersActivity :
    XmppActivity(),
    XmppConnectionService.OnMucRosterUpdate,
    MenuItem.OnActionExpandListener,
    TextWatcher {

    private lateinit var userAdapter: UserAdapter

    private var mConversation: Conversation? = null

    private var mSearchEditText: EditText? = null

    private var allUsers: Collection<MucOptions.User> = Collections.emptyList()

    override fun refreshUiReal() {}

    override fun onBackendConnected() {
        val intent = getIntent()
        val uuid = intent?.getStringExtra("uuid")
        if (uuid != null) {
            mConversation = xmppConnectionService.findConversationByUuid(uuid)
        }
        loadAndSubmitUsers()
    }

    private fun loadAndSubmitUsers() {
        if (mConversation != null) {
            allUsers = mConversation!!.getMucOptions().getUsers()
            submitFilteredList(mSearchEditText?.text?.toString())
        }
    }

    private fun submitFilteredList(search: String?) {
        if (Strings.isNullOrEmpty(search)) {
            userAdapter.submitList(Ordering.natural<MucOptions.User>().immutableSortedCopy(allUsers))
        } else {
            val needle = search!!.lowercase(Locale.getDefault())
            userAdapter.submitList(
                Ordering.natural<MucOptions.User>()
                    .immutableSortedCopy(
                        Collections2.filter(this.allUsers) { user ->
                            contains(Objects.requireNonNull(user), needle)
                        }
                    )
            )
        }
    }

    override fun onContextItemSelected(@NonNull item: MenuItem): Boolean {
        if (!MucDetailsContextMenuHelper.onContextItemSelected(
                item,
                userAdapter.getSelectedUser(),
                this
            )
        ) {
            return super.onContextItemSelected(item)
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityMucUsersBinding =
            DataBindingUtil.setContentView(this, R.layout.activity_muc_users)
        setSupportActionBar(binding.toolbar)
        Activities.setStatusAndNavigationBarColors(this, binding.root)
        configureActionBar(supportActionBar, true)
        this.userAdapter = UserAdapter()
        binding.list.adapter = this.userAdapter
    }

    override fun onMucRosterUpdate() {
        loadAndSubmitUsers()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.muc_users_activity, menu)
        val menuSearchView = menu.findItem(R.id.action_search)
        val mSearchView = menuSearchView.actionView
        mSearchEditText = mSearchView!!.findViewById(R.id.search_field)
        mSearchEditText!!.addTextChangedListener(this)
        mSearchEditText!!.setHint(R.string.search_participants)
        menuSearchView.setOnActionExpandListener(this)
        return true
    }

    override fun onMenuItemActionExpand(@NonNull item: MenuItem): Boolean {
        mSearchEditText!!.post {
            mSearchEditText!!.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT)
        }
        return true
    }

    override fun onMenuItemActionCollapse(@NonNull item: MenuItem): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(
            mSearchEditText!!.windowToken,
            InputMethodManager.HIDE_IMPLICIT_ONLY
        )
        mSearchEditText!!.setText("")
        submitFilteredList("")
        return true
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable) {
        submitFilteredList(s.toString())
    }

    companion object {
        private fun contains(user: MucOptions.User, needle: String): Boolean {
            val name = user.displayName
            val resource = user.resource()
            val realAddress = user.getRealJid()
            val hats =
                Collections2.transform(user.hats) { h ->
                    Objects.requireNonNull(h).title().lowercase(Locale.getDefault())
                }
            return name.lowercase(Locale.getDefault()).contains(needle) ||
                Strings.nullToEmpty(resource).lowercase(Locale.getDefault()).contains(needle) ||
                (realAddress != null && realAddress.toString().contains(needle)) ||
                Iterables.any(hats) { h -> Objects.requireNonNull(h).contains(needle) }
        }
    }
}
