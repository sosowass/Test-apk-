package com.o31languedoc.tvpremium;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.io.ByteArrayOutputStream;
public final class MainActivity extends Activity {
 WebView web;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  web=new WebView(this);web.setBackgroundColor(Color.rgb(8,24,43));web.setFocusable(true);web.setFocusableInTouchMode(true);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
  s.setAllowFileAccess(true);s.setMediaPlaybackRequiresUserGesture(false);s.setDefaultTextEncodingName("UTF-8");
  web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient());
  web.addJavascriptInterface(new Bridge(),"Tv");setContentView(web);
  web.loadUrl("file:///android_asset/index.html");web.requestFocus();
 }
 @Override public boolean onKeyDown(int code,KeyEvent e){
  if(code==KeyEvent.KEYCODE_BACK){web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);return true;}
  return super.onKeyDown(code,e);
 }
 @Override public void onBackPressed(){web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);}
 @Override protected void onResume(){super.onResume();if(web!=null)web.evaluateJavascript("window.__refreshApps&&window.__refreshApps()",null);}
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("Tv");web.destroy();web=null;}super.onDestroy();}
 private Map<String,ResolveInfo> available(){
  PackageManager pm=getPackageManager();Map<String,ResolveInfo> m=new LinkedHashMap<>();
  for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
   Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(cat);
   List<ResolveInfo> matches=pm.queryIntentActivities(q,0);
   for(ResolveInfo x:matches){if(x.activityInfo!=null && !x.activityInfo.packageName.equals(getPackageName()))m.put(x.activityInfo.packageName,x);}
  }return m;
 }
 public class Bridge {
  @JavascriptInterface public String apps(){
   JSONArray list=new JSONArray(); PackageManager pm=getPackageManager();
   for(Map.Entry<String,ResolveInfo> e:available().entrySet()){
    try{
     JSONObject o=new JSONObject();o.put("pkg",e.getKey());o.put("name",String.valueOf(e.getValue().loadLabel(pm)));
     try{
      Drawable icon=e.getValue().loadIcon(pm);
      if(icon!=null){Bitmap bitmap=Bitmap.createBitmap(72,72,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);icon.setBounds(0,0,72,72);icon.draw(c);
       ByteArrayOutputStream stream=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,90,stream);
       o.put("icon","data:image/png;base64,"+Base64.encodeToString(stream.toByteArray(),Base64.NO_WRAP));
       bitmap.recycle();stream.close();
      }
     }catch(Exception ignored){}
     list.put(o);
    }catch(Exception ignored){}
   }return list.toString();
  }
  @JavascriptInterface public void launch(String pkg) {
   if(pkg==null||pkg.length()>200)return;
   runOnUiThread(()->{try {
    if(!available().containsKey(pkg))return;
    PackageManager pm=getPackageManager();Intent i=pm.getLeanbackLaunchIntentForPackage(pkg);
    if(i==null)i=pm.getLaunchIntentForPackage(pkg);
    if(i!=null)startActivity(i);
   }catch(Exception ignored){}});
  }
  @JavascriptInterface public void maps(String link){
   if(link==null||!link.startsWith("https://www.google.com/maps/"))return;
   runOnUiThread(()->{try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}catch(Exception ignored){}});
  }
 }
}