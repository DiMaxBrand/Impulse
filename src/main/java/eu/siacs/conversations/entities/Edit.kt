package eu.siacs.conversations.entities

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class Edit internal constructor(
    val editedId: String?,
    val serverMsgId: String?
) {

    private fun toJson(): JSONObject {
        val jsonObject = JSONObject()
        jsonObject.put("edited_id", editedId)
        jsonObject.put("server_msg_id", serverMsgId)
        return jsonObject
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val edit = other as Edit
        if (editedId != edit.editedId) return false
        return serverMsgId == edit.serverMsgId
    }

    override fun hashCode(): Int {
        var result = editedId?.hashCode() ?: 0
        result = 31 * result + (serverMsgId?.hashCode() ?: 0)
        return result
    }

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun toJson(edits: List<Edit>): String {
            val jsonArray = JSONArray()
            for (edit in edits) {
                jsonArray.put(edit.toJson())
            }
            return jsonArray.toString()
        }

        @JvmStatic
        fun wasPreviouslyEditedRemoteMsgId(edits: List<Edit>, remoteMsgId: String): Boolean {
            for (edit in edits) {
                if (edit.editedId != null && edit.editedId == remoteMsgId) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        fun wasPreviouslyEditedServerMsgId(edits: List<Edit>, serverMsgId: String): Boolean {
            for (edit in edits) {
                if (edit.serverMsgId != null && edit.serverMsgId == serverMsgId) {
                    return true
                }
            }
            return false
        }

        @JvmStatic
        @Throws(JSONException::class)
        private fun fromJson(jsonObject: JSONObject): Edit {
            val edited = if (jsonObject.has("edited_id")) jsonObject.getString("edited_id") else null
            val serverMsgId = if (jsonObject.has("server_msg_id")) jsonObject.getString("server_msg_id") else null
            return Edit(edited, serverMsgId)
        }

        @JvmStatic
        fun fromJson(input: String?): List<Edit> {
            val list = ArrayList<Edit>()
            if (input == null) {
                return list
            }
            return try {
                val jsonArray = JSONArray(input)
                for (i in 0 until jsonArray.length()) {
                    list.add(fromJson(jsonArray.getJSONObject(i)))
                }
                list
            } catch (e: JSONException) {
                list
            }
        }
    }
}
