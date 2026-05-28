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

package eu.siacs.conversations.utils

import android.content.Context
import android.os.SystemClock
import androidx.annotation.PluralsRes
import eu.siacs.conversations.R
import java.util.Locale

object TimeFrameUtils {

    private val TIME_FRAMES: Array<TimeFrame> = arrayOf(
        TimeFrame(1000L, R.plurals.seconds),
        TimeFrame(60L * 1000, R.plurals.minutes),
        TimeFrame(60L * 60 * 1000, R.plurals.hours),
        TimeFrame(24L * 60 * 60 * 1000, R.plurals.days),
        TimeFrame(7L * 24 * 60 * 60 * 1000, R.plurals.weeks),
        TimeFrame(30L * 24 * 60 * 60 * 1000, R.plurals.months),
    )

    @JvmStatic
    fun resolve(context: Context, timeFrame: Long): String {
        for (i in TIME_FRAMES.indices.reversed()) {
            val duration = TIME_FRAMES[i].duration
            val threshold = if (i > 0) (TIME_FRAMES[i - 1].duration / 2) else 0L
            if (timeFrame >= duration - threshold) {
                val count = (timeFrame / duration + (if ((timeFrame % duration) > (duration / 2)) 1 else 0)).toInt()
                return context.resources.getQuantityString(TIME_FRAMES[i].name, count, count)
            }
        }
        return context.resources.getQuantityString(TIME_FRAMES[0].name, 0, 0)
    }

    @JvmStatic
    fun formatTimePassed(since: Long, withMilliseconds: Boolean): String {
        return formatTimePassed(since, SystemClock.elapsedRealtime(), withMilliseconds)
    }

    @JvmStatic
    fun formatTimePassed(since: Long, to: Long, withMilliseconds: Boolean): String {
        val passed = if (since < 0) 0L else (to - since)
        return formatElapsedTime(passed, withMilliseconds)
    }

    @JvmStatic
    fun formatElapsedTime(elapsed: Long, withMilliseconds: Boolean): String {
        val hours = (elapsed / 3600000).toInt()
        val minutes = (elapsed / 60000).toInt() % 60
        val seconds = (elapsed / 1000).toInt() % 60
        val milliseconds = (elapsed / 100).toInt() % 10
        return when {
            hours > 0 -> String.format(Locale.ENGLISH, "%d:%02d:%02d", hours, minutes, seconds)
            withMilliseconds -> String.format(Locale.ENGLISH, "%d:%02d.%d", minutes, seconds, milliseconds)
            else -> String.format(Locale.ENGLISH, "%d:%02d", minutes, seconds)
        }
    }

    private class TimeFrame(val duration: Long, @PluralsRes val name: Int)
}
