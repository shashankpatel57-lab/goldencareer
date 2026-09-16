package com.goldencareer.app.data

import com.goldencareer.app.model.*
import retrofit2.http.*

interface ApiService {
    @POST("auth/login") suspend fun login(@Body body:Map<String,String>): LoginResponse
    @POST("auth/register") suspend fun register(@Body body:Map<String,String>): RegisterResponse
    @POST("auth/refresh") suspend fun refresh(@Body body:Map<String,String>): Map<String,Any>
    @POST("auth/logout") suspend fun logout(): ApiEnvelope<Any>
    @GET("home") suspend fun home(): HomeResponse
    @GET("courses") suspend fun courses(@Query("q") query:String?=null): CoursesResponse
    @GET("courses/{id}") suspend fun course(@Path("id") id:Long): CourseDetailResponse
    @GET("my-courses") suspend fun myCourses(): MyCoursesResponse
    @GET("lessons/{id}") suspend fun lesson(@Path("id") id:Long): LessonResponse
    @POST("lesson/{id}/progress") suspend fun progress(@Path("id") id:Long,@Body body:Map<String,Any>): ProgressResponse
    @POST("coupons/validate") suspend fun validateCoupon(@Body body:Map<String,Any>): CouponResponse
    @POST("orders/create") suspend fun createOrder(@Body body:Map<String,Any?>): OrderCreateResponse
    @POST("payments/verify") suspend fun verifyPayment(@Body body:Map<String,String>): VerifyResponse
    @GET("newspapers") suspend fun newspapers(): NewspapersResponse
    @GET("mock-tests") suspend fun mockTests(): MockTestsResponse
    @GET("live") suspend fun live(): LiveResponse
    @GET("chat") suspend fun chat(@Query("course_id") courseId:Long): ChatResponse
    @POST("chat") suspend fun sendChat(@Body body:Map<String,Any>): SendResponse
    @GET("support") suspend fun support(): ChatResponse
    @POST("support") suspend fun sendSupport(@Body body:Map<String,String>): SendResponse
    @GET("profile") suspend fun profile(): ProfileResponse
    @GET("notifications") suspend fun notifications(): NotificationResponse
    @GET("app/config") suspend fun appConfig(): AppConfigResponse
    @GET("app/version") suspend fun appVersion(): AppVersionResponse
    @POST("videos/{id}/playback") suspend fun videoPlayback(@Path("id") lessonId:Long): VideoPlaybackResponse
}
