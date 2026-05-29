package eu.siacs.conversations.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityChooseContactBinding
import eu.siacs.conversations.entities.ListItem
import eu.siacs.conversations.ui.adapter.ListItemAdapter
import java.util.ArrayList

abstract class AbstractSearchableListItemActivity : XmppActivity(), TextView.OnEditorActionListener {

    protected lateinit var binding: ActivityChooseContactBinding
    private val listItems: MutableList<ListItem> = ArrayList()
    private lateinit var mListItemsAdapter: ArrayAdapter<ListItem>
    private var mSearchEditText: EditText? = null

    private val mOnActionExpandListener = object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
            mSearchEditText?.post {
                mSearchEditText?.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(mSearchEditText, InputMethodManager.SHOW_IMPLICIT)
            }
            return true
        }

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(mSearchEditText?.windowToken, InputMethodManager.HIDE_IMPLICIT_ONLY)
            mSearchEditText?.setText("")
            filterContacts()
            return true
        }
    }

    private val mSearchTextWatcher = object : TextWatcher {
        override fun afterTextChanged(editable: Editable) {
            filterContacts(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    val listView: ListView
        get() = binding.chooseContactList

    fun getListItems(): MutableList<ListItem> = listItems

    val searchEditText: EditText?
        get() = mSearchEditText

    val listItemAdapter: ArrayAdapter<ListItem>
        get() = mListItemsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_choose_contact)
        Activities.setStatusAndNavigationBarColors(this, binding.root)
        setSupportActionBar(binding.toolbar)
        configureActionBar(supportActionBar)
        this.binding.chooseContactList.isFastScrollEnabled = true
        mListItemsAdapter = ListItemAdapter(this, listItems)
        this.binding.chooseContactList.adapter = mListItemsAdapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.choose_contact, menu)
        val menuSearchView = menu.findItem(R.id.action_search)
        val mSearchView: View = menuSearchView.actionView!!
        mSearchEditText = mSearchView.findViewById(R.id.search_field)
        mSearchEditText?.addTextChangedListener(mSearchTextWatcher)
        mSearchEditText?.setHint(R.string.search_contacts)
        mSearchEditText?.setOnEditorActionListener(this)
        menuSearchView.setOnActionExpandListener(mOnActionExpandListener)
        return true
    }

    protected fun filterContacts() {
        val needle = mSearchEditText?.text?.toString()
        if (needle != null && needle.isNotEmpty()) {
            filterContacts(needle)
        } else {
            filterContacts(null)
        }
    }

    protected abstract fun filterContacts(needle: String?)

    override fun onBackendConnected() {
        filterContacts()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean = false
}
