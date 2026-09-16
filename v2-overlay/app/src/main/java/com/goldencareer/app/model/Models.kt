package com.goldencareer.app.model

data class ApiEnvelope<T>(val success:Boolean=false,val message:String?=null)
data class Tokens(val access_token:String,val refresh_token:String,val token_type:String="Bearer",val expires_in:Int=0)
data class User(val id:Long,val name:String,val email:String,val mobile:String?=null,val role:String="student")
data class LoginResponse(val success:Boolean=false,val message:String?=null,val user:User?=null,val tokens:Tokens?=null)
data class RegisterResponse(val success:Boolean=false,val message:String?=null,val user:User?=null,val tokens:Tokens?=null)

data class Banner(val id:Long,val title:String,val mobile_image:String?=null,val desktop_image:String?=null,val destination_type:String="none",val destination_value:String?=null)
data class Category(val id:Long,val name:String,val slug:String,val icon:String?=null)
data class HomeSection(val key:String,val title:String,val subtitle:String?=null,val sort_order:Int=0)
data class Course(
    val id:Long,val category_id:Long?=null,val title:String,val slug:String,val short_description:String?=null,val description:String?=null,
    val thumbnail:String?=null,val banner:String?=null,val course_type:String="paid",val price:Double=0.0,val original_price:Double?=null,
    val language:String?=null,val instructor:String?=null,val featured:Boolean=false,val popular:Boolean=false,val purchased:Boolean=false,val can_access:Boolean=false
)
data class LiveClass(val id:Long,val title:String,val teacher:String?=null,val starts_at:String,val status:String,val course_id:Long?=null,val description:String?=null)
data class Newspaper(val id:Long,val paper_date:String,val newspaper_name:String,val language:String,val thumbnail:String?=null,val file_id:Long?=null,val downloadable:Boolean=false)
data class HomeMiniCourse(val id:Long,val title:String,val slug:String,val thumbnail:String?=null,val expires_at:String?=null,val lessons:Int=0,val completed:Int=0)
data class HomeResponse(val success:Boolean=false,val message:String?=null,val greeting:String="Welcome",val banners:List<Banner> = emptyList(),val categories:List<Category> = emptyList(),val courses:List<Course> = emptyList(),val my_courses:List<HomeMiniCourse> = emptyList(),val live_classes:List<LiveClass> = emptyList(),val newspapers:List<Newspaper> = emptyList(),val sections:List<HomeSection> = emptyList())
data class CoursesResponse(val success:Boolean=false,val message:String?=null,val courses:List<Course> = emptyList(),val page:Int=1)

data class Section(val id:Long,val title:String,val lessons:List<LessonSummary> = emptyList())
data class LessonSummary(val id:Long,val title:String,val lesson_type:String,val video_provider:String?=null,val file_id:Long?=null,val is_preview:Boolean=false,val duration_seconds:Int?=null,val locked:Boolean=false)
data class CourseDetailResponse(val success:Boolean=false,val message:String?=null,val course:Course?=null,val sections:List<Section> = emptyList(),val purchased:Boolean=false,val can_access:Boolean=false,val premium_web_access:Boolean=false)

data class MyCourse(val id:Long,val title:String,val slug:String,val thumbnail:String?=null,val course_type:String="paid",val instructor:String?=null,val status:String,val starts_at:String?=null,val expires_at:String?=null,val completed:Int=0,val lessons:Int=0,val progress_percent:Int=0,val last_lesson_id:Long?=null,val last_lesson_title:String?=null)
data class MyCoursesResponse(val success:Boolean=false,val message:String?=null,val courses:List<MyCourse> = emptyList())

data class Pricing(val coupon_id:Long?=null,val mrp:Double=0.0,val discount:Double=0.0,val final_amount:Double=0.0,val code:String?=null)
data class CouponResponse(val success:Boolean=false,val message:String?=null,val pricing:Pricing?=null)
data class OrderCreateResponse(val success:Boolean=false,val message:String?=null,val internal_order_id:Long?=null,val order_id:String?=null,val razorpay_order_id:String?=null,val amount:Int=0,val currency:String="INR",val key_id:String?=null,val user_name:String?=null,val user_email:String?=null,val user_mobile:String?=null,val pricing:Pricing?=null,val payment_status:String?=null,val enrollment_status:String?=null,val course_id:Long?=null,val free_enrollment:Boolean=false)
data class VerifyResponse(val success:Boolean=false,val message:String?=null,val payment_status:String?=null,val enrollment_status:String?=null,val course_id:Long?=null)

data class AppVersionResponse(val success:Boolean=false,val latest_version:String="2.0.0",val minimum_version:String="1.0.1",val force_update:Boolean=false,val play_store_url:String?=null,val message:String?=null)
data class FeatureConfig(val chat:Boolean=true,val live:Boolean=true,val newspaper:Boolean=true,val mock_test:Boolean=true)
data class RazorpayConfig(val configured:Boolean=false,val mode:String="test")
data class AppConfig(val support_email:String?=null,val support_phone:String?=null,val razorpay:RazorpayConfig=RazorpayConfig(),val features:FeatureConfig=FeatureConfig())
data class AppConfigResponse(val success:Boolean=false,val config:AppConfig?=null)

data class MockTest(val id:Long,val title:String,val slug:String,val access_type:String,val price:Double=0.0,val duration_minutes:Int,val total_marks:Double)
data class MockTestsResponse(val success:Boolean=false,val message:String?=null,val mock_tests:List<MockTest> = emptyList())
data class NewspapersResponse(val success:Boolean=false,val message:String?=null,val newspapers:List<Newspaper> = emptyList())
data class LiveResponse(val success:Boolean=false,val message:String?=null,val live_classes:List<LiveClass> = emptyList())

data class ChatMessage(val id:Long,val user_id:Long,val name:String?=null,val message:String,val created_at:String,val is_pinned:Boolean=false)
data class ChatResponse(val success:Boolean=false,val message:String?=null,val messages:List<ChatMessage> = emptyList())
data class SendResponse(val success:Boolean=false,val message:String?=null,val message_id:Long?=null)
data class ProfileData(val id:Long,val name:String,val email:String,val mobile:String?=null,val address:String?=null,val city:String?=null,val state:String?=null,val pin:String?=null,val premium_web_access:Boolean=false)
data class ProfileResponse(val success:Boolean=false,val message:String?=null,val profile:ProfileData?=null)
data class NotificationItem(val id:Long,val title:String,val body:String,val sent_at:String?=null,val read_at:String?=null)
data class NotificationResponse(val success:Boolean=false,val message:String?=null,val notifications:List<NotificationItem> = emptyList())

data class LessonData(val id:Long,val course_id:Long,val title:String,val lesson_type:String,val content:String?=null,val file_id:Long?=null,val is_preview:Boolean=false,val playback_endpoint:String?=null)
data class LessonProgress(val completed:Int=0,val position_seconds:Int=0)
data class LessonResponse(val success:Boolean=false,val message:String?=null,val lesson:LessonData?=null,val progress:LessonProgress?=null)
data class ProgressResponse(val success:Boolean=false,val message:String?=null)

data class VideoWatermark(val text:String?=null,val name:String?=null,val user_id:Long?=null)
data class VideoPlaybackResponse(val success:Boolean=false,val message:String?=null,val provider:String?=null,val video_id:String?=null,val watermark:VideoWatermark?=null,val screen_secure_required:Boolean=true,val external_open_allowed:Boolean=false)
