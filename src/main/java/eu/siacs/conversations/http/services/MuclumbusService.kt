package eu.siacs.conversations.http.services

import eu.siacs.conversations.entities.Room
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.Collections

interface MuclumbusService {

    @GET("/api/1.0/rooms/unsafe")
    fun getRooms(@Query("p") page: Int): Call<Rooms>

    @POST("/api/1.0/search")
    fun search(@Body searchRequest: SearchRequest): Call<SearchResult>

    class Rooms {
        @JvmField var page: Int = 0
        @JvmField var total: Int = 0
        @JvmField var pages: Int = 0
        @JvmField var items: List<Room>? = null
    }

    class SearchRequest(keyword: String) {
        @JvmField val keywords: Set<String> = Collections.singleton(keyword)
    }

    class SearchResult {
        @JvmField var result: Result? = null
    }

    class Result {
        @JvmField var items: List<Room>? = null
    }
}
