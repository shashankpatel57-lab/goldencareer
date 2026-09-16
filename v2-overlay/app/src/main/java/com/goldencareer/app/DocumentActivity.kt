package com.goldencareer.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.goldencareer.app.security.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class DocumentActivity:ComponentActivity(){
    private var renderer:PdfRenderer?=null;private var pfd:ParcelFileDescriptor?=null;private var pageIndex=0
    private lateinit var image:ImageView;private lateinit var status:TextView;private lateinit var prev:Button;private lateinit var next:Button
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);val fileId=intent.getLongExtra("file_id",0);if(fileId<=0){finish();return};buildUi();loadPdf(fileId)}
    private fun buildUi(){
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(245,247,250))}
        val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(18,12,18,12);setBackgroundColor(Color.WHITE)}
        val close=Button(this).apply{text="←";setOnClickListener{finish()}}
        val title=TextView(this).apply{text=intent.getStringExtra("title")?:"Golden Career PDF";textSize=16f;setTextColor(Color.rgb(7,17,31));setTypeface(typeface,android.graphics.Typeface.BOLD);setPadding(12,0,0,0)}
        top.addView(close,LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT));top.addView(title,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(top)
        val scroll=ScrollView(this);image=ImageView(this).apply{adjustViewBounds=true;scaleType=ImageView.ScaleType.FIT_CENTER;setPadding(8,8,8,8)};scroll.addView(image,ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));root.addView(scroll,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        val bottom=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER;setPadding(10,8,10,12);setBackgroundColor(Color.WHITE)}
        prev=Button(this).apply{text="Previous";isEnabled=false;setOnClickListener{showPage(pageIndex-1)}};status=TextView(this).apply{text="Loading secure document…";gravity=Gravity.CENTER;textSize=12f;setTextColor(Color.DKGRAY)};next=Button(this).apply{text="Next";isEnabled=false;setOnClickListener{showPage(pageIndex+1)}}
        bottom.addView(prev,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));bottom.addView(status,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1.4f));bottom.addView(next,LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));root.addView(bottom);setContentView(root)
    }
    private fun loadPdf(id:Long){lifecycleScope.launch{try{val file=withContext(Dispatchers.IO){val token=TokenStore(this@DocumentActivity).accessToken()?:error("Session expired");val req=Request.Builder().url(BuildConfig.API_BASE_URL+"files/$id/content").header("Authorization","Bearer $token").header("X-Golden-Career-App","android/2.0.0").build();val r=OkHttpClient().newCall(req).execute();if(!r.isSuccessful)error("Document could not be opened (${r.code})");val body=r.body?:error("Empty document");val out=File(cacheDir,"gc-pdf-$id.pdf");out.outputStream().use{body.byteStream().copyTo(it)};out};pfd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY);renderer=PdfRenderer(pfd!!);showPage(0)}catch(t:Throwable){status.text=t.message?:"Could not open document";Toast.makeText(this@DocumentActivity,status.text,Toast.LENGTH_LONG).show()}}}
    private fun showPage(i:Int){val r=renderer?:return;if(i<0||i>=r.pageCount)return;val p=r.openPage(i);val width=(resources.displayMetrics.widthPixels*1.6).toInt().coerceAtLeast(800);val height=(width.toFloat()/p.width*p.height).toInt();val bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bmp.eraseColor(Color.WHITE);p.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();image.setImageBitmap(bmp);pageIndex=i;status.text="Page ${i+1} of ${r.pageCount}";prev.isEnabled=i>0;next.isEnabled=i<r.pageCount-1}
    override fun onDestroy(){renderer?.close();pfd?.close();super.onDestroy()}
}
