package eu.siacs.conversations.ui

import android.os.Bundle
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import androidx.databinding.DataBindingUtil
import de.gultsch.common.Linkify
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityAboutBinding
import eu.siacs.conversations.ui.text.FixedURLSpan

class AboutActivity : ActionBarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding: ActivityAboutBinding = DataBindingUtil.setContentView(this, R.layout.activity_about)
        val text = SpannableString(getString(R.string.pref_about_message))
        Linkify.addLinks(text)
        FixedURLSpan.fix(text)
        binding.about.setText(text)
        binding.about.movementMethod = LinkMovementMethod.getInstance()
        Activities.setStatusAndNavigationBarColors(this, binding.root)
        setSupportActionBar(binding.toolbar)
        ActionBarActivity.configureActionBar(supportActionBar)
        title = getString(R.string.title_activity_about_x, getString(R.string.app_name))
    }
}
