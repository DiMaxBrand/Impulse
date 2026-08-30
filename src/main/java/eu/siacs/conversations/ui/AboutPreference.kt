package eu.siacs.conversations.ui

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import eu.siacs.conversations.ui.activity.DeveloperOptionsActivity

/**
 * Hidden entry point: long-pressing this row opens Developer Options. Uses AndroidX's
 * [androidx.preference.Preference] (not the legacy `android.preference.Preference`) because
 * the settings screen is hosted by [androidx.preference.PreferenceFragmentCompat], which can
 * only inflate custom preferences from that hierarchy.
 */
class AboutPreference : Preference {
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.setOnLongClickListener {
            it.context.startActivity(Intent(it.context, DeveloperOptionsActivity::class.java))
            true
        }
    }
}
