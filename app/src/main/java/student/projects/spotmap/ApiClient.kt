package student.projects.spotmap.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    private const val BASE_URL = "https://spotapi20250929120915-endpfnbaafbte3dj.southafricanorth-01.azurewebsites.net/"

    val instance: SpotApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(SpotApi::class.java)
    }
}
