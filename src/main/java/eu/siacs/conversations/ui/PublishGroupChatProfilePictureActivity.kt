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

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import eu.siacs.conversations.databinding.ActivityPublishProfilePictureBinding
import eu.siacs.conversations.entities.Conversation
import eu.siacs.conversations.ui.interfaces.OnAvatarPublication
import eu.siacs.conversations.ui.util.PendingItem

class PublishGroupChatProfilePictureActivity : XmppActivity(), OnAvatarPublication {
    private val pendingConversationUuid = PendingItem<String>()
    private lateinit var binding: ActivityPublishProfilePictureBinding
    private var conversation: Conversation? = null
    private var uri: Uri? = null

    val cropImage: ActivityResultLauncher<CropImageContractOptions> =
        registerForActivityResult(CropImageContract()) { cropResult ->
            if (cropResult.isSuccessful) {
                onAvatarPicked(cropResult.uriContent)
            }
        }

    override fun refreshUiReal() {}

    override fun onBackendConnected() {
        val uuid = pendingConversationUuid.pop()
        if (uuid != null) {
            this.conversation = xmppConnectionService.findConversationByUuid(uuid)
        }
        if (this.conversation == null) {
            return
        }
        reloadAvatar()
    }

    private fun reloadAvatar() {
        val size = getResources().getDimension(R.dimen.publish_avatar_size).toInt()
        val bitmap =
            if (uri == null) {
                xmppConnectionService.getAvatarService().get(conversation, size)
            } else {
                Log.d(Config.LOGTAG, "loading $uri into preview")
                xmppConnectionService.getFileBackend().cropCenterSquare(uri, size)
            }
        this.binding.accountImage.setImageBitmap(bitmap)
        this.binding.publishButton.isEnabled = uri != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.binding =
            DataBindingUtil.setContentView(this, R.layout.activity_publish_profile_picture)
        this.binding.contactOnly.visibility = View.GONE
        Activities.setStatusAndNavigationBarColors(this, binding.root)
        setSupportActionBar(this.binding.toolbar)
        configureActionBar(supportActionBar)
        this.binding.cancelButton.setOnClickListener { this.finish() }
        this.binding.secondaryHint.visibility = View.GONE
        this.binding.accountImage.setOnClickListener { pickAvatar() }

        val intent = getIntent()
        val uuid = intent?.getStringExtra("uuid")
        if (uuid != null) {
            pendingConversationUuid.push(uuid)
        }
        this.binding.publishButton.isEnabled = uri != null
        this.binding.publishButton.setOnClickListener { v -> publish(v) }
    }

    private fun publish(view: View) {
        binding.publishButton.setText(R.string.publishing)
        binding.publishButton.isEnabled = false
        xmppConnectionService.publishMucAvatar(conversation, uri, this)
    }

    fun pickAvatar() {
        this.cropImage.launch(
            CropImageContractOptions(null, PublishProfilePictureActivity.getCropImageOptions())
        )
    }

    private fun onAvatarPicked(uri: Uri?) {
        this.uri = uri
        if (xmppConnectionServiceBound) {
            reloadAvatar()
        }
    }

    override fun onAvatarPublicationSucceeded() {
        runOnUiThread {
            Toast.makeText(this, R.string.avatar_has_been_published, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onAvatarPublicationFailed(@StringRes res: Int) {
        runOnUiThread {
            Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
            this.binding.publishButton.setText(R.string.publish)
            this.binding.publishButton.isEnabled = true
        }
    }
}
