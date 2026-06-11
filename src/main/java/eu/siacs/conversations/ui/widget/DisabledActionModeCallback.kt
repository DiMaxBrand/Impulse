package eu.siacs.conversations.ui.widget

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem

class DisabledActionModeCallback : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        return false
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        return false
    }

    override fun onDestroyActionMode(mode: ActionMode) {
    }
}
