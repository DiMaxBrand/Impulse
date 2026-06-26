/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.NonNull
import androidx.core.view.MenuProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.material.search.SearchView
import de.gultsch.common.MiniUri
import de.gultsch.common.Patterns
import eu.siacs.conversations.BuildConfig
import eu.siacs.conversations.Config
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.FragmentConversationsOverviewBinding
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.ui.activity.SettingsActivity
import eu.siacs.conversations.ui.adapter.SearchSuggestionAdapter
import eu.siacs.conversations.ui.interfaces.OnConversationSelected
import eu.siacs.conversations.ui.widget.AccountPickerDialog
import eu.siacs.conversations.utils.AccountUtils
import eu.siacs.conversations.utils.CharSequences
import eu.siacs.conversations.utils.XmppUriLauncher
import eu.siacs.conversations.xmpp.manager.BookmarkManager
import eu.siacs.conversations.xmpp.manager.RosterManager
import im.conversations.android.model.SearchSuggestion
import im.conversations.android.provider.SearchSuggestionProvider
import com.google.common.collect.Collections2
import com.google.common.collect.Iterables
import eu.siacs.conversations.services.AbstractQuickConversationsService

class ConversationsOverviewFragment : XmppFragment() {

    private val conversations: MutableList<Conversation> = mutableListOf()
    private var binding: FragmentConversationsOverviewBinding? = null
    private lateinit var composeState: ConversationListState
    private var searchSuggestionAdapter: SearchSuggestionAdapter? = null

    private val globalMenuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.fragment_global, menu)
            AccountUtils.showHideMenuItems(menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    true
                }
                R.id.action_accounts -> {
                    AccountUtils.launchManageAccounts(requireXmppActivity())
                    true
                }
                R.id.action_account -> {
                    AccountUtils.launchManageAccount(requireXmppActivity())
                    true
                }
                else -> false
            }
        }
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.fragment_conversations_overview, menu)
            menu.findItem(R.id.action_privacy_policy).isVisible =
                BuildConfig.PRIVACY_POLICY != null && AbstractQuickConversationsService.isPlayStoreFlavor()
            menu.findItem(R.id.action_qr_codes).isVisible = true
            menu.findItem(R.id.action_scan_qr_code).isVisible =
                requireXmppActivity().isCameraFeatureAvailable()
            menu.findItem(R.id.action_show_qr_code).isVisible =
                AccountPickerDialog.Enabled(requireXmppActivity()).hasAnyAccounts()
            menu.findItem(R.id.action_easy_invite).isVisible =
                AccountPickerDialog.EasyInvite(requireXmppActivity()).hasAnyAccounts()
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.action_easy_invite -> {
                    selectAccountToStartEasyInvite()
                    true
                }
                R.id.action_show_qr_code -> {
                    AccountPickerDialog.Enabled(requireXmppActivity())
                        .pick { requireXmppActivity().showQrCode(it) }
                    true
                }
                R.id.action_scan_qr_code -> {
                    (requireActivity() as? QrCodeScanningActivity)?.requestQrCodeScan()
                    true
                }
                R.id.action_privacy_policy -> {
                    openPrivacyPolicy()
                    true
                }
                else -> false
            }
        }
    }

    private val searchViewOnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding?.searchView?.hide()
        }
    }

    private fun openPrivacyPolicy() {
        val policy = BuildConfig.PRIVACY_POLICY ?: return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(policy))
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {}

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        searchSuggestionAdapter = null
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, searchViewOnBackPressedCallback)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_conversations_overview, container, false)
        val binding = binding!!

        binding.searchBar.addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        binding.searchBar.addMenuProvider(globalMenuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        binding.searchView.editText.setOnEditorActionListener { v, _, _ ->
            startSearch(CharSequences.nullToEmpty(v.text))
            binding.searchView.hide()
            true
        }
        binding.searchView.editText.addTextChangedListener(TextChangeListener(::submitSearchSuggestion))
        binding.searchView.addTransitionListener { _, _, newState ->
            val isShowing = newState == SearchView.TransitionState.SHOWING ||
                newState == SearchView.TransitionState.SHOWN
            searchViewOnBackPressedCallback.isEnabled = isShowing
        }
        binding.fab.setOnClickListener { StartConversationActivity.launch(activity) }

        composeState = ConversationListState()
        ConversationListHelper.setup(
            binding.list,
            composeState,
            { c ->
                (activity as? OnConversationSelected)?.onConversationSelected(c)
                    ?: Log.w(TAG, "Activity does not implement OnConversationSelected")
            },
            binding.fab,
        )

        searchSuggestionAdapter = SearchSuggestionAdapter()
        binding.searchSuggestionList.adapter = searchSuggestionAdapter
        searchSuggestionAdapter!!.setOnSearchSuggestionClicked(::executeSuggestion)

        return binding.root
    }

    private fun startSearch(term: CharSequence) {
        val intent = Intent(requireContext(), SearchActivity::class.java)
        intent.putExtra(SearchActivity.EXTRA_SEARCH_TERM, term.toString())
        startActivity(intent)
    }

    private fun submitSearchSuggestion(raw: String) {
        val search = raw.trim()
        if (search.isEmpty()) {
            searchSuggestionAdapter?.submitList(emptyList())
            return
        }
        val suggestions = buildList<SearchSuggestion> {
            add(SearchSuggestion.Text(search))
            if (Patterns.URI_GENERIC.matcher(search).matches()) {
                val xmppUri = MiniUri.getXmppUriOrNull(search)
                if (xmppUri != null && xmppUri.isAddress) {
                    add(SearchSuggestion.Uri(xmppUri))
                }
            }
            val service = requireXmppActivity().xmppConnectionService
            if (service != null) {
                val provider = SearchSuggestionProvider(service.accounts)
                val sortable = provider.suggest(search)
                if (sortable.size <= 8 || search.length >= 3) {
                    addAll(sortable.sortedWith(Comparator { l, r -> l.address().compareTo(r.address()) }))
                }
            }
        }
        searchSuggestionAdapter?.submitList(suggestions)
    }

    private fun executeSuggestion(suggestion: SearchSuggestion) {
        when (suggestion) {
            is SearchSuggestion.Text -> {
                hideSearchView()
                startSearch(suggestion.text())
            }
            is SearchSuggestion.Uri -> {
                hideSearchView()
                XmppUriLauncher(requireContext(), true).launch(suggestion.uri())
            }
            is SearchSuggestion.Bookmark -> {
                val account = requireXmppActivity().xmppConnectionService
                    .findAccountByUuid(suggestion.uuid()) ?: return
                val bookmark = account.xmppConnection
                    .getManager(BookmarkManager::class.java)
                    .getBookmark(suggestion.address()) ?: return
                hideSearchView()
                requireXmppActivity().openConversationsForBookmark(bookmark)
            }
            is SearchSuggestion.Contact -> {
                val account = requireXmppActivity().xmppConnectionService
                    .findAccountByUuid(suggestion.uuid()) ?: return
                val contact = account.xmppConnection
                    .getManager(RosterManager::class.java)
                    .getContact(suggestion.address())
                val conversation = requireXmppActivity().xmppConnectionService
                    .findOrCreateConversation(contact.getAccount(), contact.getAddress(), false, true)
                hideSearchView()
                requireXmppActivity().switchToConversation(conversation)
            }
        }
    }

    private fun hideSearchView() {
        val searchView = binding?.searchView ?: return
        searchView.hide()
        if (!ConversationsActivity.isTabletView(requireActivity())) {
            searchView.setVisible(false)
            searchView.clearFocus()
        }
    }

    override fun onBackendConnected() {
        refresh()
    }

    override fun onStart() {
        super.onStart()
        if (requireXmppActivity().xmppConnectionService != null) {
            refresh()
        }
    }

    private fun selectAccountToStartEasyInvite() {
        AccountPickerDialog.EasyInvite(requireXmppActivity())
            .pick { EasyOnboardingInviteActivity.launch(it, requireContext()) }
    }

    override fun refresh() {
        val binding = binding
        if (binding == null || !::composeState.isInitialized) {
            Log.d(Config.LOGTAG, "ConversationsOverviewFragment.refresh() skipped because view binding or compose state was null")
            return
        }
        binding.searchBar.invalidateMenu()
        val service = requireXmppActivity().xmppConnectionService
        service.populateWithOrderedConversations(conversations)
        composeState.isConnecting.value = service.accounts.any { it.status == Account.State.CONNECTING }
        composeState.update(conversations)
    }

    companion object {
        private val TAG = ConversationsOverviewFragment::class.java.canonicalName

        @JvmStatic
        fun getSuggestion(activity: FragmentActivity): Conversation? {
            return getSuggestion(activity, null)
        }

        @JvmStatic
        fun getSuggestion(activity: FragmentActivity, exception: Conversation?): Conversation? {
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.main_fragment)
            val overviewFragment = fragment as? ConversationsOverviewFragment ?: return null
            return overviewFragment.conversations.firstOrNull { it !== exception }
        }
    }
}
