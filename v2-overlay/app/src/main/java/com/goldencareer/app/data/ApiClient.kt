package com.goldencareer.app.data

import android.content.Context
import com.goldencareer.app.BuildConfig
import com.goldencareer.app.security.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    fun create(context:Context):ApiService{
        val tokens=TokenStore(context)
        val logging=HttpLoggingInterceptor().apply{level=if(BuildConfig.DEBUG)HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE}
        val client=OkHttpClient.Builder()
            .connectTimeout(18,TimeUnit.SECONDS).readTimeout(35,TimeUnit.SECONDS).writeTimeout(35,TimeUnit.SECONDS)
            .addInterceptor{chain->
                val b=chain.request().newBuilder().header("Accept","application/json").header("X-Golden-Career-App","android/2.0.0")
                tokens.accessToken()?.let{b.header("Authorization","Bearer $it")}
                chain.proceed(b.build())
            }
            .addInterceptor(logging).build()
        return Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
    }
}
