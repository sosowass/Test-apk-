package com.o31languedoc.tvtest;
import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.graphics.Color;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
 private WebView view;
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(5894|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
  view=new WebView(this); view.setBackgroundColor(Color.rgb(7,19,34)); view.setFocusable(true); view.setFocusableInTouchMode(true);
  WebSettings settings=view.getSettings();
  settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true); settings.setMediaPlaybackRequiresUserGesture(false);
  view.setWebChromeClient(new WebChromeClient()); view.setWebViewClient(new WebViewClient());
  view.addJavascriptInterface(new Bridge(),"O31");
  setContentView(view);view.loadUrl("file:///android_asset/index.html");view.requestFocus();
 }
 @Override public boolean onKeyDown(int code,KeyEvent evt){
  if(code==KeyEvent.KEYCODE_BACK){
   view.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))",null); return true;
  }
  return super.onKeyDown(code,evt);
 }
 @Override public void onBackPressed(){
  if(view!=null) view.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape'}))",null);
 }
 public final class Bridge {
  @JavascriptInterface public String apps(){
   JSONArray result=new JSONArray(); PackageManager pm=getPackageManager();
   try {
    LinkedHashMap<String,ResolveInfo> found=new LinkedHashMap<>();
    for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
     Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(cat);
     for(ResolveInfo info:pm.queryIntentActivities(q,0)){
      if(info.activityInfo!=null && !info.activityInfo.packageName.equals(getPackageName()))found.put(info.activityInfo.packageName,info);
     }
    }
    for(Map.Entry<String,ResolveInfo> entry:found.entrySet()){
     JSONObject ob=new JSONObject();ob.put("name",String.valueOf(entry.getValue().loadLabel(pm)));ob.put("pkg",entry.getKey());result.put(ob);
    }
   }catch(Exception ignored){}
   return result.toString();
  }
  @JavascriptInterface public void launch(String pkg){
   if(pkg==null || pkg.length()>150)return;
   runOnUiThread(()->{
    try{
     PackageManager pm=getPackageManager();Intent i=pm.getLeanbackLaunchIntentForPackage(pkg);
     if(i==null)i=pm.getLaunchIntentForPackage(pkg);
     if(i!=null)startActivity(i);
    }catch(Exception ignored){}
   });
  }
 }
}
