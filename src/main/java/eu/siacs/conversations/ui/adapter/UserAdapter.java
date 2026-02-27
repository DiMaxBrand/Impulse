package eu.siacs.conversations.ui.adapter;

import android.app.PendingIntent;
import android.content.IntentSender;
import android.content.res.ColorStateList;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.constraintlayout.helper.widget.Flow;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.color.MaterialColors;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.databinding.ItemContactBinding;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MucDetailsContextMenuHelper;
import eu.siacs.conversations.utils.Compatibility;
import eu.siacs.conversations.utils.XEP0392Helper;
import eu.siacs.conversations.xmpp.Jid;
import java.util.List;
import org.openintents.openpgp.util.OpenPgpUtils;

public class UserAdapter extends ListAdapter<MucOptions.User, UserAdapter.ViewHolder>
        implements View.OnCreateContextMenuListener {

    static final DiffUtil.ItemCallback<MucOptions.User> DIFF =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(
                        @NonNull MucOptions.User a, @NonNull MucOptions.User b) {
                    final Jid fullA = a.getFullJid();
                    final Jid fullB = b.getFullJid();
                    final Jid realA = a.getRealJid();
                    final Jid realB = b.getRealJid();
                    if (fullA != null && fullB != null) {
                        return fullA.equals(fullB);
                    } else if (realA != null && realB != null) {
                        return realA.equals(realB);
                    } else {
                        return false;
                    }
                }

                @Override
                public boolean areContentsTheSame(
                        @NonNull MucOptions.User a, @NonNull MucOptions.User b) {
                    return a.equals(b);
                }
            };
    private MucOptions.User selectedUser = null;

    public UserAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int position) {
        return new ViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(viewGroup.getContext()),
                        R.layout.item_contact,
                        viewGroup,
                        false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        final MucOptions.User user = getItem(position);
        AvatarWorkerTask.loadAvatar(user, viewHolder.binding.contactPhoto, R.dimen.avatar);
        viewHolder
                .binding
                .getRoot()
                .setOnClickListener(
                        v -> {
                            final XmppActivity activity = XmppActivity.find(v);
                            if (activity == null) {
                                return;
                            }
                            if (user.resource() == null) {
                                Toast.makeText(
                                                activity,
                                                activity.getString(
                                                        R.string.user_has_left_conference,
                                                        user.getDisplayName()),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            }
                            activity.highlightInMuc(user.getConversation(), user.resource());
                        });
        viewHolder.binding.getRoot().setTag(user);
        viewHolder.binding.getRoot().setOnCreateContextMenuListener(this);
        viewHolder
                .binding
                .getRoot()
                .setOnLongClickListener(
                        v -> {
                            selectedUser = user;
                            return false;
                        });
        final String resource = user.resource();
        final String displayName = user.getDisplayName();
        viewHolder.binding.contactDisplayName.setText(user.getDisplayName());
        final var jid = user.getRealJid();
        if (jid != null) {
            viewHolder.binding.contactJid.setText(jid);
            viewHolder.binding.contactJid.setVisibility(View.VISIBLE);
        } else {
            viewHolder.binding.contactJid.setVisibility(View.GONE);
        }
        if (user.getMucOptions().isPrivateAndNonAnonymous() && user.getPgpKeyId() != 0) {
            viewHolder.binding.key.setVisibility(View.VISIBLE);
            viewHolder.binding.key.setOnClickListener(
                    v -> {
                        final XmppActivity activity = XmppActivity.find(v);
                        final XmppConnectionService service =
                                activity == null ? null : activity.xmppConnectionService;
                        final PgpEngine pgpEngine = service == null ? null : service.getPgpEngine();
                        if (pgpEngine != null) {
                            PendingIntent intent = pgpEngine.getIntentForKey(user.getPgpKeyId());
                            if (intent != null) {
                                try {
                                    activity.startIntentSenderForResult(
                                            intent.getIntentSender(),
                                            0,
                                            null,
                                            0,
                                            0,
                                            0,
                                            Compatibility.pgpStartIntentSenderOptions());
                                } catch (IntentSender.SendIntentException ignored) {

                                }
                            }
                        }
                    });
            viewHolder.binding.key.setText(OpenPgpUtils.convertKeyIdToHex(user.getPgpKeyId()));
        } else {
            viewHolder.binding.key.setVisibility(View.GONE);
        }
        setHats(viewHolder.binding.tags, user.getDynamicTags());
    }

    public static void setHats(
            final ConstraintLayout layout, final List<MucOptions.DynamicTag> tags) {
        if (tags.isEmpty()) {
            layout.setVisibility(View.GONE);
        } else {
            final var context = layout.getContext();
            final var inflater = LayoutInflater.from(context);
            layout.removeViews(1, layout.getChildCount() - 1);
            final ImmutableList.Builder<Integer> viewIdBuilder = new ImmutableList.Builder<>();
            for (final var tag : tags) {
                final TextView tv = (TextView) inflater.inflate(R.layout.item_tag, layout, false);
                if (tag instanceof MucOptions.Hat hat) {
                    setTag(tv, hat);
                } else if (tag instanceof MucOptions.Attributes attributes) {
                    setAttributes(tv, attributes);
                } else {
                    throw new IllegalArgumentException("Could not render unknown tag");
                }
                final int id = View.generateViewId();
                tv.setId(id);
                viewIdBuilder.add(id);
                layout.addView(tv);
            }
            final Flow flowWidget = layout.findViewById(R.id.flow_widget);
            flowWidget.setReferencedIds(Ints.toArray(viewIdBuilder.build()));
            layout.setVisibility(View.VISIBLE);
        }
    }

    private static void setAttributes(
            final TextView textView, final MucOptions.Attributes attributes) {
        textView.setTextColor(
                MaterialColors.getColor(
                        textView, com.google.android.material.R.attr.colorOnSurfaceVariant));
        @ColorInt int color;
        @StringRes int title;
        switch (attributes.affiliation()) {
            case OWNER -> {
                color =
                        MaterialColors.getColor(
                                textView,
                                com.google.android.material.R.attr.colorSurfaceContainerHighest);
                title = R.string.owner;
            }
            case ADMIN -> {
                color =
                        MaterialColors.getColor(
                                textView,
                                com.google.android.material.R.attr.colorSurfaceContainerHigh);
                title = R.string.admin;
            }
            case MEMBER -> {
                color =
                        MaterialColors.getColor(
                                textView, com.google.android.material.R.attr.colorSurfaceContainer);
                title = R.string.member;
            }
            case OUTCAST -> {
                color =
                        MaterialColors.getColor(
                                textView,
                                com.google.android.material.R.attr.colorSurfaceContainerLowest);
                title = R.string.outcast;
            }
            default -> {
                color =
                        MaterialColors.getColor(
                                textView,
                                com.google.android.material.R.attr.colorSurfaceContainerLow);
                switch (attributes.role()) {
                    case MODERATOR -> title = R.string.moderator;
                    case PARTICIPANT -> title = R.string.participant;
                    case VISITOR -> title = R.string.visitor;
                    default ->
                            throw new IllegalArgumentException(
                                    "Dynamic tags should not be rendered for NONE/NONE attributes");
                }
            }
        }
        textView.setText(title);
        textView.setBackgroundTintList(ColorStateList.valueOf(color));
    }

    private static void setTag(final TextView textView, final MucOptions.Hat hat) {
        textView.setText(hat.title());
        @ColorInt final int color;
        if (hat.hue() == null) {
            color = XEP0392Helper.rgbFromNick(hat.uri());
        } else {
            color = XEP0392Helper.rgbFromAngle(hat.hue() % 360.0);
        }
        @ColorInt
        final int harmonizedColor =
                MaterialColors.harmonizeWithPrimary(textView.getContext(), color);
        textView.setBackgroundTintList(ColorStateList.valueOf(harmonizedColor));
    }

    public MucOptions.User getSelectedUser() {
        return selectedUser;
    }

    @Override
    public void onCreateContextMenu(
            ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        MucDetailsContextMenuHelper.onCreateContextMenu(menu, v);
    }

    public static final class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemContactBinding binding;

        private ViewHolder(ItemContactBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
