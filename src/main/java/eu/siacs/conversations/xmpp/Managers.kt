package eu.siacs.conversations.xmpp

import com.google.common.collect.ClassToInstanceMap
import com.google.common.collect.ImmutableClassToInstanceMap
import eu.siacs.conversations.services.XmppConnectionService
import eu.siacs.conversations.xmpp.manager.AbstractManager
import eu.siacs.conversations.xmpp.manager.ActivityManager
import eu.siacs.conversations.xmpp.manager.AdHocCommandsManager
import eu.siacs.conversations.xmpp.manager.AvatarManager
import eu.siacs.conversations.xmpp.manager.AxolotlManager
import eu.siacs.conversations.xmpp.manager.BlockingManager
import eu.siacs.conversations.xmpp.manager.BookmarkManager
import eu.siacs.conversations.xmpp.manager.CarbonsManager
import eu.siacs.conversations.xmpp.manager.ChatStateManager
import eu.siacs.conversations.xmpp.manager.ClientStateIndicationManager
import eu.siacs.conversations.xmpp.manager.DeliveryReceiptManager
import eu.siacs.conversations.xmpp.manager.DiscoManager
import eu.siacs.conversations.xmpp.manager.DisplayedManager
import eu.siacs.conversations.xmpp.manager.EasyOnboardingManager
import eu.siacs.conversations.xmpp.manager.EntityTimeManager
import eu.siacs.conversations.xmpp.manager.ExternalServiceDiscoveryManager
import eu.siacs.conversations.xmpp.manager.HttpUploadManager
import eu.siacs.conversations.xmpp.manager.JingleManager
import eu.siacs.conversations.xmpp.manager.JingleMessageManager
import eu.siacs.conversations.xmpp.manager.LegacyBookmarkManager
import eu.siacs.conversations.xmpp.manager.MessageArchiveManager
import eu.siacs.conversations.xmpp.manager.MessageDisplayedSynchronizationManager
import eu.siacs.conversations.xmpp.manager.ModerationManager
import eu.siacs.conversations.xmpp.manager.MultiUserChatManager
import eu.siacs.conversations.xmpp.manager.NativeBookmarkManager
import eu.siacs.conversations.xmpp.manager.NickManager
import eu.siacs.conversations.xmpp.manager.OfflineMessagesManager
import eu.siacs.conversations.xmpp.manager.PepManager
import eu.siacs.conversations.xmpp.manager.PingManager
import eu.siacs.conversations.xmpp.manager.PresenceManager
import eu.siacs.conversations.xmpp.manager.PrivateStorageManager
import eu.siacs.conversations.xmpp.manager.PubSubManager
import eu.siacs.conversations.xmpp.manager.PushNotificationManager
import eu.siacs.conversations.xmpp.manager.ReactionManager
import eu.siacs.conversations.xmpp.manager.RegistrationManager
import eu.siacs.conversations.xmpp.manager.RosterManager
import eu.siacs.conversations.xmpp.manager.StanzaIdManager
import eu.siacs.conversations.xmpp.manager.StreamHostManager
import eu.siacs.conversations.xmpp.manager.UnifiedPushManager
import eu.siacs.conversations.xmpp.manager.VCardManager

object Managers {
    @JvmStatic
    fun get(
        context: XmppConnectionService,
        connection: XmppConnection,
    ): ClassToInstanceMap<AbstractManager> =
        ImmutableClassToInstanceMap.Builder<AbstractManager>()
            .put(ActivityManager::class.java, ActivityManager(context, connection))
            .put(AdHocCommandsManager::class.java, AdHocCommandsManager(context, connection))
            .put(AvatarManager::class.java, AvatarManager(context, connection))
            .put(AxolotlManager::class.java, AxolotlManager(context, connection))
            .put(BlockingManager::class.java, BlockingManager(context, connection))
            .put(BookmarkManager::class.java, BookmarkManager(context, connection))
            .put(CarbonsManager::class.java, CarbonsManager(context, connection))
            .put(ChatStateManager::class.java, ChatStateManager(context, connection))
            .put(ClientStateIndicationManager::class.java, ClientStateIndicationManager(context, connection))
            .put(DeliveryReceiptManager::class.java, DeliveryReceiptManager(context, connection))
            .put(DiscoManager::class.java, DiscoManager(context, connection))
            .put(DisplayedManager::class.java, DisplayedManager(context, connection))
            .put(EasyOnboardingManager::class.java, EasyOnboardingManager(context, connection))
            .put(EntityTimeManager::class.java, EntityTimeManager(context, connection))
            .put(ExternalServiceDiscoveryManager::class.java, ExternalServiceDiscoveryManager(context, connection))
            .put(HttpUploadManager::class.java, HttpUploadManager(context, connection))
            .put(JingleManager::class.java, JingleManager(context, connection))
            .put(JingleMessageManager::class.java, JingleMessageManager(context, connection))
            .put(LegacyBookmarkManager::class.java, LegacyBookmarkManager(context, connection))
            .put(MessageArchiveManager::class.java, MessageArchiveManager(context, connection))
            .put(MessageDisplayedSynchronizationManager::class.java, MessageDisplayedSynchronizationManager(context, connection))
            .put(ModerationManager::class.java, ModerationManager(context, connection))
            .put(MultiUserChatManager::class.java, MultiUserChatManager(context, connection))
            .put(NativeBookmarkManager::class.java, NativeBookmarkManager(context, connection))
            .put(NickManager::class.java, NickManager(context, connection))
            .put(OfflineMessagesManager::class.java, OfflineMessagesManager(context, connection))
            .put(PepManager::class.java, PepManager(context, connection))
            .put(PingManager::class.java, PingManager(context, connection))
            .put(PresenceManager::class.java, PresenceManager(context, connection))
            .put(PrivateStorageManager::class.java, PrivateStorageManager(context, connection))
            .put(PubSubManager::class.java, PubSubManager(context, connection))
            .put(PushNotificationManager::class.java, PushNotificationManager(context, connection))
            .put(ReactionManager::class.java, ReactionManager(context, connection))
            .put(RegistrationManager::class.java, RegistrationManager(context, connection))
            .put(RosterManager::class.java, RosterManager(context, connection))
            .put(StanzaIdManager::class.java, StanzaIdManager(context, connection))
            .put(StreamHostManager::class.java, StreamHostManager(context, connection))
            .put(UnifiedPushManager::class.java, UnifiedPushManager(context, connection))
            .put(VCardManager::class.java, VCardManager(context, connection))
            .build()
}
