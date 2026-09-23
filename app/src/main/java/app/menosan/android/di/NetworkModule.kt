package app.menosan.android.di

import app.menosan.android.BuildConfig
import app.menosan.android.core.network.AuthInterceptor
import app.menosan.android.core.network.DebugLogInterceptor
import app.menosan.android.core.network.MenosanJson
import app.menosan.android.data.remote.MenosanApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = MenosanJson

    /** Timeouts come from the flavor: staging is generous because Render Free can take ~60 s to wake up. */
    @Provides
    @Singleton
    fun okHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(BuildConfig.HTTP_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.HTTP_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(BuildConfig.HTTP_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .apply { if (BuildConfig.DEBUG) addInterceptor(DebugLogInterceptor()) }
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun menosanApi(retrofit: Retrofit): MenosanApi = retrofit.create(MenosanApi::class.java)
}
