package eu.siacs.conversations.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import android.os.RemoteException
import android.util.Log
import com.google.common.base.Charsets
import com.google.common.base.Joiner
import com.google.common.base.Strings
import com.google.common.collect.Lists
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import eu.siacs.conversations.Config
import eu.siacs.conversations.persistance.UnifiedPushDatabase
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.utils.Compatibility
import java.util.Arrays

class UnifiedPushDistributor : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val pi: Parcelable? = intent.getParcelableExtra("pi")
        val application: String?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val sentFromPackage = sentFromPackage
            if (Strings.isNullOrEmpty(sentFromPackage)) {
                val fallback = asApplication(pi)
                if (Strings.isNullOrEmpty(fallback)) {
                    Log.d(Config.LOGTAG, "register/unregister command did not include application")
                    return
                }
                val targetSdk = getTargetSdk(context, fallback!!)
                if (targetSdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Log.d(
                        Config.LOGTAG,
                        "not accepting fallback because $fallback targets $targetSdk"
                    )
                    return
                }
                application = fallback
            } else {
                application = sentFromPackage
            }
        } else {
            application = asApplication(pi)
        }
        if (Strings.isNullOrEmpty(application)) {
            Log.d(Config.LOGTAG, "register/unregister command did not include application")
            return
        }
        val messenger: Parcelable? = intent.getParcelableExtra("messenger")
        val instance = intent.getStringExtra("token")
        val features = intent.getStringArrayListExtra("features")
        when (Strings.nullToEmpty(action)) {
            ACTION_REGISTER -> register(context, application!!, instance, features, messenger)
            ACTION_UNREGISTER -> unregister(context, instance)
            Intent.ACTION_PACKAGE_FULLY_REMOVED -> unregisterApplication(context, intent.data)
            else -> Log.d(Config.LOGTAG, "UnifiedPushDistributor received unknown action $action")
        }
    }

    private fun register(
        context: Context,
        application: String,
        instance: String?,
        features: Collection<String>?,
        messenger: Parcelable?
    ) {
        if (Strings.isNullOrEmpty(application) || Strings.isNullOrEmpty(instance)) {
            Log.w(Config.LOGTAG, "ignoring invalid UnifiedPush registration")
            return
        }
        val receivers = getBroadcastReceivers(context, application)
        if (receivers.contains(application)) {
            val byteMessage = features != null && features.contains(ACTION_BYTE_MESSAGE)
            Log.d(
                Config.LOGTAG,
                "received up registration from $application/$instance features: $features"
            )
            if (UnifiedPushDatabase.getInstance(context).register(application, instance)) {
                Log.d(
                    Config.LOGTAG,
                    "successfully created UnifiedPush entry. waking up XmppConnectionService"
                )
                quickLog(
                    context,
                    String.format(
                        "successfully registered %s (token = %s) for UnifiedPushed",
                        application,
                        instance
                    )
                )
                val serviceIntent = Intent(context, XmppConnectionService::class.java)
                serviceIntent.setAction(XmppConnectionService.ACTION_RENEW_UNIFIED_PUSH_ENDPOINTS)
                serviceIntent.putExtra("instance", instance)
                serviceIntent.putExtra("application", application)
                if (messenger is Messenger) {
                    serviceIntent.putExtra("messenger", messenger)
                }
                Compatibility.startService(context, serviceIntent)
            } else {
                Log.d(Config.LOGTAG, "not successful. sending error message back to application")
                val registrationFailed = Intent(ACTION_REGISTRATION_FAILED)
                registrationFailed.putExtra(EXTRA_MESSAGE, "instance already exits")
                registrationFailed.setPackage(application)
                registrationFailed.putExtra("token", instance)
                if (messenger is Messenger) {
                    val message = Message()
                    message.obj = registrationFailed
                    try {
                        messenger.send(message)
                    } catch (e: RemoteException) {
                        context.sendBroadcast(registrationFailed)
                    }
                } else {
                    context.sendBroadcast(registrationFailed)
                }
            }
        } else {
            if (messenger is Messenger) {
                sendRegistrationFailed(messenger, "Your application is not registered to receive messages")
            }
            Log.d(
                Config.LOGTAG,
                "ignoring invalid UnifiedPush registration. Unknown application $application"
            )
        }
    }

    private fun sendRegistrationFailed(messenger: Messenger, error: String) {
        val intent = Intent(ACTION_REGISTRATION_FAILED)
        intent.putExtra(EXTRA_MESSAGE, error)
        val message = Message()
        message.obj = intent
        try {
            messenger.send(message)
        } catch (e: RemoteException) {
            Log.d(Config.LOGTAG, "unable to tell messenger of failed registration", e)
        }
    }

    private fun getBroadcastReceivers(context: Context, application: String): List<String?> {
        val messageIntent = Intent(ACTION_MESSAGE)
        messageIntent.setPackage(application)
        val resolveInfo: List<ResolveInfo> =
            context.packageManager.queryBroadcastReceivers(messageIntent, 0)
        return Lists.transform(resolveInfo) { ri -> ri?.activityInfo?.packageName }
    }

    private fun unregister(context: Context, instance: String?) {
        if (Strings.isNullOrEmpty(instance)) {
            Log.w(Config.LOGTAG, "ignoring invalid UnifiedPush un-registration")
            return
        }
        val unifiedPushDatabase = UnifiedPushDatabase.getInstance(context)
        if (unifiedPushDatabase.deleteInstance(instance)) {
            quickLog(
                context,
                String.format(
                    "successfully unregistered token %s from UnifiedPushed (application requested unregister)",
                    instance
                )
            )
            Log.d(Config.LOGTAG, "successfully removed $instance from UnifiedPush")
            // TODO send UNREGISTERED broadcast back to app?!
        }
    }

    private fun unregisterApplication(context: Context, uri: Uri?) {
        if (uri != null && "package".equals(uri.scheme, ignoreCase = true)) {
            val application = uri.schemeSpecificPart
            if (Strings.isNullOrEmpty(application)) return
            Log.d(Config.LOGTAG, "app $application has been removed from the system")
            val database = UnifiedPushDatabase.getInstance(context)
            if (database.deleteApplication(application)) {
                quickLog(
                    context,
                    String.format(
                        "successfully removed %s from UnifiedPushed (ACTION_PACKAGE_FULLY_REMOVED)",
                        application
                    )
                )
                Log.d(Config.LOGTAG, "successfully removed $application from UnifiedPush")
            }
        }
    }

    companion object {
        // distributor actions (these are actions used for connector->distributor broadcasts)
        // we, the distributor, have a broadcast receiver listening for those actions
        const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
        const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"

        // connector actions (these are actions used for distributor->connector broadcasts)
        const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
        const val ACTION_BYTE_MESSAGE =
            "org.unifiedpush.android.distributor.feature.BYTES_MESSAGE"
        const val ACTION_REGISTRATION_FAILED =
            "org.unifiedpush.android.connector.REGISTRATION_FAILED"

        // this action is only used in 'messenger' communication to tell the app that a registration is
        // probably fine but can not be processed right now; for example due to spotty internet
        const val ACTION_REGISTRATION_DELAYED =
            "org.unifiedpush.android.connector.REGISTRATION_DELAYED"
        const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
        const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"

        const val EXTRA_MESSAGE = "message"

        const val PREFERENCE_ACCOUNT = "up_push_account"
        const val PREFERENCE_PUSH_SERVER = "up_push_server"

        @JvmField
        val PREFERENCES: List<String> = Arrays.asList(PREFERENCE_ACCOUNT, PREFERENCE_PUSH_SERVER)

        private fun asApplication(parcelable: Parcelable?): String? {
            return if (parcelable is PendingIntent) {
                Strings.emptyToNull(parcelable.intentSender.creatorPackage)
            } else {
                null
            }
        }

        private fun getTargetSdk(context: Context, application: String): Int {
            return try {
                context.packageManager.getApplicationInfo(application, 0).targetSdkVersion
            } catch (e: PackageManager.NameNotFoundException) {
                // this will def. be over our max sdk of 34
                Int.MIN_VALUE
            }
        }

        @JvmStatic
        fun hash(vararg components: String): String {
            return BaseEncoding.base64().encode(
                Hashing.sha256()
                    .hashString(Joiner.on(' ').join(components.toList()), Charsets.UTF_8)
                    .asBytes()
            )
        }

        @JvmStatic
        fun quickLog(context: Context, message: String) {
            val intent = Intent(context, XmppConnectionService::class.java)
            intent.setAction(XmppConnectionService.ACTION_QUICK_LOG)
            intent.putExtra("message", message)
            Compatibility.startService(context, intent)
        }
    }
}
