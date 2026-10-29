package eu.siacs.conversations.utils

/** The chat with yourself is called "Saved messages", wherever its name is shown. */
object SavedMessages {
    /** Same pattern as [SupportAccounts.displayName]: no context here, so the two languages the app has. */
    @JvmStatic
    fun title(): String = if (java.util.Locale.getDefault().language == "ru") "Избранное" else "Saved messages"

    /** The Material Symbols Rounded bookmark (filled), as path data in a 960 x 960 box: the avatar of the chat. */
    const val BOOKMARK_PATH =
        "M480,720L312,792Q272,809 236,785.5Q200,762 200,719L200,200Q200,167 223.5,143.5Q247,120 280,120L680,120Q713,120 736.5,143.5Q760,167 760,200L760,719Q760,762 724,785.5Q688,809 648,792L480,720Z"
}
