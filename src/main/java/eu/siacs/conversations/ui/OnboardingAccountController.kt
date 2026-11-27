package eu.siacs.conversations.ui

import eu.siacs.conversations.entities.Account
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.utils.Resolver
import eu.siacs.conversations.xmpp.Jid

/** Account operations for the new onboarding host. It never replaces an existing account. */
internal object OnboardingAccountController {
    internal sealed interface Result {
        data class Started(val account: Account) : Result
        data object InvalidAddress : Result
        data object EmptyPassword : Result
        data object AccountAlreadyExists : Result
        data object InvalidHostname : Result
        data object InvalidPort : Result
        data object UpdateFailed : Result
    }

    /**
     * [pending] is the account already created by this onboarding session, including one
     * recovered after process death. It can be retried, but an unrelated account is never edited.
     */
    fun submit(
        service: XmppConnectionService,
        address: String,
        password: String,
        register: Boolean,
        registrationDomain: String,
        preAuth: String?,
        pending: Account?,
        hostname: String,
        port: String,
        checkpointJid: (Jid) -> Unit,
    ): Result {
        val input = address.trim()
        val jid = try {
            if (!input.contains('@')) {
                Jid.of(input, if (register) registrationDomain else "on-chat.ru", null)
            } else {
                Jid.ofUserInput(input).also { Resolver.checkDomain(it) }
            }
        } catch (_: IllegalArgumentException) {
            return Result.InvalidAddress
        } catch (_: NullPointerException) {
            return Result.InvalidAddress
        }
        if (jid.local == null) return Result.InvalidAddress
        if (password.isEmpty()) return Result.EmptyPassword
        val host = hostname.filterNot(Char::isWhitespace)
        if (host.isNotEmpty() && Resolver.invalidHostname(host)) return Result.InvalidHostname
        val numericPort = if (host.isEmpty()) 5222 else port.filterNot(Char::isWhitespace).toIntOrNull()
            ?: return Result.InvalidPort
        if (numericPort !in 0..65535) return Result.InvalidPort
        if (pending?.isOptionSet(Account.OPTION_LOGGED_IN_SUCCESSFULLY) == true) return Result.AccountAlreadyExists
        val existing = service.findAccountByJid(jid)
        if (existing != null && existing !== pending) return Result.AccountAlreadyExists
        if (pending != null) {
            pending.setJid(jid)
            pending.setHostname(host.ifEmpty { null })
            pending.setPort(numericPort)
            pending.setPassword(password)
            pending.setOption(Account.OPTION_REGISTER, register)
            if (register && !preAuth.isNullOrBlank()) pending.setKey(Account.KEY_PRE_AUTH_REGISTRATION_TOKEN, preAuth)
            pending.setOption(Account.OPTION_DISABLED, false)
            pending.setOption(Account.OPTION_SOFT_DISABLED, false)
            return if (service.updateAccount(pending)) {
                checkpointJid(jid.asBareJid())
                Result.Started(pending)
            } else Result.UpdateFailed
        }
        val account = Account(jid.asBareJid(), password)
        account.setHostname(host.ifEmpty { null })
        account.setPort(numericPort)
        account.setOption(Account.OPTION_REGISTER, register)
        if (register && !preAuth.isNullOrBlank()) account.setKey(Account.KEY_PRE_AUTH_REGISTRATION_TOKEN, preAuth)
        checkpointJid(jid.asBareJid())
        service.createAccount(account)
        return Result.Started(account)
    }
}
