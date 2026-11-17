/* Ô31 Languedoc TV — V3 immersive layer. All navigation works offline except live services. */
(function(){
'use strict';
var $=function(s){return document.querySelector(s)}, $$=function(s){return Array.prototype.slice.call(document.querySelectorAll(s))};
var activeLanguage='fr', currentOverlay='', soundEnabled=true, saverTimer=null,slideTimer=null,slideIndex=0,weatherData=null,agendaData=null,cacheTime='',stateId='',allowedAdmin=false,media=['salon.webp','cuisine.webp','chambre.webp','repas.webp'];
var L={
fr:{extras:'Agenda Toulouse',agendaIntro:'Concerts, spectacles et événements prochainement à Toulouse.',weather:'Météo',weatherWait:'Chargement météo…',weatherOffline:'Données météo indisponibles. Connexion nécessaire.',agendaWait:'Recherche des prochains événements…',agendaOffline:'Agenda indisponible. Consultez le programme officiel de Toulouse.',update:'Dernière mise à jour',back:'Retour',apps:'Applications installées',admin:'Réglages · Propriétaire',adminHint:'Code PIN propriétaire à 6 chiffres',pinSetup:'Définir le PIN administrateur',pinOpen:'Déverrouiller les réglages',wrongPin:'Code invalide',wifi:'Paramètres Wi-Fi',xiaomi:'Quitter vers Xiaomi',sound:'Ambiance musicale',stayGallery:'Visite de l’appartement',heritage:'Toulouse historique',food:'Gastronomie',family:'En famille',walks:'Balades',tourIntro:'Découvrez Toulouse selon vos envies.',photo:['Le salon','La cuisine','La chambre','L’espace repas'],day:'Aujourd’hui',open:'Voir le programme officiel',steps:'Tutoriel animé · étape',next:'Étape suivante',previous:'Étape précédente'},
en:{extras:'Toulouse events',agendaIntro:'Upcoming concerts, shows and local events in Toulouse.',weather:'Weather',weatherWait:'Loading weather…',weatherOffline:'Weather data unavailable. Internet is required.',agendaWait:'Looking for upcoming events…',agendaOffline:'Events unavailable. Visit the official Toulouse calendar.',update:'Last updated',back:'Back',apps:'Installed apps',admin:'Settings · Owner',adminHint:'Six-digit owner PIN',pinSetup:'Create your administrator PIN',pinOpen:'Unlock settings',wrongPin:'Invalid PIN',wifi:'Wi-Fi settings',xiaomi:'Exit to Xiaomi',sound:'Ambient music',stayGallery:'Apartment tour',heritage:'Historic Toulouse',food:'Food and markets',family:'Family activities',walks:'Scenic walks',tourIntro:'Explore Toulouse by interest.',photo:['Living room','Kitchen','Bedroom','Dining area'],day:'Today',open:'Official event calendar',steps:'Animated guide · step',next:'Next step',previous:'Previous step'},
es:{extras:'Agenda Toulouse',agendaIntro:'Próximos conciertos, espectáculos y eventos en Toulouse.',weather:'El tiempo',weatherWait:'Cargando el tiempo…',weatherOffline:'El tiempo no está disponible sin Internet.',agendaWait:'Buscando próximos eventos…',agendaOffline:'Agenda no disponible. Consulte el calendario oficial.',update:'Última actualización',back:'Volver',apps:'Aplicaciones instaladas',admin:'Ajustes · Propietario',adminHint:'PIN de seis dígitos',pinSetup:'Crear PIN de administrador',pinOpen:'Desbloquear ajustes',wrongPin:'Código incorrecto',wifi:'Configuración Wi-Fi',xiaomi:'Salir al Xiaomi',sound:'Música ambiental',stayGallery:'Visita del apartamento',heritage:'Toulouse histórico',food:'Gastronomía',family:'En familia',walks:'Paseos',tourIntro:'Descubra Toulouse por temáticas.',photo:['Salón','Cocina','Dormitorio','Comedor'],day:'Hoy',open:'Agenda oficial',steps:'Guía animada · paso',next:'Paso siguiente',previous:'Paso anterior'}
};
function t(k){return L[activeLanguage][k]||k}
function clean(s){return String(s||'').replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]})}
function make(tag,cls,textContent){var el=document.createElement(tag);if(cls)el.className=cls;if(textContent!==undefined)el.textContent=textContent;return el}
function refreshLanguage(){activeLanguage=($('.language.active')||{}).dataset?.lang||'fr';document.documentElement.lang=activeLanguage;var title=$('[data-title="extras"]'),desc=$('[data-desc="extras"]');if(title)title.textContent=t('extras');if(desc)desc.textContent=t('agendaIntro');var welcome=$('#welcome');if(welcome){var hour=new Date().getHours();welcome.textContent=activeLanguage==='fr'?(hour>=18||hour<6?'Bonsoir et bienvenue à':'Bonjour et bienvenue à'):activeLanguage==='en'?(hour>=18||hour<6?'Good evening, welcome to':'Good morning, welcome to'):(hour>=18||hour<6?'Buenas noches, bienvenido a':'Buenos días, bienvenido a')};var w=$('#weatherButton');if(w)w.setAttribute('aria-label',t('weather'));var s=$('#toggleMusic');if(s)s.textContent='♫ '+t('sound')+' '+(soundEnabled?'ON':'OFF')}
function pauseFilm(){document.body.classList.add('in-section');var v=$('#video');if(v)v.pause()}
function playFilm(){document.body.classList.remove('in-section');var v=$('#video');if(v)v.play().catch(function(){})}
function resetOnClose(){if(!$('#panel').classList.contains('open')&&!$('#detail').classList.contains('open')&&!currentOverlay)playFilm()}
function openOverlay(id,title){hideOverlay();currentOverlay=id;pauseFilm();var el=make('section','o31-overlay open');el.id='o31extra';el.setAttribute('role','dialog');el.innerHTML='<button class="close" id="premiumBack">← '+clean(t('back'))+'</button><h2>'+clean(title)+'</h2><div id="premiumBody"></div>';$('#app').appendChild(el);$('#premiumBack').onclick=hideOverlay;$('#premiumBack').focus();return $('#premiumBody')}
function hideOverlay(){var el=$('#o31extra');if(el)el.remove();currentOverlay='';clearInterval(slideTimer);if(!$('#panel').classList.contains('open')&&!$('#detail').classList.contains('open'))playFilm()}
function closeAll(){hideOverlay();if($('#detail').classList.contains('open'))$('#detailreturn').click();if($('#panel').classList.contains('open'))$('#return').click();resetOnClose()}
function stylePanel(){var open=$('#panel').classList.contains('open')||$('#detail').classList.contains('open');if(open)pauseFilm();else resetOnClose()}
function focusSafe(el){if(el&&el.focus)el.focus()}
function makeButton(name,info,click){var b=make('button','choice');b.innerHTML='<span class="circle">✧</span><span><strong>'+clean(name)+'</strong><small>'+clean(info||'')+'</small></span><span class="arrow">›</span>';b.onclick=click;return b}
function adaptApps(){
 if(!$('#panel').classList.contains('open')||$('#heading').textContent.indexOf('application')===-1&&$('#heading').textContent.indexOf('Application')===-1&&$('#heading').textContent.indexOf('aplicacion')===-1&&$('#heading').textContent.indexOf('Aplicacion')===-1)return;
 var grid=$('#grid');grid.classList.add('apps-carousel');grid.classList.remove('themes');
 if(!grid.querySelector('[data-o31-admin]')){var admin=makeButton(t('admin'),t('adminHint'),openAdmin);admin.dataset.o31Admin='1';admin.querySelector('.circle').textContent='⚙';grid.appendChild(admin)}
}
var cityButtons=null;
function adaptCity(){
 if(!$('#panel').classList.contains('open'))return;
 var heading=$('#heading').textContent;
 if(!/Toulouse/i.test(heading)||/Agenda|events/i.test(heading)||$('#grid').classList.contains('themed-sub'))return;
 if($('#grid').classList.contains('themes'))return;
 cityButtons=$$('#grid .choice');if(cityButtons.length<6)return;
 $('#intro').textContent=t('tourIntro');var grid=$('#grid');grid.replaceChildren();grid.classList.add('themes');grid.classList.remove('apps-carousel');
 var types=[['heritage',[1,3,4],'♜'],['food',[0],'♨'],['family',[2,6],'✦'],['walks',[5,2],'☀']];
 types.forEach(function(z){var btn=makeButton(t(z[0]),'',function(){grid.replaceChildren();grid.classList.remove('themes');grid.classList.add('themed-sub');var back=makeButton('← '+t('back'),t('tourIntro'),function(){grid.classList.remove('themed-sub');grid.classList.remove('themes');var tile=$('.tile[data-go="city"]');if(tile){tile.click();setTimeout(adaptCity,0)}});grid.appendChild(back);z[1].forEach(function(index){var item=cityButtons[index];if(item)grid.appendChild(item)});focusSafe(back)});btn.querySelector('.circle').textContent=z[2];grid.appendChild(btn)})
}
function addGallery(){
 if(!$('#panel').classList.contains('open')||!/(séjour|stay|estancia)/i.test($('#heading').textContent))return;
 var grid=$('#grid');grid.classList.remove('apps-carousel','themes','themed-sub');
 if(grid.querySelector('[data-gallery]'))return;
 var button=makeButton(t('stayGallery'),'Ô31 Languedoc',openGallery);button.dataset.gallery='1';button.querySelector('.circle').textContent='▣';grid.appendChild(button);
}
function openGallery(){
 var body=openOverlay('gallery',t('stayGallery'));
 body.innerHTML='<img id="galleryPhoto" alt=""><p id="galleryCaption"></p><div id="adminActions"><button id="galleryPrev">← '+clean(t('back'))+'</button><button id="galleryNext">'+clean(t('next'))+' →</button></div>';
 function update(){var img=$('#galleryPhoto');if(!img)return;img.src='media/'+media[slideIndex];img.alt=t('photo')[slideIndex];$('#galleryCaption').textContent=(slideIndex+1)+' / '+media.length+' · '+t('photo')[slideIndex]}
 $('#galleryPrev').onclick=function(){slideIndex=(slideIndex+media.length-1)%media.length;update()};
 $('#galleryNext').onclick=function(){slideIndex=(slideIndex+1)%media.length;update()};
 slideIndex=0;update();focusSafe($('#galleryNext'));
 slideTimer=setInterval(function(){if(currentOverlay==='gallery'){slideIndex=(slideIndex+1)%media.length;update()}},6500)
}
function openAdmin(){
 var body=openOverlay('admin',t('admin'));
 var configured=false;try{configured=window.Tv&&Tv.pinConfigured()}catch(e){}
 allowedAdmin=false;body.innerHTML='<p>'+clean(configured?t('pinOpen'):t('pinSetup'))+' — '+clean(t('adminHint'))+'</p><input id="adminPin" type="password" inputmode="numeric" pattern="[0-9]*" maxlength="6" autocomplete="off" aria-label="PIN"><div id="adminActions"><button id="pinSubmit">'+clean(configured?t('pinOpen'):t('pinSetup'))+'</button></div><p id="pinMessage"></p>';
 function tryPin(){var pin=$('#adminPin').value;if(!/^[0-9]{6}$/.test(pin)){$('#pinMessage').textContent=t('adminHint');return}
  var ok=false;try{ok=window.Tv?(configured?Tv.verifyPin(pin):Tv.setPin(pin)):false}catch(e){}
  $('#adminPin').value='';if(!ok){$('#pinMessage').textContent=t('wrongPin');return}
  allowedAdmin=true;$('#pinMessage').textContent='✓';var acts=$('#adminActions');acts.replaceChildren();
  [[t('wifi'),function(){try{Tv.openWiFi()}catch(e){}}],[t('xiaomi'),function(){try{Tv.exitToXiaomi()}catch(e){}}]].forEach(function(pair){var b=make('button','',pair[0]);b.onclick=pair[1];acts.appendChild(b)});focusSafe(acts.querySelector('button'))
 }
 $('#pinSubmit').onclick=tryPin;$('#adminPin').addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();e.stopImmediatePropagation();tryPin()}});focusSafe($('#adminPin'));
}
function openWeather(){
 var body=openOverlay('weather',t('weather'));
 var c=$('#weatherButton');var cached=null;try{cached=JSON.parse(localStorage.getItem('o31-weather')||'null')}catch(e){}
 weatherData=weatherData||cached?.data||null;
 body.innerHTML='<p id="weatherStamp">'+clean(t('weatherWait'))+'</p><div id="weatherContent" class="collection"></div>';
 function render(){if(!currentOverlay||currentOverlay!=='weather')return;var d=weatherData;if(!d||!d.daily){$('#weatherStamp').textContent=t('weatherOffline');return}
  var dates=d.daily.time||[],high=d.daily.temperature_2m_max||[],low=d.daily.temperature_2m_min||[],codes=d.daily.weather_code||[];
  var stamp='';try{stamp=JSON.parse(localStorage.getItem('o31-weather')||'null').at||''}catch(e){}
  $('#weatherStamp').textContent=stamp?t('update')+' : '+new Date(stamp).toLocaleString(activeLanguage):'';
  $('#weatherContent').innerHTML='';dates.slice(0,3).forEach(function(s,i){var date=new Date(s+'T12:00:00'),emoji=iconWeather(codes[i]),el=make('div','weatherDay');
  el.innerHTML='<strong>'+clean(date.toLocaleDateString(activeLanguage,{weekday:'long'}))+'</strong><p>'+emoji+' '+clean(Math.round(low[i]))+'° / '+clean(Math.round(high[i]))+'°C</p>';
  $('#weatherContent').appendChild(el)})
 }
 window.o31RenderWeather=render;render();
}
function iconWeather(code){return code===0?'☀️':[1,2].indexOf(code)>=0?'🌤️':code===3?'☁️':[51,53,55,61,63,65,80,81,82].indexOf(code)>=0?'🌧️':[71,73,75].indexOf(code)>=0?'❄️':'🌥️'}
window.o31WeatherData=function(json){try{var obj=JSON.parse(json);if(!obj.daily)throw Error('Bad weather');weatherData=obj;localStorage.setItem('o31-weather',JSON.stringify({at:new Date().toISOString(),data:obj}));if($('#weatherButton')&&obj.current){$('#weatherButton').textContent=iconWeather(obj.current.weather_code)+' '+Math.round(obj.current.temperature_2m)+'°C'}if(window.o31RenderWeather)window.o31RenderWeather()}catch(e){/* keep cached values */}};
function fetchWeather(){try{if(window.Tv)Tv.requestWeather()}catch(e){}}
function getCachedAgenda(){try{return JSON.parse(localStorage.getItem('o31-agenda')||'null')}catch(e){return null}}
function normalizeKey(s){return String(s).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f_\- ]/g,'')}
function getField(rec,variants){var entries=Object.keys(rec||{});for(var i=0;i<variants.length;i++){var n=normalizeKey(variants[i]);for(var j=0;j<entries.length;j++){if(normalizeKey(entries[j])===n)return rec[entries[j]]}}return ''}
function agendaEvents(input){var rows=(input?.results||input?.records||[]),today=new Date();today.setHours(0,0,0,0);return rows.map(function(x){var name=getField(x,['nom_de_la_manifestation','nom_manifestation','nom_de_la_manifestation_fr','nom','title','titre','nomgenerique']);var date=getField(x,['date_debut','date_debut_manifestation','datedebut','firstdate_start','start_date','date_fin','datefin']);var end=getField(x,['date_fin','datefin','end_date'])||date;
 var where=getField(x,['lieu_nom','lieu','lieunom','nom_du_lieu','ville','commune']);if(typeof date==='object')date='';if(typeof end==='object')end='';var parsed=new Date(String(date).slice(0,10)),last=new Date(String(end).slice(0,10));return {name:name,date:String(date),where:where,parsed:parsed,last:last};
 }).filter(function(x){return x.name&&x.date&&!isNaN(x.parsed)&&x.last>=today}).sort(function(a,b){return a.parsed-b.parsed}).slice(0,15)}
window.o31AgendaData=function(json){try{var obj=JSON.parse(json);if(!Array.isArray(obj.results)&&!Array.isArray(obj.records))throw Error('Bad agenda');agendaData=obj;localStorage.setItem('o31-agenda',JSON.stringify({at:new Date().toISOString(),data:obj}));if(window.o31RenderAgenda)window.o31RenderAgenda()}catch(e){}}
function loadAgenda(){try{if(window.Tv)Tv.requestAgenda()}catch(e){}}
function showAgenda(){
 var grid=$('#grid');grid.replaceChildren();grid.classList.remove('apps-carousel','themes','themed-sub');$('#heading').textContent=t('extras');$('#intro').textContent=t('agendaIntro');
 var cached=getCachedAgenda();agendaData=agendaData||cached?.data||null;
 function render(){if(!$('#panel').classList.contains('open')||$('#heading').textContent!==t('extras'))return;
  grid.replaceChildren();var rows=agendaData?agendaEvents(agendaData):[];
  var stamp=cached?.at||'';if(rows.length){rows.forEach(function(e){var date=new Date(e.date.slice(0,10));grid.appendChild(makeButton(e.name,date.toLocaleDateString(activeLanguage)+' · '+e.where,'function'===typeof Tv?.openAgenda?function(){Tv.openAgenda()}:function(){try{Tv.openOfficialAgenda()}catch(er){}}))})}
  else{var p=make('div','',t('agendaOffline'));p.style.cssText='padding:1.7vw;color:#e0e7ed;grid-column:span 4';grid.appendChild(p)}
  var go=makeButton(t('open'),stamp?t('update')+' : '+new Date(stamp).toLocaleDateString(activeLanguage):'Toulouse Métropole',function(){try{Tv.openOfficialAgenda()}catch(e){}});grid.appendChild(go);
 }
 window.o31RenderAgenda=render;render();loadAgenda()
}
function enhancedGuide(){
 if(!$('#detail').classList.contains('open')||!$('#steps .step'))return;
 if($('#guideControls'))return;
 var steps=$$('#steps .step');if(steps.length<2)return;var selected=0,controls=make('div','');controls.id='guideControls';controls.style.cssText='display:flex;gap:1vw;align-items:center;flex-wrap:wrap;margin:1vw 0 2vw';
 var prev=make('button','', '← '+t('previous')),next=make('button','',t('next')+' →'),tag=make('span','',t('steps')+' 1 / '+steps.length);
 [prev,next].forEach(function(b){b.style.cssText='padding:.8vw 1.2vw;border:1px solid #e8c58f;background:#213a53;border-radius:12px'});
 controls.append(prev,tag,next);$('#steps').before(controls);
 function update(){steps.forEach(function(step,i){step.classList.toggle('animated',i===selected);step.style.opacity=i===selected?'1':'.55';step.style.background=i===selected?'#1e3b5270':'transparent'});tag.textContent=t('steps')+' '+(selected+1)+' / '+steps.length;steps[selected].scrollIntoView({block:'nearest'})}
 prev.onclick=function(){selected=(selected+steps.length-1)%steps.length;update()};next.onclick=function(){selected=(selected+1)%steps.length;update()};update()
}
function handleMenu(){
 var panel=$('#panel'),grid=$('#grid');if(!panel.classList.contains('open')){grid.classList.remove('apps-carousel','themes','themed-sub');cityButtons=null;return}
 var head=$('#heading').textContent;
 if(/^(Applications TV|TV applications|Aplicaciones de TV)/i.test(head)){adaptApps();return}
 if(/^(Visiter Toulouse|Explore Toulouse|Visitar Toulouse)/i.test(head)){adaptCity();return}
 if(/^(Votre séjour|Your stay|Su estancia)/i.test(head)){addGallery();return}
 if(/^(À découvrir|Discover more|Descubrir|Agenda Toulouse|Toulouse events)/i.test(head)){showAgenda();return}
 grid.classList.remove('apps-carousel','themes','themed-sub');
}
function createClockAndWeather(){
 var button=make('button','');button.id='weatherButton';button.textContent='☁ · °C';button.onclick=openWeather;
 var right=$('.headright');right.insertBefore(button,$('#clock'));
 var music=make('button','');music.id='toggleMusic';music.style.cssText='border:1px solid #e6c98b74;border-radius:22px;padding:.4vw .6vw;background:#102f44;font-size:.78vw';
 music.onclick=function(){soundEnabled=!soundEnabled;localStorage.setItem('o31-music',soundEnabled?'1':'0');updateMusic();refreshLanguage()};right.insertBefore(music,button);
 try{soundEnabled=localStorage.getItem('o31-music')!=='0'}catch(e){}
 var audio=make('audio');audio.id='o31Audio';audio.loop=true;audio.preload='auto';audio.src='media/ambience.wav';audio.volume=.10;document.body.appendChild(audio);
 refreshLanguage();updateMusic();fetchWeather()
}
function updateMusic(){var audio=$('#o31Audio');if(!audio)return;if(soundEnabled&&document.visibilityState==='visible'){audio.play().catch(function(){})}else audio.pause()}
function initSaver(){
 var el=make('div','');el.id='screenSaver';el.innerHTML='<div class="screenBg"></div><div class="screenText"><div id="saverClock"></div><small>Ô31 Languedoc · Toulouse <span id="saverWeather"></span></small></div>';
 $('#app').appendChild(el);var saverImages=['toulouse.webp','salon.webp','cuisine.webp','chambre.webp'],index=0;
 function update(){if(!$('#screenSaver').classList.contains('open'))return;el.querySelector('.screenBg').style.backgroundImage='url(media/'+saverImages[index++%saverImages.length]+')';$('#saverClock').textContent=new Date().toLocaleTimeString(activeLanguage,{hour:'2-digit',minute:'2-digit'});$('#saverWeather').textContent=weatherData?.current?Math.round(weatherData.current.temperature_2m)+'°C':''}
 function activate(){if(currentOverlay||$('#panel').classList.contains('open')||$('#detail').classList.contains('open'))return;el.classList.add('open');pauseFilm();update();slideTimer=setInterval(update,9000)}
 function dismiss(){if(el.classList.contains('open')){el.classList.remove('open');clearInterval(slideTimer);slideTimer=null;resetOnClose();$('.tile').focus();return true}return false}
 function reset(){clearTimeout(saverTimer);saverTimer=setTimeout(activate,300000)}
 document.addEventListener('keydown',function(e){if(dismiss()){e.stopImmediatePropagation();e.preventDefault();reset();return}
 if(currentOverlay){if(['Escape','Backspace'].indexOf(e.key)>=0){e.preventDefault();e.stopImmediatePropagation();hideOverlay();$('#return').focus();reset();return}
  if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown'].indexOf(e.key)>=0){var buttons=$$('#o31extra button, #o31extra input').filter(function(x){return x.offsetWidth>0}),cur=buttons.indexOf(document.activeElement);if(cur<0)cur=0;var diff=e.key==='ArrowLeft'||e.key==='ArrowUp'?-1:1;if(buttons.length){buttons[(cur+diff+buttons.length)%buttons.length].focus()}e.preventDefault();e.stopImmediatePropagation();reset();return}
  if(e.key==='Enter'&&document.activeElement&&document.activeElement.tagName==='BUTTON'){document.activeElement.click();e.stopImmediatePropagation();e.preventDefault();reset();return}
 }
 reset();},true);
 document.addEventListener('mousemove',reset,{passive:true});reset()
}
function init(){
 var origReturn=$('#return').onclick,origDetailReturn=$('#detailreturn').onclick;
 $('#return').onclick=function(){origReturn();cityButtons=null;window.o31RenderAgenda=null;setTimeout(resetOnClose,0)};
 $('#detailreturn').onclick=function(){origDetailReturn();setTimeout(stylePanel,0)};
 $$('.tile, #primary').forEach(function(el){el.addEventListener('click',function(){setTimeout(function(){refreshLanguage();handleMenu();stylePanel()},0)})});
 $$('.language').forEach(function(el){el.addEventListener('click',function(){setTimeout(function(){refreshLanguage();handleMenu();if(currentOverlay){hideOverlay()}},0)})});
 var watch=new MutationObserver(function(){stylePanel();if($('#detail').classList.contains('open'))enhancedGuide()});
 watch.observe($('#panel'),{attributes:true,attributeFilter:['class']});watch.observe($('#detail'),{attributes:true,attributeFilter:['class']});
 createClockAndWeather();initSaver();refreshLanguage();setInterval(refreshLanguage,60000);setInterval(function(){if(document.visibilityState==='visible')fetchWeather()},3600000);
 document.addEventListener('visibilitychange',function(){updateMusic();if(document.visibilityState==='visible'){refreshLanguage();fetchWeather()}});
 // Launching a third-party TV app never plays background music.
 $$('.tile').forEach(function(tile){if(tile.dataset.go==='apps'){tile.addEventListener('click',function(){setTimeout(adaptApps,50)})}});
 document.addEventListener('click',function(e){if(e.target.closest('#grid .choice')&&$('#heading').textContent===t('extras'))setTimeout(showAgenda,0)},false);
}
init();
})();