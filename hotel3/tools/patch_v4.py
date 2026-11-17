#!/usr/bin/env python3
"""V4 build patch: official YouTube embed, compact lower navigation and stable HTTPS WebView origin."""
from pathlib import Path
base=Path("hotel3/app/src/main")
html_path=base/"assets/index.html"
css_path=base/"assets/style.css"
js_path=base/"assets/app.js"
java_path=base/"java/com/o31languedoc/hotel/MainActivity.java"
gradle_path=Path("hotel3/app/build.gradle")

def must_replace(s,old,new,label):
    if old not in s:
        raise RuntimeError("V4 patch source mismatch: "+label)
    return s.replace(old,new,1)

html=html_path.read_text()
html=must_replace(html,'<main id="home" class="page"><section class="welcome">',
'<main id="home" class="page"><section class="hero-row"><div class="welcome">','HTML hero start')
html=must_replace(html,'</section>\n<div class="menu" id="menu">',
'''</div><div class="video-feature" id="videoFeature">
<div class="youtube-player-frame" id="youtubeBox">
  <iframe id="youtubeEmbed" title="Around the Brick: Toulouse Hyperlapse — HyperlapsePro" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen referrerpolicy="strict-origin-when-cross-origin" tabindex="-1"></iframe>
  <div class="youtube-offline" id="youtubeOffline"><img src="media/toulouse.webp" alt="Toulouse"><span>Film YouTube : connexion nécessaire</span></div>
</div>
<div class="video-credit"><span>Film : <strong>Around the Brick · Toulouse</strong> · HyperlapsePro / YouTube</span><button class="video-open" id="openYoutube">▶ Voir sur YouTube</button></div>
</div></section>
<div class="menu" id="menu">''','HTML hero end')
html_path.write_text(html)

css=css_path.read_text()
css+="""
/* Ô31 V4 — official YouTube player, separate from menus (no obscured credits). */
#home .hero-row{height:65%;width:100%;display:flex;gap:2.2vw;align-items:center;padding:.5vh 1vw 1.2vh}
#home .hero-row .welcome{height:100%;width:37%;max-height:none;min-width:0;flex:0 0 37%;padding:0 .1vw;justify-content:center}
#home .hero-row h1{max-width:100%;font-size:clamp(30px,2.75vw,58px);line-height:1.13}
#home .hero-row .subtitle{font-size:1.14vw;max-width:100%}
#home .hero-row .eyebrow{font-size:.76vw;letter-spacing:.24em}
#home .video-feature{width:61%;height:100%;min-width:0;display:flex;flex-direction:column;justify-content:center;padding:1vh .6vw 0}
#home .youtube-player-frame{position:relative;width:100%;height:calc(100% - 4.5vh);min-height:200px;background:#0d1f33;border-radius:17px;border:1px solid #f0cc8b88;box-shadow:0 10px 36px #000a;overflow:hidden}
#home .youtube-player-frame iframe{display:block;position:relative;width:100%;height:100%;border:0;z-index:2;background:transparent}
#home .youtube-offline{position:absolute;inset:0;display:flex;align-items:flex-end;justify-content:center;z-index:1;background:#092238}
#home .youtube-offline img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;filter:brightness(.7)}
#home .youtube-offline span{position:relative;padding:1.3vw;background:#091928b9;font-size:.95vw;text-align:center}
#home .video-credit{height:4.5vh;display:flex;align-items:center;justify-content:space-between;gap:1vw;color:#f4dec0;font-size:.72vw;letter-spacing:.02em}
#home .video-credit strong{font-weight:600}
#home .video-open{background:#1b3551c9;color:#fce2b9;border:1px solid #ecc88c9e;border-radius:12px;padding:.5vw .85vw;font-size:.8vw;white-space:nowrap}
#home .menu{height:25%}
@media(max-height:680px){#home .hero-row{height:65%;padding:.2vh .6vw 0}#home .youtube-player-frame{min-height:170px}#home .video-credit{font-size:.64vw}#home .hero-row h1{font-size:2.65vw}}
"""
css_path.write_text(css)

js=js_path.read_text()
add="""const youtubeVideoId='TBCqQmnsNFY';
const youtubeUrl='https://www.youtube.com/watch?v='+youtubeVideoId;
const youtubeOrigin='https://appassets.androidplatform.net';
const youtubeEmbedUrl='https://www.youtube-nocookie.com/embed/'+youtubeVideoId+'?autoplay=1&mute=1&controls=1&loop=1&playlist='+youtubeVideoId+'&playsinline=1&enablejsapi=1&origin='+encodeURIComponent(youtubeOrigin);
function startYoutube(){
 const f=$('youtubeEmbed');if(!f||page||document.hidden||!$('screenSaver').classList.contains('hidden'))return;
 if(navigator.onLine===false){stopYoutube();return;}
 f.style.display='block';
 if(f.getAttribute('src')!==youtubeEmbedUrl)f.setAttribute('src',youtubeEmbedUrl);
}
function stopYoutube(){const f=$('youtubeEmbed');if(f){f.removeAttribute('src');f.style.display='none';}}
window.stopYoutube=stopYoutube;
"""
js=must_replace(js,'function showHome()',add+'\nfunction showHome()','JS youtube')
js=must_replace(js,"document.querySelector('.menu-tile').focus();syncAudio()}","document.querySelector('.menu-tile').focus();startYoutube();syncAudio()}","JS return home")
js=must_replace(js,"page=id;document.body.classList.add('in-section');","page=id;stopYoutube();document.body.classList.add('in-section');","JS open section")
js=must_replace(js,"$('heroMovie').pause();syncAudio();let ix=0;","$('heroMovie').pause();stopYoutube();syncAudio();let ix=0;","JS saver")
js=must_replace(js,"if(!page)$('heroMovie').play().catch(()=>{});syncAudio();}","if(!page){$('heroMovie').play().catch(()=>{});startYoutube();}syncAudio();}","JS stop saver")
js=must_replace(js,"if(document.hidden){$('heroMovie').pause();syncAudio()}else{if(!page)$('heroMovie').play().catch(()=>{});requestRemote()}","if(document.hidden){$('heroMovie').pause();stopYoutube();syncAudio()}else{if(!page){$('heroMovie').play().catch(()=>{});startYoutube();}requestRemote()}","JS visibility")
js=must_replace(js,"window.O31_onResume=()=>{if(window.O31&&O31.wasExternalApp()){page='';$('modal').classList.add('hidden');showHome()}syncAudio();requestRemote()};","window.O31_onResume=()=>{if(window.O31&&O31.wasExternalApp()){page='';$('modal').classList.add('hidden');showHome()}if(!page)startYoutube();syncAudio();requestRemote()};","JS external app")
js=must_replace(js,"musicBtn.onclick=()=>{musicOn=!musicOn;savePref('music',musicOn);musicBtn.textContent=musicOn?'♫ ON':'♫ OFF';syncAudio();};getCached();","""musicBtn.onclick=()=>{musicOn=!musicOn;savePref('music',musicOn);musicBtn.textContent=musicOn?'♫ ON':'♫ OFF';syncAudio();};
$('openYoutube').onclick=()=>{if(window.O31)O31.openLink(youtubeUrl);else location.assign(youtubeUrl);};
window.addEventListener('online',()=>{if(!page)startYoutube();});
window.addEventListener('offline',stopYoutube);
getCached();""","JS YouTube button")
js=must_replace(js,"document.querySelector('.menu-tile').focus();resetSavers();syncAudio();","document.querySelector('.menu-tile').focus();resetSavers();startYoutube();syncAudio();","JS initial")
js_path.write_text(js)

java=java_path.read_text()
java=must_replace(java,'import android.webkit.WebResourceRequest;','import android.webkit.WebResourceRequest;\nimport android.webkit.WebResourceResponse;\nimport androidx.webkit.WebViewAssetLoader;','Java import')
java=must_replace(java,'private WebView web;','private WebView web;\n private WebViewAssetLoader assetLoader;','Java field')
java=must_replace(java,"""  web.setWebViewClient(new WebViewClient(){
    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
      return !req.getUrl().toString().startsWith("file:///android_asset/");
    }
  });""","""  assetLoader=new WebViewAssetLoader.Builder()
    .addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this))
    .build();
  web.setWebViewClient(new WebViewClient(){
    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest req){
      return assetLoader.shouldInterceptRequest(req.getUrl());
    }
    @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req){
      if(!req.isForMainFrame())return false;
      return !req.getUrl().toString().startsWith("https://appassets.androidplatform.net/assets/");
    }
  });""","Java client")
java=must_replace(java,'web.loadUrl("file:///android_asset/index.html");','web.loadUrl("https://appassets.androidplatform.net/assets/index.html");','Java HTTPS local')
java=must_replace(java,"document.getElementById('heroMovie')?.pause();document.getElementById('ambientAudio')?.pause();","document.getElementById('heroMovie')?.pause();document.getElementById('ambientAudio')?.pause();window.stopYoutube&&window.stopYoutube();","Java pause Youtube")
java_path.write_text(java)

gradle=gradle_path.read_text()
gradle=must_replace(gradle,"applicationId 'com.o31languedoc.hotel'","applicationId 'com.o31languedoc.hotel.v4'","V4 side-by-side installation ID")
gradle=must_replace(gradle,'versionCode 3','versionCode 4','version code')
gradle=must_replace(gradle,"versionName '3.0-hotel'","versionName '4.0-youtube'","version name")
gradle+="\ndependencies { implementation 'androidx.webkit:webkit:1.11.0' }\n"
gradle_path.write_text(gradle)
manifest_path=base/"AndroidManifest.xml"
manifest=manifest_path.read_text()
manifest=must_replace(manifest,'android:label="Ô31 Languedoc Hôtel"','android:label="Ô31 Languedoc Hôtel V4"','V4 app label')
manifest=must_replace(manifest,'android:name=".MainActivity"','android:name="com.o31languedoc.hotel.MainActivity"','V4 resolved activity')
manifest_path.write_text(manifest)

assert "TBCqQmnsNFY" in js and "youtubeEmbed" in html and "WebViewAssetLoader" in java
print("V4 patch complete: official YouTube player + original credits, cards lower, offline film retained")
