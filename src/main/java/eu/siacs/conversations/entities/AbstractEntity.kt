package eu.siacs.conversations.entities

import android.content.ContentValues

abstract class AbstractEntity {

    companion object {
        const val UUID = "uuid"
    }

    protected var uuid: String? = null

    open fun getUuid(): String? = uuid

    abstract fun getContentValues(): ContentValues

    fun equals(entity: AbstractEntity): Boolean = uuid == entity.uuid
}
