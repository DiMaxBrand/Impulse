package eu.siacs.conversations

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.util.Log
import com.google.common.base.Stopwatch
import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import eu.siacs.conversations.persistance.DatabaseBackend
import eu.siacs.conversations.services.EmojiInitializationService
import eu.siacs.conversations.ui.util.SettingsUtils
import eu.siacs.conversations.utils.ExceptionHelper
import eu.siacs.conversations.worker.ApkCleanupWorker
import eu.siacs.conversations.worker.UpdateCheckWorker
import java.security.Security
import org.conscrypt.Conscrypt

class Conversations : Application() {

    private val accountWithOptionsSupplier: Supplier<Collection<DatabaseBackend.AccountWithOptions>> =
        Supplier {
            val stopwatch = Stopwatch.createStarted()
            val accounts =
                DatabaseBackend.getInstance(this@Conversations).getAccountWithOptions()
            Log.d(Config.LOGTAG, "fetching accounts from database in ${stopwatch.stop()}")
            accounts
        }

    private var accountWithOptions: Supplier<Collection<DatabaseBackend.AccountWithOptions>> =
        Suppliers.memoize(accountWithOptionsSupplier)

    override fun onCreate() {
        super.onCreate()
        installSecurityProvider()
        CONTEXT = this.applicationContext
        AppSettings.migratePreferences(applicationContext)
        EmojiInitializationService.execute(applicationContext)
        ExceptionHelper.init(applicationContext)
        SettingsUtils.applyThemeSettings(this)
        UpdateCheckWorker.schedule(this)
        ApkCleanupWorker.schedule(this)
    }

    fun resetAccounts() {
        this.accountWithOptions = Suppliers.memoize(accountWithOptionsSupplier)
    }

    fun getAccounts(): Collection<DatabaseBackend.AccountWithOptions> {
        return this.accountWithOptions.get()
    }

    fun hasEnabledAccount(): Boolean {
        return DatabaseBackend.AccountWithOptions.hasEnabledAccount(getAccounts())
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var CONTEXT: Context? = null

        @JvmStatic
        fun getContext(): Context? = CONTEXT

        @JvmStatic
        fun getInstance(context: Context): Conversations {
            val appContext = context.applicationContext
            if (appContext is Conversations) {
                return appContext
            }
            throw IllegalStateException("Application is not Conversations")
        }

        private fun installSecurityProvider() {
            try {
                Security.insertProviderAt(Conscrypt.newProvider(), 1)
            } catch (throwable: Throwable) {
                Log.e(Config.LOGTAG, "could not install security provider", throwable)
            }
        }
    }
}
