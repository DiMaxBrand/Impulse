package eu.siacs.conversations.ui

import android.view.MenuItem
import androidx.appcompat.app.ActionBar

abstract class ActionBarActivity : BaseActivity() {
    companion object {
        @JvmStatic
        fun configureActionBar(actionBar: ActionBar?) = configureActionBar(actionBar, true)

        @JvmStatic
        fun configureActionBar(actionBar: ActionBar?, upNavigation: Boolean) {
            if (actionBar != null) {
                actionBar.setHomeButtonEnabled(upNavigation)
                actionBar.setDisplayHomeAsUpEnabled(upNavigation)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}
