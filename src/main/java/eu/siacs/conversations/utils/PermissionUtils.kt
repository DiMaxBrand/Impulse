package eu.siacs.conversations.utils

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.google.common.collect.ImmutableList
import com.google.common.primitives.Ints
import java.util.ArrayList

object PermissionUtils {

    @JvmStatic
    fun allGranted(grantResults: IntArray): Boolean {
        for (grantResult in grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    @JvmStatic
    fun writeGranted(grantResults: IntArray, permissions: Array<String>): Boolean {
        return permissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE, grantResults, permissions)
    }

    @JvmStatic
    fun audioGranted(grantResults: IntArray, permissions: Array<String>): Boolean {
        return permissionGranted(Manifest.permission.RECORD_AUDIO, grantResults, permissions)
    }

    @JvmStatic
    fun cameraGranted(grantResults: IntArray, permissions: Array<String>): Boolean {
        return permissionGranted(Manifest.permission.CAMERA, grantResults, permissions)
    }

    private fun permissionGranted(
        permission: String,
        grantResults: IntArray,
        permissions: Array<String>
    ): Boolean {
        for (i in grantResults.indices) {
            if (permission == permissions[i]) {
                return grantResults[i] == PackageManager.PERMISSION_GRANTED
            }
        }
        return false
    }

    @JvmStatic
    fun getFirstDenied(grantResults: IntArray, permissions: Array<String>): String? {
        for (i in grantResults.indices) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                return permissions[i]
            }
        }
        return null
    }

    class PermissionResult(
        @JvmField val permissions: Array<String>,
        @JvmField val grantResults: IntArray
    )

    @JvmStatic
    fun removeBluetoothConnect(
        inPermissions: Array<String>,
        inGrantResults: IntArray
    ): PermissionResult {
        val outPermissions = ArrayList<String>()
        val outGrantResults = ArrayList<Int>()
        for (i in 0 until minOf(inPermissions.size, inGrantResults.size)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (inPermissions[i] == Manifest.permission.BLUETOOTH_CONNECT) {
                    continue
                }
            }
            outPermissions.add(inPermissions[i])
            outGrantResults.add(inGrantResults[i])
        }
        return PermissionResult(
            outPermissions.toTypedArray(),
            Ints.toArray(outGrantResults)
        )
    }

    @JvmStatic
    fun hasPermission(
        activity: Activity,
        permissions: List<String>,
        requestCode: Int
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val missingPermissions = ImmutableList.builder<String>()
            for (permission in permissions) {
                if (ActivityCompat.checkSelfPermission(activity, permission)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    missingPermissions.add(permission)
                }
            }
            val missing = missingPermissions.build()
            if (missing.size == 0) {
                return true
            }
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), requestCode)
            return false
        } else {
            return true
        }
    }
}
