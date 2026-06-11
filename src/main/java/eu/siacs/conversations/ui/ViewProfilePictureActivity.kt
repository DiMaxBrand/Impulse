package eu.siacs.conversations.ui

import android.net.Uri
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityViewProfilePictureBinding
import eu.siacs.conversations.persistance.FileBackend

class ViewProfilePictureActivity : ActionBarActivity() {

    companion object {
        const val EXTRA_DISPLAY_NAME = "eu.siacs.conversations.extra.DISPLAY_NAME"
    }

    private lateinit var binding: ActivityViewProfilePictureBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_view_profile_picture)
        Activities.setStatusAndNavigationBarColors(this, binding.root, false, false)

        setSupportActionBar(binding.toolbar)
        configureActionBar(supportActionBar)
    }

    override fun onStart() {
        super.onStart()
        val intent = getIntent() ?: return
        val uri = intent.data ?: return
        val avatar = uri.schemeSpecificPart ?: return
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        val file = FileBackend.getAvatarFile(this, avatar)
        this.binding.imageView.setImageURI(Uri.fromFile(file))
        setTitle(displayName)
    }
}
