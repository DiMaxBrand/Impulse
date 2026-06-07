package eu.siacs.conversations.xml

import android.util.Log
import eu.siacs.conversations.Config
import im.conversations.android.xmpp.StreamElementWriter
import im.conversations.android.xmpp.model.StreamElement
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class TagWriter {

    private var outputStream: StreamElementWriter? = null
    private var finished = false

    private val writeQueue = LinkedBlockingQueue<StreamElement>()
    private var stanzaWriterCountDownLatch: CountDownLatch? = null

    private val asyncStanzaWriter = object : Thread() {
        override fun run() {
            stanzaWriterCountDownLatch = CountDownLatch(1)
            while (!isInterrupted) {
                if (finished && writeQueue.isEmpty()) {
                    break
                }
                try {
                    val output = writeQueue.take()
                    outputStream!!.write(output)
                    if (writeQueue.isEmpty()) {
                        outputStream!!.flush()
                    }
                } catch (e: Exception) {
                    break
                }
            }
            stanzaWriterCountDownLatch!!.countDown()
        }
    }

    @Throws(IOException::class)
    @Synchronized
    fun setOutputStream(outputStream: OutputStream?) {
        if (outputStream == null) {
            throw IOException()
        }
        this.outputStream = StreamElementWriter(outputStream)
    }

    @Throws(IOException::class)
    fun beginDocument() {
        if (outputStream == null) {
            throw IOException("output stream was null")
        }
        outputStream!!.write("<?xml version='1.0'?>")
    }

    @Throws(IOException::class)
    fun writeTag(tag: Tag) {
        writeTag(tag, true)
    }

    @Throws(IOException::class)
    @Synchronized
    fun writeTag(tag: Tag, flush: Boolean) {
        if (outputStream == null) {
            throw IOException("output stream was null")
        }
        outputStream!!.write(tag)
        if (flush) {
            outputStream!!.flush()
        }
    }

    @Throws(IOException::class)
    @Synchronized
    fun writeElement(element: StreamElement) {
        if (outputStream == null) {
            throw IOException("output stream was null")
        }
        outputStream!!.write(element)
        outputStream!!.flush()
    }

    fun writeStanzaAsync(stanza: StreamElement) {
        if (finished) {
            Log.d(Config.LOGTAG, "attempting to write stanza to finished TagWriter")
        } else {
            if (!asyncStanzaWriter.isAlive) {
                try {
                    asyncStanzaWriter.start()
                } catch (e: IllegalThreadStateException) {
                    // already started
                }
            }
            writeQueue.add(stanza)
        }
    }

    fun finish() {
        this.finished = true
    }

    @Throws(InterruptedException::class)
    fun await(timeout: Long, timeunit: TimeUnit): Boolean {
        return if (stanzaWriterCountDownLatch == null) {
            true
        } else {
            stanzaWriterCountDownLatch!!.await(timeout, timeunit)
        }
    }

    fun isActive(): Boolean = outputStream != null

    @Synchronized
    fun forceClose() {
        asyncStanzaWriter.interrupt()
        if (outputStream != null) {
            try {
                outputStream!!.close()
            } catch (e: IOException) {
                // ignoring
            }
        }
        outputStream = null
    }
}
