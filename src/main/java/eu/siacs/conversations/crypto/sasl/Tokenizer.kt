package eu.siacs.conversations.crypto.sasl

/** A tokenizer for GS2 header strings */
class Tokenizer(challenge: ByteArray) : Iterator<String>, Iterable<String> {

    private val parts: MutableList<String>
    private var index: Int = 0

    init {
        val challengeString = String(challenge)
        parts = challengeString.split(",").map { it.trim() }.toMutableList()
    }

    /**
     * Returns true if there is at least one more element, false otherwise.
     *
     * @see next
     */
    override fun hasNext(): Boolean = parts.size != index + 1

    /**
     * Returns the next object and advances the iterator.
     *
     * @return the next object.
     * @throws java.util.NoSuchElementException if there are no more elements.
     * @see hasNext
     */
    override fun next(): String {
        if (hasNext()) {
            return parts[index++]
        } else {
            throw NoSuchElementException("No such element. Size is: ${parts.size}")
        }
    }

    /**
     * Removes the last object returned by [next] from the collection. This method can only be
     * called once between each call to [next].
     *
     * @throws UnsupportedOperationException if removing is not supported by the collection being
     *     iterated.
     * @throws IllegalStateException if [next] has not been called, or [remove] has already been
     *     called after the last call to [next].
     */
    fun remove() {
        if (index <= 0) {
            throw IllegalStateException(
                "You can't delete an element before first next() method call"
            )
        }
        parts.removeAt(--index)
    }

    /**
     * Returns an [Iterator] for the elements in this object.
     *
     * @return An [Iterator] instance.
     */
    override fun iterator(): Iterator<String> = parts.iterator()
}
