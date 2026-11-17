package com.o31languedoc.hotel;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.content.Intent;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Arrays;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.io.InputStream;


public final class MainActivity extends Activity {
 private WebView web;
 private SharedPreferences state;
 private volatile long adminUntil=0;
 private volatile boolean externalApp=false;
 private volatile int failedPins=0;
 private volatile long lockedUntil=0;
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);state=getSharedPreferences("hotel_private",MODE_PRIVATE);
  getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  web=new WebView(this);web.setBackgroundColor(Color.rgb(5,21,37));web.setFocusable(true);web.setFocusableInTouchMode(true);
  WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setAllowFileAccess(true);
  ws.setMediaPlaybackRequiresUserGesture(false);ws.setDefaultTextEncodingName("UTF-8");
  ws.setAllowFileAccessFromFileURLs(false);ws.setAllowUniversalAccessFromFileURLs(false);
  web.setWebViewClient(new WebViewClient(){
    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
      return !req.getUrl().toString().startsWith("file:///android_asset/");
    }
  });
  web.setWebChromeClient(new WebChromeClient());
  web.addJavascriptInterface(new Bridge(),"O31");setContentView(web);
  web.loadUrl("file:///android_asset/index.html");web.requestFocus();
 }
 @Override protected void onResume(){super.onResume();if(web!=null)web.postDelayed(()->{if(web!=null)web.evaluateJavascript("window.O31_onResume&&window.O31_onResume()",null);},200);}
 @Override protected void onPause(){if(web!=null)web.evaluateJavascript("document.getElementById('heroMovie')?.pause();document.getElementById('ambientAudio')?.pause();",null);super.onPause();}
 @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("O31");web.destroy();web=null;}super.onDestroy();}
 @Override public boolean onKeyDown(int k,KeyEvent e){if(k==KeyEvent.KEYCODE_BACK){if(web!=null)web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);return true;}return super.onKeyDown(k,e);}
 @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}))",null);else super.onBackPressed();}
 private Map<String,ResolveInfo> appList(){
  PackageManager pm=getPackageManager();Map<String,ResolveInfo> apps=new LinkedHashMap<>();
  for(String category:Arrays.asList(Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER)){
    Intent i=new Intent(Intent.ACTION_MAIN);i.addCategory(category);
    for(ResolveInfo res:pm.queryIntentActivities(i,0)){
      if(res.activityInfo!=null && !res.activityInfo.packageName.equals(getPackageName()))
        apps.put(res.activityInfo.packageName,res);
    }
  }return apps;
 }
 private boolean launchPackage(String pkg){
  try{
    if(!appList().containsKey(pkg))return false;
    PackageManager pm=getPackageManager();Intent i=pm.getLeanbackLaunchIntentForPackage(pkg);
    if(i==null)i=pm.getLaunchIntentForPackage(pkg);
    if(i==null)return false;externalApp=true;i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);return true;
  }catch(Exception ex){return false;}
 }
 private String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte x:b)s.append(String.format(java.util.Locale.ROOT,"%02x",x&0xff));return s.toString();}
 private byte[] sha(String raw,byte[] salt){
  try{MessageDigest dig=MessageDigest.getInstance("SHA-256");dig.update(salt);dig.update(raw.getBytes(StandardCharsets.UTF_8));return dig.digest();}catch(Exception e){return new byte[32];}
 }
 private boolean validPin(String x){return x!=null&&x.matches("[0-9]{6}");}
 private void launchActivity(Intent i){runOnUiThread(()->{try{i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);}catch(Exception ignored){}});}
 private void goToSystemHome(){
  runOnUiThread(()->{
    PackageManager pm=getPackageManager();
    Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    List<ResolveInfo> list=pm.queryIntentActivities(query,0);
    for(ResolveInfo x:list){
      if(x.activityInfo==null)continue;
      String pkg=x.activityInfo.packageName;
      if(pkg.equals(getPackageName())||pkg.contains(".settings")||pkg.contains("tvlauncher")||pkg.equals("com.google.android.apps.tv.launcherx"))continue;
      try{Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        i.setComponent(new ComponentName(pkg,x.activityInfo.name));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(i);return;
      }catch(Exception ignored){}
    }
    try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}
    catch(Exception ex){try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception ignored){}}
  });
 }
 private static String getString(String url) throws Exception{
  HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();
  try{
    c.setConnectTimeout(12000);c.setReadTimeout(14000);c.setRequestProperty("User-Agent","O31LanguedocTV/3 (https://o31languedoc.com)");
    if(c.getResponseCode()!=200)throw new java.io.IOException("HTTP "+c.getResponseCode());
    InputStream in=c.getInputStream();java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
    byte[] block=new byte[8192];int n;while((n=in.read(block))!=-1){buf.write(block,0,n);if(buf.size()>900000)throw new java.io.IOException("Large data");}
    in.close();return buf.toString("UTF-8");
  }finally{c.disconnect();}
 }
 public final class Bridge{
  @JavascriptInterface public String listApps(){
    JSONArray ar=new JSONArray();PackageManager pm=getPackageManager();
    for(Map.Entry<String,ResolveInfo> e:appList().entrySet()){
      try{
       JSONObject o=new JSONObject();o.put("label",String.valueOf(e.getValue().loadLabel(pm)));o.put("packageName",e.getKey());
       try{Drawable d=e.getValue().loadIcon(pm);
         if(d!=null){Bitmap b=Bitmap.createBitmap(72,72,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
           d.setBounds(0,0,72,72);d.draw(c);java.io.ByteArrayOutputStream s=new java.io.ByteArrayOutputStream();
           b.compress(Bitmap.CompressFormat.PNG,85,s);
           o.put("icon","data:image/png;base64,"+Base64.encodeToString(s.toByteArray(),Base64.NO_WRAP));s.close();b.recycle();}
       }catch(Exception ignored){}
       ar.put(o);
      }catch(Exception ignored){}
    }return ar.toString();
  }
  @JavascriptInterface public void openApp(String pkg){if(pkg==null||pkg.length()>160)return;runOnUiThread(()->launchPackage(pkg));}
  @JavascriptInterface public boolean wasExternalApp(){boolean had=externalApp;externalApp=false;return had;}
  @JavascriptInterface public void openLink(String url){
    if(url==null||!url.startsWith("https://")||url.length()>1000)return;
    launchActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));
  }
  @JavascriptInterface public boolean pinExists(){return state.contains("pin_hash");}
  @JavascriptInterface public boolean setPin(String p){
    if(!validPin(p))return false;
    if(pinExists()&&SystemClock.elapsedRealtime()>adminUntil)return false;
    byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);
    state.edit().putString("pin_salt",Base64.encodeToString(salt,Base64.NO_WRAP))
      .putString("pin_hash",hex(sha(p,salt))).apply();
    adminUntil=SystemClock.elapsedRealtime()+120000;failedPins=0;return true;
  }
  @JavascriptInterface public boolean verifyPin(String p){
    if(!validPin(p)||!pinExists())return false;
    if(SystemClock.elapsedRealtime()<lockedUntil)return false;
    try{
      byte[] salt=Base64.decode(state.getString("pin_salt",""),Base64.DEFAULT);
      byte[] expected=hex(sha(p,salt)).getBytes(StandardCharsets.UTF_8);
      boolean match=MessageDigest.isEqual(expected,state.getString("pin_hash","").getBytes(StandardCharsets.UTF_8));
      if(match){failedPins=0;adminUntil=SystemClock.elapsedRealtime()+120000;return true;}
    }catch(Exception ignored){}
    failedPins++;if(failedPins>=8){lockedUntil=SystemClock.elapsedRealtime()+60000;failedPins=0;}return false;
  }
  @JavascriptInterface public void adminAction(String action){
    if(SystemClock.elapsedRealtime()>adminUntil)return;
    if("wifi".equals(action))launchActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
    else if("system".equals(action))launchActivity(new Intent(Settings.ACTION_SETTINGS));
    else if("home".equals(action))goToSystemHome();
  }
  @JavascriptInterface public String getCache(String type){
    if(!("agenda".equals(type)||"weather".equals(type)))return "";
    return state.getString("cache_"+type,"");
  }
  @JavascriptInterface public void fetch(String type){
    if(!("agenda".equals(type)||"weather".equals(type)))return;
    String url="weather".equals(type)?"https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=43.6047&lon=1.4442"
      :"https://data.toulouse-metropole.fr/api/explore/v2.1/catalog/datasets/agenda-des-manifestations-culturelles-so-toulouse/records?limit=60";
    new Thread(()->{
      try{
        String content=getString(url);new JSONObject(content);
        long stamp=System.currentTimeMillis();JSONObject cache=new JSONObject();
        cache.put("data",new JSONObject(content));cache.put("stamp",stamp);
        state.edit().putString("cache_"+type,cache.toString()).apply();
        String cmd="window.O31_onRemote&&window.O31_onRemote("+JSONObject.quote(type)+","+JSONObject.quote(content)+","+stamp+")";
        runOnUiThread(()->{if(web!=null)web.evaluateJavascript(cmd,null);});
      }catch(Exception ignored){}
    },"O31_"+type).start();
  }
 }
}