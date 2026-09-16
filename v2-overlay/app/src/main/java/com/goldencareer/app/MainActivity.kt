package com.goldencareer.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.goldencareer.app.model.Course
import com.goldencareer.app.model.LessonSummary
import com.goldencareer.app.ui.GoldenCareerApp
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity:ComponentActivity(),PaymentResultWithDataListener{
    private lateinit var vm:AppViewModel
    private val deviceUuid:String by lazy{Settings.Secure.getString(contentResolver,Settings.Secure.ANDROID_ID)?:"gc-android"}
    private val deviceName:String get()="${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);Checkout.preload(applicationContext);vm=ViewModelProvider(this)[AppViewModel::class.java];setContent{GoldenCareerApp(vm=vm,deviceUuid=deviceUuid,deviceName=deviceName,onStartPayment=::startPurchase,onOpenLesson=::openLesson)}}
    override fun onResume(){super.onResume();if(::vm.isInitialized)vm.refreshIfStale()}

    private fun openLesson(lesson:LessonSummary){
        if(lesson.locked){Toast.makeText(this,"This lesson requires active course access.",Toast.LENGTH_SHORT).show();return}
        when(lesson.lesson_type){
            "video"->openProtectedYoutube(lesson.id)
            "pdf"->lesson.file_id?.let{startActivity(Intent(this,DocumentActivity::class.java).putExtra("file_id",it).putExtra("title",lesson.title))}?:Toast.makeText(this,"PDF is not attached yet.",Toast.LENGTH_LONG).show()
            else->vm.openTextLesson(lesson.id)
        }
    }
    private fun startPurchase(course:Course,coupon:String){lifecycleScope.launch{
        val order=vm.createOrder(course.id,coupon);if(!order.success){vm.paymentFailed(order.message?:"Could not start payment.");return@launch}
        if(order.payment_status in listOf("paid","already_enrolled","not_required")){vm.paymentVerified(if(order.payment_status=="not_required")"Free course added to My Courses" else "Course is active");return@launch}
        val razorOrder=order.razorpay_order_id;val key=order.key_id;if(razorOrder.isNullOrBlank()||key.isNullOrBlank()||order.amount<=0){vm.paymentFailed("Razorpay is not fully configured on the Golden Career server.");return@launch}
        try{vm.setPendingRazorOrder(razorOrder);val checkout=Checkout();checkout.setKeyID(key);checkout.setImage(R.drawable.ic_launcher_foreground);val options=JSONObject().apply{put("name","Golden Career");put("description",course.title);put("currency",order.currency);put("amount",order.amount);put("order_id",razorOrder);put("prefill",JSONObject().apply{put("name",order.user_name?:"");put("email",order.user_email?:"");put("contact",order.user_mobile?:"")});put("theme",JSONObject().put("color","#07182D"));put("retry",JSONObject().put("enabled",true))};checkout.open(this@MainActivity,options)}catch(t:Throwable){vm.paymentFailed(t.message?:"Razorpay checkout could not open.")}
    }}
    private fun openProtectedYoutube(lessonId:Long){lifecycleScope.launch{val p=vm.requestVideoPlayback(lessonId);if(!p.success||p.video_id.isNullOrBlank()){Toast.makeText(this@MainActivity,p.message?:"Video authorization failed.",Toast.LENGTH_LONG).show();return@launch};startActivity(Intent(this@MainActivity,YoutubePlayerActivity::class.java).putExtra("video_id",p.video_id).putExtra("watermark",p.watermark?.text?:"Golden Career"))}}
    override fun onPaymentSuccess(razorpayPaymentID:String?,paymentData:PaymentData?){val paymentId=paymentData?.paymentId?:razorpayPaymentID;val orderId=paymentData?.orderId?:vm.pendingRazorpayOrderId;val sig=paymentData?.signature;if(paymentId.isNullOrBlank()||orderId.isNullOrBlank()||sig.isNullOrBlank()){vm.paymentFailed("Razorpay returned incomplete verification data. Please contact support if money was debited.");return};vm.verifyPayment(paymentId,orderId,sig)}
    override fun onPaymentError(code:Int,response:String?,paymentData:PaymentData?){vm.paymentFailed(if(code==Checkout.PAYMENT_CANCELED)"Payment cancelled. Your course was not charged." else response?:"Payment failed ($code). You can retry safely.")}
}
