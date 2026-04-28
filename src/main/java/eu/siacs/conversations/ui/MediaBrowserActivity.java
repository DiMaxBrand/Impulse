package eu.siacs.conversations.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityMediaBrowserBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.adapter.MediaAdapter;
import eu.siacs.conversations.ui.interfaces.OnMediaLoaded;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.GridManager;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.xmpp.Jid;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class MediaBrowserActivity extends XmppActivity implements OnMediaLoaded {

    private static final String EXTRA_SELECTION = "selection";

    private final ActionMode.Callback actionModeCallBack =
            new ActionMode.Callback() {

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    actionMode = mode;
                    mode.setTitle(String.valueOf(mMediaAdapter.countSelections()));
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(final ActionMode mode, final Menu menu) {
                    mode.getMenuInflater().inflate(R.menu.media_browser_action_mode, menu);
                    return true;
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    if (item.getItemId() == R.id.action_share) {
                        shareSelectedAttachments();
                        return true;
                    }
                    if (item.getItemId() == R.id.action_delete) {
                        deleteSelectedAttachments();
                        return true;
                    }
                    if (item.getItemId() == R.id.action_save) {
                        saveSelectedAttachments();
                        return true;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    final var mediaAdapter = mMediaAdapter;
                    if (mediaAdapter != null) {
                        mediaAdapter.clearSelection();
                    }
                    actionMode = null;
                }
            };

    private ActivityMediaBrowserBinding binding;

    private MediaAdapter mMediaAdapter;
    private ActionMode actionMode;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_media_browser);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.mMediaAdapter = new MediaAdapter(this, R.dimen.media_size);
        if (savedInstanceState != null) {
            final String[] selection = savedInstanceState.getStringArray(EXTRA_SELECTION);
            if (selection != null) {
                final var uuids = Lists.transform(Arrays.asList(selection), UUID::fromString);
                this.mMediaAdapter.setSelection(uuids);
                if (!uuids.isEmpty()) {
                    startSupportActionMode(this.actionModeCallBack);
                }
            }
        }
        this.mMediaAdapter.setOnAttachmentClicked(
                attachment -> {
                    if (actionMode == null) {
                        ViewUtil.view(this, attachment);
                    } else {
                        toggleSelection(attachment);
                    }
                });
        this.mMediaAdapter.setOnAttachmentLongClicked(
                attachment -> {
                    toggleSelection(attachment);
                    return true;
                });
        this.binding.media.setAdapter(mMediaAdapter);
        GridManager.setupLayoutManager(this, this.binding.media, R.dimen.browser_media_size);
    }

    private void toggleSelection(final Attachment attachment) {
        final var actionMode = this.actionMode;
        if (actionMode == null) {
            if (this.mMediaAdapter.toggleSelection(attachment)) {
                this.actionMode = startSupportActionMode(this.actionModeCallBack);
                if (this.actionMode != null) {
                    this.actionMode.setTitle(String.valueOf(this.mMediaAdapter.countSelections()));
                }
            }
        } else {
            if (this.mMediaAdapter.toggleSelection(attachment)) {
                actionMode.setTitle(String.valueOf(this.mMediaAdapter.countSelections()));
            } else {
                actionMode.finish();
            }
        }
    }

    private void saveSelectedAttachments() {
        final var fileBackend = xmppConnectionService.getFileBackend();
        final var storageLocations =
                fileBackend.inferStorageLocation(this.mMediaAdapter.getSelectedAttachments());
        if (storageLocations.isEmpty()) {
            return;
        }
        final var actionMode = this.actionMode;
        if (actionMode != null) {
            actionMode.finish();
            this.mMediaAdapter.clearSelection();
        }
        final var future = fileBackend.saveInternalToExternal(storageLocations);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(List<Void> result) {
                        Toast.makeText(
                                        getApplicationContext(),
                                        getResources()
                                                .getQuantityText(
                                                        R.plurals.attachments_saved, result.size()),
                                        Toast.LENGTH_LONG)
                                .show();
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(Config.LOGTAG, "could not save attachments", t);
                        Toast.makeText(
                                        getApplicationContext(),
                                        getResources()
                                                .getQuantityText(
                                                        R.plurals.attachments_not_saved,
                                                        storageLocations.size()),
                                        Toast.LENGTH_LONG)
                                .show();
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void deleteSelectedAttachments() {
        final var attachments = this.mMediaAdapter.getSelectedAttachments();
        final var files =
                Collections2.transform(
                        Collections2.filter(
                                attachments,
                                a -> "file".equals(Objects.requireNonNull(a).getUri().getScheme())),
                        a ->
                                new File(
                                        Objects.requireNonNull(
                                                Objects.requireNonNull(a).getUri().getPath())));
        if (files.isEmpty()) {
            return;
        }
        final var builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(
                getResources()
                        .getQuantityString(
                                R.plurals.delete_files_dialog, files.size(), files.size()));
        builder.setMessage(
                getResources().getQuantityString(R.plurals.delete_files_dialog_msg, files.size()));
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.confirm, (dialog, which) -> deleteMediaFiles(files));
        builder.create().show();
    }

    private void deleteMediaFiles(final Collection<File> files) {
        final var actionMode = this.actionMode;
        if (actionMode != null) {
            actionMode.finish();
            this.mMediaAdapter.clearSelection();
        }
        final var future = xmppConnectionService.deleteMediaFiles(files);
        Futures.addCallback(
                future,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(Void result) {
                        final var service = xmppConnectionService;
                        if (service == null) {
                            return;
                        }
                        final var gallery = gallery(getIntent());
                        service.getAttachments(
                                gallery.account, gallery.contact, 0, MediaBrowserActivity.this);
                    }

                    @Override
                    public void onFailure(@NonNull Throwable t) {
                        Log.e(Config.LOGTAG, "could not delete files", t);
                    }
                },
                ContextCompat.getMainExecutor(this));
    }

    private void shareSelectedAttachments() {
        final var attachments = this.mMediaAdapter.getSelectedAttachments();
        if (attachments.isEmpty()) {
            return;
        }
        final var mimes =
                ImmutableSet.copyOf(
                        Lists.transform(attachments, a -> Objects.requireNonNull(a).getMime()));
        final var uris =
                ImmutableList.copyOf(
                        Lists.transform(
                                attachments,
                                a ->
                                        FileBackend.getUriForUri(
                                                        this, Objects.requireNonNull(a).getUri())
                                                .buildUpon()
                                                .appendQueryParameter(
                                                        "uuid", a.getUuid().toString())
                                                .build()));
        final var intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType(Iterables.getOnlyElement(mimes, ViewUtil.WILDCARD));
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, null));
    }

    public void onSaveInstanceState(@NonNull final Bundle bundle) {
        bundle.putStringArray(
                EXTRA_SELECTION,
                Lists.transform(
                                this.mMediaAdapter.getSelectedAttachments(),
                                a -> Objects.requireNonNull(a).getUuid().toString())
                        .toArray(new String[0]));
        super.onSaveInstanceState(bundle);
    }

    @Override
    protected void refreshUiReal() {}

    @Override
    protected void onBackendConnected() {
        final var gallery = gallery(getIntent());
        xmppConnectionService.getAttachments(gallery.account, gallery.contact, 0, this);
    }

    private static Gallery gallery(final Intent intent) {
        String account = intent == null ? null : intent.getStringExtra("account");
        String jid = intent == null ? null : intent.getStringExtra("jid");
        if (account != null && jid != null) {
            return new Gallery(account, Jid.of(jid));
        } else {
            throw new IllegalStateException("Activity started with incomplete extras");
        }
    }

    public static void launch(Context context, Contact contact) {
        launch(context, contact.getAccount(), contact.getAddress().asBareJid().toString());
    }

    public static void launch(Context context, Conversation conversation) {
        launch(
                context,
                conversation.getAccount(),
                conversation.getAddress().asBareJid().toString());
    }

    private static void launch(Context context, Account account, String jid) {
        final Intent intent = new Intent(context, MediaBrowserActivity.class);
        intent.putExtra("account", account.getUuid());
        intent.putExtra("jid", jid);
        context.startActivity(intent);
    }

    @Override
    public void onMediaLoaded(List<Attachment> attachments) {
        runOnUiThread(
                () -> {
                    mMediaAdapter.setAttachments(attachments);
                });
    }

    private record Gallery(String account, Jid contact) {}
}
