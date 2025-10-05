package student.projects.spotmap

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import student.projects.spotmap.model.DirectionsResponse

interface DirectionsApiService {
    @GET("directions/json")
    fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String
    ): Call<DirectionsResponse>
}