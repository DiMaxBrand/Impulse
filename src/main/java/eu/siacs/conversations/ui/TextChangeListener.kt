package eu.siacs.conversations.ui

import android.text.Editable
import android.text.TextWatcher
import eu.siacs.conversations.utils.CharSequences
import java.util.function.Consumer

class TextChangeListener(private val consumer: Consumer<String>) : TextWatcher {
    override fun afterTextChanged(s: Editable) = consumer.accept(CharSequences.nullToEmpty(s))
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
}
