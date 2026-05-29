package eu.siacs.conversations.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.util.Log
import androidx.annotation.Nullable
import com.google.common.base.Strings
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableList
import com.google.common.collect.Lists
import eu.siacs.conversations.Config
import eu.siacs.conversations.R
import java.io.File
import java.util.ArrayList
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.function.Consumer

class ConversationsFileObserver(
    private val context: Context,
    private val onFileDeleted: Consumer<File>
) {
    private val fileObservers = ArrayList<FileObserver>()

    fun restartWatching() {
        synchronized(this.fileObservers) {
            for (observer in this.fileObservers) {
                observer.stopWatching()
            }
            this.fileObservers.clear()
            for (observer in getFileObservers()) {
                this.fileObservers.add(observer)
                observer.startWatching()
            }
        }
    }

    fun stopWatching() {
        synchronized(this.fileObservers) {
            for (observer in this.fileObservers) {
                observer.stopWatching()
            }
        }
    }

    private fun getInternalStorageLocation(): File =
        File(context.filesDir, "attachments")

    private fun getFileObservers(): Collection<FileObserver> {
        val locations = ImmutableList.Builder<File>()
            .add(getInternalStorageLocation())
            .addAll(getSharedStorageLocations())
            .build()
        val existing = Collections2.filter(locations) { location ->
            location!!.exists() && location.isDirectory
        }
        return Collections2.transform(existing) { location ->
            SingleDirectoryObserver(location!!, onFileDeleted)
        }
    }

    private fun getSharedStorageLocations(): List<File> =
        Lists.transform(STORAGE_TYPES) { type ->
            val parent = Environment.getExternalStoragePublicDirectory(type)
            File(parent, context.getString(R.string.app_name))
        }

    private class SingleDirectoryObserver(
        private val directory: File,
        private val onFileDeleted: Consumer<File>
    ) : FileObserver(directory.absolutePath, MASK) {

        override fun onEvent(event: Int, @Nullable path: String?) {
            if (Strings.isNullOrEmpty(path)) {
                return
            }
            onFileDeleted.accept(File(directory, path!!))
        }

        override fun startWatching() {
            super.startWatching()
            if (directory.exists() && directory.isDirectory) {
                Log.d(Config.LOGTAG, "started to watch ${directory.absolutePath}")
            }
        }
    }

    companion object {
        private val EVENT_EXECUTOR: Executor = Executors.newSingleThreadExecutor()
        private val MASK = FileObserver.DELETE or FileObserver.MOVED_FROM

        private val STORAGE_TYPES: List<String>

        init {
            val builder = ImmutableList.Builder<String>()
                .add(
                    Environment.DIRECTORY_DOWNLOADS,
                    Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_MOVIES,
                    Environment.DIRECTORY_DOCUMENTS,
                    Environment.DIRECTORY_DCIM
                )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.add(Environment.DIRECTORY_RECORDINGS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.add(Environment.DIRECTORY_AUDIOBOOKS)
            }
            STORAGE_TYPES = builder.build()
        }
    }
}
