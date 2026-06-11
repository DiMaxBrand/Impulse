package eu.siacs.conversations.ui

import android.view.View
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableSet
import eu.siacs.conversations.R
import eu.siacs.conversations.entities.Reaction
import java.util.Locale
import java.util.function.Consumer
import java.util.function.Function

object BindingAdapters {

    @JvmStatic
    fun setReactionsOnReceived(
        chipGroup: ChipGroup,
        reactions: Reaction.Aggregated,
        onModifiedReactions: Consumer<Collection<String>>,
        onDetailsClicked: Function<String, Boolean>,
        addReaction: Runnable
    ) {
        setReactions(chipGroup, reactions, true, onModifiedReactions, onDetailsClicked, addReaction)
    }

    @JvmStatic
    fun setReactionsOnSent(
        chipGroup: ChipGroup,
        reactions: Reaction.Aggregated,
        onModifiedReactions: Consumer<Collection<String>>,
        onDetailsClicked: Function<String, Boolean>
    ) {
        setReactions(chipGroup, reactions, false, onModifiedReactions, onDetailsClicked, null)
    }

    private fun setReactions(
        chipGroup: ChipGroup,
        aggregated: Reaction.Aggregated,
        onReceived: Boolean,
        onModifiedReactions: Consumer<Collection<String>>,
        onDetailsClicked: Function<String, Boolean>,
        addReaction: Runnable?
    ) {
        val context = chipGroup.context
        val reactions = aggregated.reactions
        if (reactions == null || reactions.isEmpty()) {
            chipGroup.visibility = View.GONE
        } else {
            chipGroup.removeAllViews()
            chipGroup.visibility = View.VISIBLE
            for (reaction in reactions) {
                val emoji = reaction.key
                val count = reaction.value
                val chip = Chip(chipGroup.context)
                chip.setEnsureMinTouchTargetSize(false)
                chip.chipStartPadding = 0.0f
                chip.chipEndPadding = 0.0f
                if (count == 1) {
                    chip.text = emoji
                } else {
                    chip.text = String.format(Locale.ENGLISH, "%s %d", emoji, count)
                }
                val oneOfOurs = aggregated.ourReactions.contains(emoji)
                // received = surface; sent = surface high matches bubbles
                if (oneOfOurs) {
                    chip.setChipBackgroundColor(
                        MaterialColors.getColorStateListOrNull(
                            context,
                            com.google.android.material.R.attr.colorSurfaceContainerHighest
                        )
                    )
                } else {
                    chip.setChipBackgroundColor(
                        MaterialColors.getColorStateListOrNull(
                            context,
                            com.google.android.material.R.attr.colorSurfaceContainerLow
                        )
                    )
                }
                chip.setOnClickListener {
                    if (oneOfOurs) {
                        onModifiedReactions.accept(
                            ImmutableSet.copyOf(
                                Collections2.filter(aggregated.ourReactions) { r -> r != emoji }
                            )
                        )
                    } else {
                        onModifiedReactions.accept(
                            ImmutableSet.Builder<String>()
                                .addAll(aggregated.ourReactions)
                                .add(emoji)
                                .build()
                        )
                    }
                }
                chip.setOnLongClickListener { onDetailsClicked.apply(emoji) }
                chipGroup.addView(chip)
            }
            if (onReceived) {
                val chip = Chip(chipGroup.context)
                chip.setChipIconResource(R.drawable.ic_add_reaction_24dp)
                chip.setChipStrokeColor(
                    MaterialColors.getColorStateListOrNull(
                        chipGroup.context,
                        com.google.android.material.R.attr.colorTertiary
                    )
                )
                chip.setChipBackgroundColor(
                    MaterialColors.getColorStateListOrNull(
                        chipGroup.context,
                        com.google.android.material.R.attr.colorTertiaryContainer
                    )
                )
                chip.setChipIconTint(
                    MaterialColors.getColorStateListOrNull(
                        chipGroup.context,
                        com.google.android.material.R.attr.colorOnTertiaryContainer
                    )
                )
                chip.setEnsureMinTouchTargetSize(false)
                chip.textEndPadding = 0.0f
                chip.textStartPadding = 0.0f
                chip.setOnClickListener { addReaction!!.run() }
                chipGroup.addView(chip)
            }
        }
    }
}
