package com.o31languedoc.tvpremium;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.os.Bundle;
import android.content.Intent;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.BufferedInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public final class MainActivity extends Activity {
 private WebView web;
 private volatile long adminAuthorizedUntil=0L;
 private static final String PREFS="o31_secure_settings";
 private static final String WEATHER_URL="https://api.open-meteo.com/v1/forecast?latitude=43.6047&longitude=1.4442&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=Europe%2FParis&forecast_days=3";
 private static final String AGENDA_URL="https://data.toulouse-metropole.fr/api/explore/v2.1/catalog/datasets/agenda-des-manifestations-culturelles-so-toulouse/records?limit=100";
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  web=new WebView(this);web.setBackgroundColor(Color.rgb(8,24,43));web.setFocusable(true);web.setFocusableInTouchMode(true);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
  s.setAllowFileAccess(true);s.setAllowContentAccess(false);s.setMediaPlaybackRequiresUserGesture(false);s.setDefaultTextEncodingName("UTF-8");
  s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    Uri uri=request.getUrl();return uri==null||!"file".equals(uri.getScheme())||!uri.toString().startsWith("file:///android_asset/");
   }
  });
  web.setWebChromeClient(new WebChromeClient());
  web.addJavascriptInterface(new Bridge(),"Tv");setContentView(web);
  web.loadUrl("file:///android_asset/index.html");web.requestFocus();
 }
 @Override public boolean onKeyDown(int code,KeyEvent e){
  if(code==KeyEvent.KEYCODE_BACK){if(web!=null)web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);return true;}
  return super.onKeyDown(code,e);
 }
 @Override public void onBackPressed(){
  if(web!=null)web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);
 }
 @Override protected void onResume(){super.onResume();if(web!=null)web.evaluateJavascript("window.__refreshApps&&window.__refreshApps()",null);}
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("Tv");web.destroy();web=null;}super.onDestroy();}
 private Map<String,ResolveInfo> available(){
  PackageManager pm=getPackageManager();Map<String,ResolveInfo> m=new LinkedHashMap<>();
  for(String cat:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
   Intent q=new Intent(Intent.ACTION_MAIN);q.addCategory(cat);
   List<ResolveInfo> matches=pm.queryIntentActivities(q,0);
   for(ResolveInfo x:matches){if(x.activityInfo!=null&&x.activityInfo.exported&&!x.activityInfo.packageName.equals(getPackageName()))m.put(x.activityInfo.packageName,x);}
  }return m;
 }
 private SharedPreferences prefs(){return getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
 private String hash(String salt,String pin){
  try{MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=digest.digest((salt+":"+pin).getBytes(StandardCharsets.UTF_8));return Base64.encodeToString(bytes,Base64.NO_WRAP);}
  catch(Exception ex){return "";}
 }
 private boolean authorized(){return System.currentTimeMillis()<adminAuthorizedUntil;}
 private void loadRemote(final String url,final String callback){
  new Thread(()->{
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(16000);
    c.setRequestProperty("Accept","application/json");c.setRequestProperty("User-Agent","O31TV/3 AndroidTV");
    c.connect();if(c.getResponseCode()!=200)return;
    InputStream is=new BufferedInputStream(c.getInputStream());ByteArrayOutputStream out=new ByteArrayOutputStream();
    byte[] buf=new byte[8192];int n;while((n=is.read(buf))!=-1){if(out.size()+n>600000)break;out.write(buf,0,n);}is.close();
    final String json=new String(out.toByteArray(),StandardCharsets.UTF_8);
    runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window."+callback+"&&window."+callback+"("+JSONObject.quote(json)+")",null);});
   }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
  },"O31-Network").start();
 }
 public class Bridge {
  @JavascriptInterface public String apps(){
   JSONArray list=new JSONArray();PackageManager pm=getPackageManager();
   for(Map.Entry<String,ResolveInfo> e:available().entrySet()){
    try{
     JSONObject o=new JSONObject();o.put("pkg",e.getKey());o.put("name",String.valueOf(e.getValue().loadLabel(pm)));
     try{Drawable icon=e.getValue().loadIcon(pm);if(icon!=null){
      Bitmap bitmap=Bitmap.createBitmap(72,72,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);icon.setBounds(0,0,72,72);icon.draw(c);
      ByteArrayOutputStream stream=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,90,stream);
      o.put("icon","data:image/png;base64,"+Base64.encodeToString(stream.toByteArray(),Base64.NO_WRAP));
      bitmap.recycle();stream.close();
     }}catch(Exception ignored){}
     list.put(o);
    }catch(Exception ignored){}
   }return list.toString();
  }
  @JavascriptInterface public void launch(String pkg){
   if(pkg==null||pkg.length()>200)return;
   runOnUiThread(()->{try{if(!available().containsKey(pkg))return;PackageManager pm=getPackageManager();Intent i=pm.getLeanbackLaunchIntentForPackage(pkg);if(i==null)i=pm.getLaunchIntentForPackage(pkg);if(i!=null)startActivity(i);}catch(Exception ignored){}});
  }
  @JavascriptInterface public void maps(String link){
   if(link==null||!link.startsWith("https://www.google.com/maps/"))return;
   runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link)));}catch(Exception ignored){}});
  }
  @JavascriptInterface public void requestWeather(){loadRemote(WEATHER_URL,"o31WeatherData");}
  @JavascriptInterface public void requestAgenda(){loadRemote(AGENDA_URL,"o31AgendaData");}
  @JavascriptInterface public void openOfficialAgenda(){
   runOnUiThread(()->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://metropole.toulouse.fr/agenda")));}catch(Exception ignored){}});
  }
  @JavascriptInterface public synchronized boolean pinConfigured(){return prefs().contains("pin_hash");}
  @JavascriptInterface public synchronized boolean setPin(String pin){
   if(pin==null||!pin.matches("[0-9]{6}")||pinConfigured())return false;
   byte[] saltBytes=new byte[24];new SecureRandom().nextBytes(saltBytes);String salt=Base64.encodeToString(saltBytes,Base64.NO_WRAP);
   boolean saved=prefs().edit().putString("pin_salt",salt).putString("pin_hash",hash(salt,pin)).commit();
   if(saved)adminAuthorizedUntil=System.currentTimeMillis()+180000L;
   return saved;
  }
  @JavascriptInterface public synchronized boolean verifyPin(String pin){
   if(pin==null||!pin.matches("[0-9]{6}")||!pinConfigured())return false;
   String encoded=hash(prefs().getString("pin_salt",""),pin);
   byte[] left=encoded.getBytes(StandardCharsets.UTF_8);
   byte[] right=prefs().getString("pin_hash","").getBytes(StandardCharsets.UTF_8);
   boolean ok=MessageDigest.isEqual(left,right);
   if(ok)adminAuthorizedUntil=System.currentTimeMillis()+180000L;
   return ok;
  }
  @JavascriptInterface public void openWiFi(){
   if(!authorized())return;
   runOnUiThread(()->{try{startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}catch(Exception ignored){}});
  }
  @JavascriptInterface public void exitToXiaomi(){
   if(!authorized())return;
   adminAuthorizedUntil=0L;
   runOnUiThread(()->{
    try{
     Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(Intent.CATEGORY_HOME);
     List<ResolveInfo> homes=getPackageManager().queryIntentActivities(query,0);
     ResolveInfo selected=null;
     String[] preferred={"com.google.android.apps.tv.launcherx","com.google.android.tvlauncher","com.mitv.tvhome","com.android.tvlauncher"};
     for(String p:preferred){for(ResolveInfo r:homes){if(r.activityInfo!=null&&p.equals(r.activityInfo.packageName)){selected=r;break;}}if(selected!=null)break;}
     if(selected==null)for(ResolveInfo r:homes){if(r.activityInfo!=null&&!r.activityInfo.packageName.equals(getPackageName())){selected=r;break;}}
     if(selected!=null){
      Intent home=new Intent(Intent.ACTION_MAIN);home.addCategory(Intent.CATEGORY_HOME);home.setClassName(selected.activityInfo.packageName,selected.activityInfo.name);
      home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);startActivity(home);
     }else startActivity(new Intent(Settings.ACTION_SETTINGS));
     finish();
    }catch(Exception e){try{startActivity(new Intent(Settings.ACTION_SETTINGS));finish();}catch(Exception ignored){}}
   });
  }
 }
}
