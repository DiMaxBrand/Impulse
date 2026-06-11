package eu.siacs.conversations.ui.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.widget.ImageView
import androidx.annotation.DimenRes
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.entities.Message
import eu.siacs.conversations.services.AvatarService
import eu.siacs.conversations.ui.XmppActivity
import java.lang.ref.WeakReference
import java.util.concurrent.RejectedExecutionException

class AvatarWorkerTask(imageView: ImageView, @DimenRes private val size: Int) :
    AsyncTask<AvatarService.Avatar, Void, Bitmap>() {

    private val imageViewReference: WeakReference<ImageView> = WeakReference(imageView)
    private var avatar: AvatarService.Avatar? = null

    override fun doInBackground(vararg params: AvatarService.Avatar): Bitmap? {
        this.avatar = params[0]
        val activity = XmppActivity.find(imageViewReference)
            ?: return null
        return activity.avatarService()
            .get(avatar, activity.resources.getDimension(size).toInt(), isCancelled)
    }

    override fun onPostExecute(bitmap: Bitmap?) {
        if (bitmap != null && !isCancelled) {
            val imageView = imageViewReference.get()
            if (imageView != null) {
                imageView.setImageBitmap(bitmap)
                imageView.setBackgroundColor(0x00000000)
            }
        }
    }

    internal class AsyncDrawable(res: Resources, bitmap: Bitmap?, workerTask: AvatarWorkerTask) :
        BitmapDrawable(res, bitmap) {

        private val avatarWorkerTaskReference: WeakReference<AvatarWorkerTask> =
            WeakReference(workerTask)

        fun getAvatarWorkerTask(): AvatarWorkerTask? = avatarWorkerTaskReference.get()
    }

    companion object {
        @JvmStatic
        fun cancelPotentialWork(avatar: AvatarService.Avatar, imageView: ImageView): Boolean {
            val workerTask = getBitmapWorkerTask(imageView)
            if (workerTask != null) {
                val old = workerTask.avatar
                if (old == null || avatar != old) {
                    workerTask.cancel(true)
                } else {
                    return false
                }
            }
            return true
        }

        @JvmStatic
        fun getBitmapWorkerTask(imageView: ImageView?): AvatarWorkerTask? {
            if (imageView != null) {
                val drawable: Drawable? = imageView.drawable
                if (drawable is AsyncDrawable) {
                    return drawable.getAvatarWorkerTask()
                }
            }
            return null
        }

        @JvmStatic
        fun loadAvatar(
            avatar: AvatarService.Avatar,
            imageView: ImageView,
            @DimenRes size: Int
        ) {
            if (cancelPotentialWork(avatar, imageView)) {
                val activity = XmppActivity.find(imageView)
                    ?: return
                val bm: Bitmap? = activity.avatarService()
                    .get(avatar, activity.resources.getDimension(size).toInt(), true)
                setContentDescription(avatar, imageView)
                if (bm != null) {
                    cancelPotentialWork(avatar, imageView)
                    imageView.setImageBitmap(bm)
                    imageView.setBackgroundColor(0x00000000)
                } else {
                    imageView.setBackgroundColor(avatar.avatarBackgroundColor)
                    imageView.setImageDrawable(null)
                    val task = AvatarWorkerTask(imageView, size)
                    val asyncDrawable = AsyncDrawable(activity.resources, null, task)
                    imageView.setImageDrawable(asyncDrawable)
                    try {
                        task.execute(avatar)
                    } catch (ignored: RejectedExecutionException) {
                    }
                }
            }
        }

        private fun setContentDescription(avatar: AvatarService.Avatar, imageView: ImageView) {
            val context: Context = imageView.context
            if (avatar is Account) {
                imageView.contentDescription = context.getString(R.string.your_avatar)
            } else if (avatar is Message && avatar.type == Message.TYPE_STATUS) {
                imageView.contentDescription = null
                return
            } else {
                imageView.contentDescription =
                    context.getString(R.string.avatar_for_x, avatar.displayName)
            }
        }
    }
}
