package student.projects.spotmap.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*
import student.projects.spotmap.Comment
import student.projects.spotmap.Spot

data class SpotResponse(
    val success: Boolean,
    val message: String,
    val spotId: String?
)

interface SpotApi {

    @Multipart
    @POST("api/spots/add")
    fun addSpot(
        @Part("userId") userId: RequestBody,
        @Part("username") username: RequestBody,
        @Part("name") name: RequestBody,
        @Part("description") desc: RequestBody,
        @Part("tags") tags: RequestBody,
        @Part("lat") lat: RequestBody,
        @Part("lng") lng: RequestBody,
        @Part("isPublic") isPublic: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Call<SpotResponse>

    @GET("api/spots/public")
    fun getPublicSpots(): Call<List<Spot>>

    @GET("api/spots/{id}")
    fun getSpot(@Path("id") spotId: String): Call<Spot>

    @POST("api/spots/{id}/comment")
    fun addComment(
        @Path("id") spotId: String,
        @Body comment: Comment
    ): Call<Spot>

    @POST("api/spots/{id}/rate")
    fun addRating(
        @Path("id") spotId: String,
        @Body body: Map<String, Int>
    ): Call<Spot>
}

