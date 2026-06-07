package eu.siacs.conversations.ui.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class ImmediateAutoCompleteTextView : MaterialAutoCompleteTextView {

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun enoughToFilter(): Boolean {
        return true
    }
}
