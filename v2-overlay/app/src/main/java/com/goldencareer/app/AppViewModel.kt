package com.goldencareer.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldencareer.app.data.ApiClient
import com.goldencareer.app.model.*
import com.goldencareer.app.security.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException

sealed class LoadState<out T>{data object Idle:LoadState<Nothing>();data object Loading:LoadState<Nothing>();data class Data<T>(val value:T):LoadState<T>();data class Error(val message:String):LoadState<Nothing>()}

data class AppUiState(
    val authenticated:Boolean=false,val user:User?=null,
    val home:LoadState<HomeResponse> = LoadState.Idle,val courses:LoadState<CoursesResponse> = LoadState.Idle,val myCourses:LoadState<MyCoursesResponse> = LoadState.Idle,
    val live:LoadState<LiveResponse> = LoadState.Idle,val newspapers:LoadState<NewspapersResponse> = LoadState.Idle,val mocks:LoadState<MockTestsResponse> = LoadState.Idle,
    val profile:LoadState<ProfileResponse> = LoadState.Idle,val notifications:LoadState<NotificationResponse> = LoadState.Idle,val appConfig:LoadState<AppConfigResponse> = LoadState.Idle,
    val selectedCourse:LoadState<CourseDetailResponse> = LoadState.Idle,val selectedLesson:LoadState<LessonResponse> = LoadState.Idle,
    val busy:Boolean=false,val toast:String?=null,val lastRefresh:Long=0L
)

class AppViewModel(app:Application):AndroidViewModel(app){
    private val api=ApiClient.create(app);private val tokens=TokenStore(app)
    private val _ui=MutableStateFlow(AppUiState(authenticated=tokens.accessToken()!=null));val ui:StateFlow<AppUiState> = _ui.asStateFlow()
    var pendingRazorpayOrderId:String?=null;private set
    init{if(_ui.value.authenticated)refreshAll()}

    private fun error(t:Throwable):String{
        if(t is HttpException){val raw=runCatching{t.response()?.errorBody()?.string()}.getOrNull();if(!raw.isNullOrBlank())return runCatching{JSONObject(raw).optString("message").takeIf{it.isNotBlank()}}.getOrNull()?:"Request failed (${t.code()}).";if(t.code()==401)return "Session expired. Please log in again."}
        return t.message?.takeIf{it.isNotBlank()}?:"Couldn't connect to Golden Career. Check your internet and retry."
    }
    fun consumeToast(){_ui.value=_ui.value.copy(toast=null)}
    fun login(identity:String,password:String,deviceUuid:String,deviceName:String){viewModelScope.launch{_ui.value=_ui.value.copy(busy=true,toast=null);runCatching{api.login(mapOf("email" to identity.trim(),"password" to password,"device_uuid" to deviceUuid,"device_name" to deviceName,"source" to "ANDROID"))}.onSuccess{r->if(r.success&&r.tokens!=null&&r.user!=null){tokens.saveTokens(r.tokens.access_token,r.tokens.refresh_token);_ui.value=_ui.value.copy(authenticated=true,user=r.user,busy=false);refreshAll()}else _ui.value=_ui.value.copy(busy=false,toast=r.message?:"Login failed.")}.onFailure{_ui.value=_ui.value.copy(busy=false,toast=error(it))}}}
    fun register(name:String,email:String,mobile:String,password:String,deviceUuid:String,deviceName:String){viewModelScope.launch{_ui.value=_ui.value.copy(busy=true,toast=null);runCatching{api.register(mapOf("name" to name.trim(),"email" to email.trim(),"mobile" to mobile.trim(),"password" to password,"device_uuid" to deviceUuid,"device_name" to deviceName,"source" to "ANDROID"))}.onSuccess{r->if(r.success&&r.tokens!=null&&r.user!=null){tokens.saveTokens(r.tokens.access_token,r.tokens.refresh_token);_ui.value=_ui.value.copy(authenticated=true,user=r.user,busy=false);refreshAll()}else _ui.value=_ui.value.copy(busy=false,toast=r.message?:"Registration failed.")}.onFailure{_ui.value=_ui.value.copy(busy=false,toast=error(it))}}}
    fun logout(){viewModelScope.launch{runCatching{api.logout()};tokens.clear();_ui.value=AppUiState(authenticated=false)}}

    fun refreshAll(){loadHome();loadCourses();loadMyCourses();loadLive();loadNewspapers();loadMocks();loadProfile();loadNotifications();loadConfig();_ui.value=_ui.value.copy(lastRefresh=System.currentTimeMillis())}
    fun refreshIfStale(){if(_ui.value.authenticated&&System.currentTimeMillis()-_ui.value.lastRefresh>20_000)refreshAll()}
    fun loadHome(){viewModelScope.launch{_ui.value=_ui.value.copy(home=LoadState.Loading);runCatching{api.home()}.onSuccess{_ui.value=_ui.value.copy(home=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(home=LoadState.Error(error(it)))}}}
    fun loadCourses(q:String?=null){viewModelScope.launch{_ui.value=_ui.value.copy(courses=LoadState.Loading);runCatching{api.courses(q?.takeIf{it.isNotBlank()})}.onSuccess{_ui.value=_ui.value.copy(courses=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(courses=LoadState.Error(error(it)))}}}
    fun loadMyCourses(){viewModelScope.launch{_ui.value=_ui.value.copy(myCourses=LoadState.Loading);runCatching{api.myCourses()}.onSuccess{_ui.value=_ui.value.copy(myCourses=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(myCourses=LoadState.Error(error(it)))}}}
    fun loadLive(){viewModelScope.launch{runCatching{api.live()}.onSuccess{_ui.value=_ui.value.copy(live=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(live=LoadState.Error(error(it)))}}}
    fun loadNewspapers(){viewModelScope.launch{runCatching{api.newspapers()}.onSuccess{_ui.value=_ui.value.copy(newspapers=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(newspapers=LoadState.Error(error(it)))}}}
    fun loadMocks(){viewModelScope.launch{runCatching{api.mockTests()}.onSuccess{_ui.value=_ui.value.copy(mocks=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(mocks=LoadState.Error(error(it)))}}}
    fun loadProfile(){viewModelScope.launch{runCatching{api.profile()}.onSuccess{r->_ui.value=_ui.value.copy(profile=LoadState.Data(r));r.profile?.let{p->_ui.value=_ui.value.copy(user=User(p.id,p.name,p.email,p.mobile))}}.onFailure{_ui.value=_ui.value.copy(profile=LoadState.Error(error(it)))}}}
    fun loadNotifications(){viewModelScope.launch{runCatching{api.notifications()}.onSuccess{_ui.value=_ui.value.copy(notifications=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(notifications=LoadState.Error(error(it)))}}}
    fun loadConfig(){viewModelScope.launch{runCatching{api.appConfig()}.onSuccess{_ui.value=_ui.value.copy(appConfig=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(appConfig=LoadState.Error(error(it)))}}}
    fun openCourse(id:Long){viewModelScope.launch{_ui.value=_ui.value.copy(selectedCourse=LoadState.Loading);runCatching{api.course(id)}.onSuccess{_ui.value=_ui.value.copy(selectedCourse=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(selectedCourse=LoadState.Error(error(it)))}}}
    fun closeCourse(){_ui.value=_ui.value.copy(selectedCourse=LoadState.Idle,selectedLesson=LoadState.Idle)}
    fun openTextLesson(id:Long){viewModelScope.launch{_ui.value=_ui.value.copy(selectedLesson=LoadState.Loading);runCatching{api.lesson(id)}.onSuccess{_ui.value=_ui.value.copy(selectedLesson=LoadState.Data(it))}.onFailure{_ui.value=_ui.value.copy(selectedLesson=LoadState.Error(error(it)))}}}
    fun closeLesson(){_ui.value=_ui.value.copy(selectedLesson=LoadState.Idle)}
    fun markComplete(id:Long){viewModelScope.launch{runCatching{api.progress(id,mapOf("completed" to true,"position_seconds" to 0))}.onSuccess{_ui.value=_ui.value.copy(toast="Lesson marked complete");loadMyCourses();val cid=(_ui.value.selectedCourse as? LoadState.Data)?.value?.course?.id;cid?.let(::openCourse)}.onFailure{_ui.value=_ui.value.copy(toast=error(it))}}}

    suspend fun createOrder(courseId:Long,coupon:String):OrderCreateResponse=try{api.createOrder(mapOf("course_id" to courseId,"coupon_code" to coupon.trim().uppercase(),"source" to "ANDROID"))}catch(t:Throwable){OrderCreateResponse(success=false,message=error(t))}
    fun setPendingRazorOrder(orderId:String?){pendingRazorpayOrderId=orderId}
    fun paymentVerified(message:String="Course unlocked successfully"){pendingRazorpayOrderId=null;_ui.value=_ui.value.copy(toast=message,busy=false);loadMyCourses();loadCourses();loadHome();(_ui.value.selectedCourse as? LoadState.Data)?.value?.course?.id?.let(::openCourse)}
    fun paymentFailed(message:String){_ui.value=_ui.value.copy(toast=message,busy=false)}
    fun verifyPayment(paymentId:String,orderId:String,signature:String){viewModelScope.launch{_ui.value=_ui.value.copy(busy=true);runCatching{api.verifyPayment(mapOf("razorpay_payment_id" to paymentId,"razorpay_order_id" to orderId,"razorpay_signature" to signature))}.onSuccess{r->if(r.success&&r.payment_status=="paid")paymentVerified() else paymentFailed(r.message?:"Payment verification failed.")}.onFailure{paymentFailed(error(it))}}}
    suspend fun requestVideoPlayback(lessonId:Long):VideoPlaybackResponse=try{api.videoPlayback(lessonId)}catch(t:Throwable){VideoPlaybackResponse(success=false,message=error(t))}
    fun sendChat(courseId:Long,message:String){viewModelScope.launch{runCatching{api.sendChat(mapOf("course_id" to courseId,"message" to message))}.onSuccess{_ui.value=_ui.value.copy(toast="Message sent")}.onFailure{_ui.value=_ui.value.copy(toast=error(it))}}}
}
