package eu.siacs.conversations.ui;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;
import androidx.compose.ui.platform.ComposeView;
import eu.siacs.conversations.utils.PhoneNumberUtilWrapper;
import eu.siacs.conversations.xmpp.manager.RosterManager;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.Settings;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import de.gultsch.common.MiniUri;
import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.AbstractQuickConversationsService;
import eu.siacs.conversations.services.QuickConversationsService;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.JidDialog;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.AccountUtils;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.ContactGenderOverride;
import eu.siacs.conversations.utils.NameGenderGuesser;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnKeyStatusUpdated;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;
import eu.siacs.conversations.xmpp.XmppConnection;
import eu.siacs.conversations.xmpp.manager.BlockingManager;
import eu.siacs.conversations.xmpp.manager.PresenceManager;
import java.util.Collections;
import java.util.List;

public class ContactDetailsActivity extends OmemoActivity
        implements OnAccountUpdate,
                OnRosterUpdate,
                OnUpdateBlocklist,
                OnKeyStatusUpdated,
                OnMediaLoaded {
    public static final String ACTION_VIEW_CONTACT = "view_contact";
    private final int REQUEST_SYNC_CONTACTS = 0x28cf;
    private final ContactDetailsState uiState = new ContactDetailsState();

    private Contact contact;
    private final DialogInterface.OnClickListener removeFromRoster =
            new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    xmppConnectionService.deleteContactOnServer(contact);
                }
            };

    private void setSendPresence(final boolean isChecked) {
        if (isChecked) {
            if (contact.getOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST)) {
                xmppConnectionService.stopPresenceUpdatesTo(contact);
            } else {
                contact.setOption(Contact.Options.PREEMPTIVE_GRANT);
            }
        } else {
            contact.resetOption(Contact.Options.PREEMPTIVE_GRANT);
            final var connection = contact.getAccount().getXmppConnection();
            connection
                    .getManager(PresenceManager.class)
                    .unsubscribed(contact.getAddress().asBareJid());
        }
        populateView();
    }

    private void setReceivePresence(final boolean isChecked) {
        final var connection = contact.getAccount().getXmppConnection();
        if (isChecked) {
            connection.getManager(PresenceManager.class).subscribe(contact.getAddress().asBareJid());
        } else {
            connection
                    .getManager(PresenceManager.class)
                    .unsubscribe(contact.getAddress().asBareJid());
        }
        populateView();
    }

    private Jid accountJid;
    private Jid contactJid;
    private boolean showDynamicTags = false;
    private boolean showLastSeen = false;
    private boolean showInactiveOmemo = false;
    private String messageFingerprint;

    private void checkContactPermissionAndShowAddDialog() {
        if (hasContactsPermission()) {
            showAddToPhoneBookDialog();
        } else if (QuickConversationsService.isContactListIntegration(this)) {
            requestPermissions(
                    new String[] {Manifest.permission.READ_CONTACTS}, REQUEST_SYNC_CONTACTS);
        }
    }

    private boolean hasContactsPermission() {
        if (QuickConversationsService.isContactListIntegration(this)) {
            return checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    private void showAddToPhoneBookDialog() {
        final Jid jid = contact.getAddress();
        final boolean quicksyContact =
                AbstractQuickConversationsService.isQuicksy()
                        && Config.QUICKSY_DOMAIN.equals(jid.getDomain())
                        && jid.getLocal() != null;
        final String value;
        if (quicksyContact) {
            value = PhoneNumberUtilWrapper.toFormattedPhoneNumber(this, jid);
        } else {
            value = jid.toString();
        }
        // A note first, then the phone's contacts screen (pick an existing contact or make a new one).
        AddToAddressBookDialogFragment.show(
                this,
                contact.getDisplayName(),
                quicksyContact ? null : value,
                quicksyContact ? value : null);
    }

    @Override
    public void onRosterUpdate() {
        refreshUi();
    }

    @Override
    public void onAccountUpdate() {
        refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(final Status status) {
        refreshUi();
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        populateView();
    }

    @Override
    protected MiniUri getShareableUri(final boolean http) {
        final var uri = new MiniUri.Xmpp(contact.getAddress());
        return http ? uri.asInvitationUri() : uri;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showInactiveOmemo =
                savedInstanceState != null
                        && savedInstanceState.getBoolean("show_inactive_omemo", false);
        if (getIntent().getAction().equals(ACTION_VIEW_CONTACT)) {
            try {
                this.accountJid = Jid.of(getIntent().getExtras().getString(EXTRA_ACCOUNT));
            } catch (final IllegalArgumentException ignored) {
            }
            try {
                this.contactJid = Jid.of(getIntent().getExtras().getString("contact"));
            } catch (final IllegalArgumentException ignored) {
            }
        }
        this.messageFingerprint = getIntent().getStringExtra("fingerprint");
        // The picture reaches up under the status bar: the screen draws edge to edge.
        androidx.activity.EdgeToEdge.enable(this);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        final ComposeView composeView = new ComposeView(this);
        setContentView(composeView);
        ContactDetailsHost.setup(composeView, uiState, actions);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        savedInstanceState.putBoolean("show_inactive_omemo", showInactiveOmemo);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        this.showDynamicTags = preferences.getBoolean(AppSettings.SHOW_DYNAMIC_TAGS, false);
        this.showLastSeen = preferences.getBoolean("last_activity", false);
        uiState.setMediaAllowed(Compatibility.hasStoragePermission(this));
        uiState.setMedia(Collections.emptyList());
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // TODO check for Camera / Scan permission
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) {
            return;
        }
        if (requestCode == REQUEST_SYNC_CONTACTS && xmppConnectionServiceBound) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showAddToPhoneBookDialog();
                xmppConnectionService.loadPhoneContacts();
                xmppConnectionService.startContactObserver();
            } else {
                showRedirectToAppSettings();
            }
        }
    }

    private void showRedirectToAppSettings() {
        final var dialogBuilder = new MaterialAlertDialogBuilder(this);
        dialogBuilder.setTitle(R.string.save_to_contact);
        dialogBuilder.setMessage(
                getString(R.string.no_contacts_permission, getString(R.string.app_name)));
        dialogBuilder.setPositiveButton(
                R.string.continue_btn,
                (d, w) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                });
        dialogBuilder.setNegativeButton(R.string.cancel, null);
        dialogBuilder.create().show();
    }

    private void deleteContactDialog() {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setTitle(getString(R.string.action_delete_contact))
                .setMessage(
                        JidDialog.style(
                                this,
                                R.string.remove_contact_text,
                                contact.getAddress().toString()))
                .setPositiveButton(getString(R.string.delete), removeFromRoster)
                .create()
                .show();
    }

    private void editContact() {
        final Uri systemAccount = contact.getSystemAccount();
        if (systemAccount == null) {
            quickEdit(
                    contact.getServerName(),
                    R.string.contact_name,
                    value -> {
                        contact.setServerName(value);
                        final var connection = contact.getAccount().getXmppConnection();
                        connection.getManager(RosterManager.class).addRosterItem(contact, null);
                        populateView();
                        return null;
                    },
                    true);
        } else {
            Intent intent = new Intent(Intent.ACTION_EDIT);
            intent.setDataAndType(systemAccount, Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra("finishActivityOnSaveCompleted", true);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(
                                ContactDetailsActivity.this,
                                R.string.no_application_found_to_view_contact,
                                Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    private void configureCustomNotifications(final Contact contact) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        final var shortcut = xmppConnectionService.getShortcutService().getShortcutInfo(contact);
        configureCustomNotification(shortcut);
    }

    private void populateView() {
        if (contact == null) {
            return;
        }
        uiState.setUi(
                ContactDetailsPresenter.build(
                        this,
                        contact,
                        showInactiveOmemo,
                        messageFingerprint,
                        showDynamicTags,
                        isCameraFeatureAvailable(),
                        QuickConversationsService.isContactListIntegration(this)));
    }

    private String genderLabel(final Contact contact) {
        final NameGenderGuesser.Gender gender = ContactGenderOverride.INSTANCE.get(this, contact);
        return switch (gender) {
            case MASCULINE -> getString(R.string.contact_grammatical_gender_masculine);
            case FEMININE -> getString(R.string.contact_grammatical_gender_feminine);
            // "Auto-detect" alone doesn't tell the user which grammatical form actually gets
            // used, same info UIHelper.resolveGender() would compute for this exact contact at
            // message-render time. Only shown in parentheses when the name-based guess actually
            // resolved to something -- an unrecognized name (NameGenderGuesser's own UNKNOWN)
            // stays plain "Auto-detect", not a guessed "(Masculine)" that would misrepresent a
            // grammar-only fallback as a detected result. See NameGenderGuesser's own class doc:
            // never surfaced as a claim about anyone's actual gender.
            case UNKNOWN -> {
                final NameGenderGuesser.Gender guessed =
                        NameGenderGuesser.INSTANCE.guess(contact.getDisplayName());
                yield switch (guessed) {
                    case MASCULINE ->
                            getString(
                                    R.string.contact_grammatical_gender_auto_detected,
                                    getString(R.string.contact_grammatical_gender_masculine));
                    case FEMININE ->
                            getString(
                                    R.string.contact_grammatical_gender_auto_detected,
                                    getString(R.string.contact_grammatical_gender_feminine));
                    case UNKNOWN -> getString(R.string.contact_grammatical_gender_auto);
                };
            }
        };
    }

    private void showGenderDialog(final Contact contact) {
        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.contact_grammatical_gender_dialog_title);
        final String[] choices = {
            getString(R.string.contact_grammatical_gender_auto),
            getString(R.string.contact_grammatical_gender_masculine),
            getString(R.string.contact_grammatical_gender_feminine),
        };
        final NameGenderGuesser.Gender current = ContactGenderOverride.INSTANCE.get(this, contact);
        final int initial =
                switch (current) {
                    case MASCULINE -> 1;
                    case FEMININE -> 2;
                    case UNKNOWN -> 0;
                };
        final java.util.concurrent.atomic.AtomicInteger choice =
                new java.util.concurrent.atomic.AtomicInteger(initial);
        builder.setSingleChoiceItems(choices, initial, (dialog, which) -> choice.set(which));
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(
                R.string.ok,
                (dialog, which) -> {
                    final NameGenderGuesser.Gender selected =
                            switch (choice.get()) {
                                case 1 -> NameGenderGuesser.Gender.MASCULINE;
                                case 2 -> NameGenderGuesser.Gender.FEMININE;
                                default -> NameGenderGuesser.Gender.UNKNOWN;
                            };
                    ContactGenderOverride.INSTANCE.set(this, contact, selected);
                    populateView();
                });
        builder.create().show();
    }

    private void openAccount() {
        final var contact = this.contact;
        if (contact == null) {
            return;
        }
        // The accounts screen at this account (not the old account editor).
        final Intent hub = new Intent(this, AccountsHubActivity.class);
        hub.putExtra(
                AccountsHubActivity.EXTRA_FOCUS_ACCOUNT,
                contact.getAccount().getJid().asBareJid().toString());
        startActivity(hub);
    }

    private void openAvatar() {
        final var contact = this.contact;
        if (contact == null) {
            return;
        }
        final var avatar = contact.getAvatar();
        if (avatar == null) {
            return;
        }
        final var intent = new Intent(this, ViewProfilePictureActivity.class);
        intent.setData(Uri.fromParts("avatar", avatar, null));
        intent.putExtra(ViewProfilePictureActivity.EXTRA_DISPLAY_NAME, contact.getDisplayName());
        startActivity(intent);
    }

    private void onAddToAddressBookClick() {
        if (QuickConversationsService.isContactListIntegration(this)) {
            final Uri systemAccount = contact.getSystemAccount();
            if (systemAccount == null) {
                checkContactPermissionAndShowAddDialog();
            } else {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(systemAccount);
                try {
                    startActivity(intent);
                } catch (final ActivityNotFoundException e) {
                    Toast.makeText(
                                    this,
                                    R.string.no_application_found_to_view_contact,
                                    Toast.LENGTH_SHORT)
                            .show();
                }
            }
        } else {
            Toast.makeText(
                            this,
                            R.string.contact_list_integration_not_available,
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public void onBackendConnected() {
        if (accountJid != null && contactJid != null) {
            Account account = xmppConnectionService.findAccountByJid(accountJid);
            if (account == null) {
                return;
            }
            this.contact = account.getRoster().getContact(contactJid);
            if (mPendingFingerprintVerificationUri != null) {
                processFingerprintVerification(mPendingFingerprintVerificationUri);
                mPendingFingerprintVerificationUri = null;
            }

            if (Compatibility.hasStoragePermission(this)) {
                xmppConnectionService.getAttachments(
                        account, contact.getAddress().asBareJid(), 12, this);
            }
            populateView();
        }
    }

    @Override
    public void onKeyStatusUpdated(AxolotlService.FetchStatus report) {
        refreshUi();
    }

    @Override
    protected void processFingerprintVerification(final MiniUri.Xmpp uri) {
        if (contact != null
                && contact.getAddress().asBareJid().equals(uri.asJid())
                && uri.hasOmemoFingerprints()) {
            if (xmppConnectionService.verifyFingerprints(contact, uri.getOmemoFingerprints())) {
                Toast.makeText(this, R.string.verified_fingerprints, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, R.string.invalid_barcode, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(
                () ->
                        uiState.setMedia(
                                new java.util.ArrayList<>(
                                        attachments.subList(0, Math.min(12, attachments.size())))));
    }

    /** What the screen asks the activity to do. */
    private final ContactDetailsActions actions =
            new ContactDetailsActions() {
                @Override
                public void onBack() {
                    finish();
                }

                @Override
                public void onMessage() {
                    if (contact == null) {
                        return;
                    }
                    final var conversation =
                            xmppConnectionService.findOrCreateConversation(
                                    contact.getAccount(), contact.getAddress(), false, true);
                    switchToConversation(conversation);
                }

                @Override
                public void onAvatar() {
                    openAvatar();
                }

                @Override
                public void onAccount() {
                    openAccount();
                }

                @Override
                public void onCopyAddress() {
                    if (contact != null) {
                        copyTextToClipboard(
                                contact.getAddress().toString(),
                                R.string.account_settings_jabber_id);
                    }
                }

                @Override
                public void onSendPresence(final boolean checked) {
                    setSendPresence(checked);
                }

                @Override
                public void onReceivePresence(final boolean checked) {
                    setReceivePresence(checked);
                }

                @Override
                public void onGender() {
                    if (contact != null) {
                        showGenderDialog(contact);
                    }
                }

                @Override
                public void onAddressBook() {
                    onAddToAddressBookClick();
                }

                @Override
                public void onAddToRoster() {
                    showAddToRosterDialog(contact);
                }

                @Override
                public void onShareLink(final boolean http) {
                    shareLink(http);
                }

                @Override
                public void onEdit() {
                    editContact();
                }

                @Override
                public void onDelete() {
                    deleteContactDialog();
                }

                @Override
                public void onBlock() {
                    BlockContactDialog.show(ContactDetailsActivity.this, contact);
                }

                @Override
                public void onCustomNotifications() {
                    configureCustomNotifications(contact);
                }

                @Override
                public void onToggleInactive() {
                    showInactiveOmemo = !showInactiveOmemo;
                    populateView();
                }

                @Override
                public void onScan() {
                    requestPermissionAndScanQrCode();
                }

                @Override
                public void onTrust(final FingerprintRowUi key, final boolean trusted) {
                    contact.getAccount()
                            .getAxolotlService()
                            .setFingerprintTrust(
                                    key.getFingerprint(), FingerprintStatus.createActive(trusted));
                    populateView();
                }

                @Override
                public void onEnableDevice(final FingerprintRowUi key) {
                    contact.getAccount()
                            .getAxolotlService()
                            .setFingerprintTrust(
                                    key.getFingerprint(), FingerprintStatus.createActive(false));
                    populateView();
                }

                @Override
                public void onCopyKey(final FingerprintRowUi key) {
                    copyOmemoFingerprint(key.getFingerprint());
                }

                @Override
                public void onDistrust(final FingerprintRowUi key) {
                    showPurgeKeyDialog(contact.getAccount(), key.getFingerprint());
                }

                @Override
                public void onPgpKey() {
                    launchOpenKeyChain(contact.getPgpKeyId());
                }

                @Override
                public void onViewMedia() {
                    MediaBrowserActivity.launch(ContactDetailsActivity.this, contact);
                }

                @Override
                public void onOpenAttachment(final Attachment attachment) {
                    eu.siacs.conversations.ui.util.ViewUtil.view(
                            ContactDetailsActivity.this, attachment);
                }

                @Override
                public Bitmap avatar(final int sizePx) {
                    if (contact == null || xmppConnectionService == null) {
                        return null;
                    }
                    return xmppConnectionService.getAvatarService().get(contact, sizePx);
                }

                @Override
                public Bitmap preview(final Attachment attachment, final int sizePx) {
                    if (xmppConnectionService == null || !attachment.renderThumbnail()) {
                        return null;
                    }
                    return xmppConnectionService
                            .getFileBackend()
                            .getPreviewForUri(attachment, sizePx, false);
                }
            };
}
