package eu.siacs.conversations.utils

import java.lang.ref.WeakReference

class WeakReferenceSet<T> : HashSet<WeakReference<T>>() {
    fun removeWeakReferenceTo(reference: T) {
        val it = iterator()
        while (it.hasNext()) {
            if (reference === it.next().get()) it.remove()
        }
    }

    fun addWeakReferenceTo(reference: T) {
        for (weakReference in this) {
            if (reference === weakReference.get()) return
        }
        add(WeakReference(reference))
    }
}
