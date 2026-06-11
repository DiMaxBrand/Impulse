package eu.siacs.conversations.utils

import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteCursor
import android.os.Build

object CursorUtils {
    @JvmStatic
    fun upgradeCursorWindowSize(cursor: Cursor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (cursor is AbstractWindowedCursor) {
                cursor.setWindow(CursorWindow("4M", 4 * 1024 * 1024))
            }
            if (cursor is SQLiteCursor) {
                cursor.setFillWindowForwardOnly(true)
            }
        }
    }
}
