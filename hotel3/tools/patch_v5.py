#!/usr/bin/env python3
"""Ô31 Hôtel V5 - premium app carousel, multi-event OpenAgenda, locally packaged film.
Run after patch_v4.py, before Gradle. Fails loud if source patterns drift.
"""
from pathlib import Path
import re
root=Path("hotel3/app/src/main")
js_path=root/"assets/app.js"
css_path=root/"assets/style.css"
html_path=root/"assets/index.html"
java_path=root/"java/com/o31languedoc/hotel/MainActivity.java"
gradle_path=Path("hotel3/app/build.gradle")
manifest_path=root/"AndroidManifest.xml"

def replace_once(src,old,new,label):
    if old not in src: raise RuntimeError(f"V5 mismatch: {label}")
    return src.replace(old,new,1)
def replace_section(src,start,end,replacement,label):
    a=src.find(start)
    b=src.find(end,a+len(start)) if a>=0 else -1
    if a<0 or b<0:raise RuntimeError("V5 missing "+label)
    return src[:a]+replacement+"\n"+src[b:]

# Keep the official YouTube player from V4: preserves controls and creator credit.
# The video end marker is added below; no third-party file is downloaded by CI.
js=js_path.read_text()
js=replace_once(js,"&playsinline=1&enablejsapi=1","&end=73&playsinline=1&enablejsapi=1","YouTube stop before last 10 seconds")
new_apps=r"""
function appsPage(){
 const t={
 fr:{lead:'Votre divertissement, à portée de télécommande',fav:'Vos applications TV',all:'Toutes les applications installées',allBtn:'Voir toutes les applications',tv:'Streaming & TV',other:'Autres applications',count:'applications détectées',admin:'ESPACE PROPRIÉTAIRE',notice:'Les applications affichées correspondent uniquement aux services réellement installés.'},
 en:{lead:'Entertainment at your fingertips',fav:'Your TV applications',all:'All installed apps',allBtn:'See all applications',tv:'Streaming & TV',other:'Other applications',count:'apps detected',admin:'OWNER ACCESS',notice:'Only apps actually installed on this device are shown.'},
 es:{lead:'Entretenimiento a su alcance',fav:'Aplicaciones TV',all:'Todas las aplicaciones',allBtn:'Ver todas las aplicaciones',tv:'Streaming y TV',other:'Otras aplicaciones',count:'aplicaciones detectadas',admin:'ACCESO PROPIETARIO',notice:'Se muestran únicamente las aplicaciones instaladas.'}
 }[L];
 $('sectionDesc').textContent=t.lead;
 const body=$('sectionBody');body.classList.add('apps-premium');
 let original=[];try{if(window.O31)original=JSON.parse(O31.listApps())||[];}catch(e){}
 const found=new Set();
 const installed=original.filter(a=>a&&a.packageName&&!found.has(a.packageName)&&found.add(a.packageName))
  .map(a=>({...a,label:String(a.label||a.packageName)}));
 const streaming=[
  {test:/netflix/i,priority:1,tag:'NETFLIX'},
  {test:/youtube|smarttube/i,priority:2,tag:'YOUTUBE'},
  {test:/prime.video|amazon prime/i,priority:3,tag:'PRIME VIDEO'},
  {test:/disney\+|disney plus/i,priority:4,tag:'DISNEY+'},
  {test:/mycanal|canal\+|canal plus/i,priority:5,tag:'CANAL+'},
  {test:/molotov/i,priority:6,tag:'MOLOTOV'},
  {test:/france\.tv|france tv/i,priority:7,tag:'FRANCE.TV'},
  {test:/arte/i,priority:8,tag:'ARTE'},
  {test:/apple tv/i,priority:9,tag:'APPLE TV'},
  {test:/pluto tv/i,priority:10,tag:'PLUTO TV'},
  {test:/spotify/i,priority:11,tag:'SPOTIFY'},
  {test:/plex/i,priority:12,tag:'PLEX'},
  {test:/kodi/i,priority:13,tag:'KODI'},
  {test:/twitch/i,priority:14,tag:'TWITCH'},
  {test:/mytf1|tf1\+/i,priority:15,tag:'TF1+'},
  {test:/6play|m6\+/i,priority:16,tag:'M6+'},
  {test:/rakuten tv/i,priority:17,tag:'RAKUTEN'}
 ];
 function rank(a){const name=a.label+' '+a.packageName;for(const x of streaming)if(x.test.test(name))return x.priority;return 999;}
 installed.sort((a,b)=>rank(a)-rank(b)||a.label.localeCompare(b.label,L));
 const tvapps=installed.filter(x=>rank(x)<999);
 const header=document.createElement('div');header.className='app-showcase-head';
 header.innerHTML='<div><div class="app-overline">Ô31 LANGUEDOC · ENTERTAINMENT</div><h3>'+clean(t.fav)+'</h3></div><span class="app-count">'+installed.length+' '+clean(t.count)+'</span>';
 body.appendChild(header);
 function appTile(a,featured){
   const b=document.createElement('button');b.className='premium-app'+(featured?' featured-app':'');
   const art=document.createElement('span');art.className='premium-app-art';
   const icon=document.createElement('span');icon.className='premium-app-icon';
   if(a.icon&&/^data:image\/(png|jpeg|webp);base64,/.test(a.icon)){
     const img=document.createElement('img');img.src=a.icon;img.alt='';icon.appendChild(img);
   } else {icon.textContent=a.label.slice(0,1).toUpperCase();}
   art.appendChild(icon);b.appendChild(art);
   const label=document.createElement('span');label.className='premium-app-name';label.textContent=a.label;b.appendChild(label);
   if(featured){const mark=document.createElement('span');mark.className='premium-app-tag';mark.textContent='TV';b.appendChild(mark)}
   b.onclick=()=>{if(window.O31)try{O31.openApp(a.packageName)}catch(e){}};
   return b;
 }
 if(tvapps.length){
   const hero=document.createElement('div');hero.className='premium-featured-row';
   tvapps.forEach(a=>hero.appendChild(appTile(a,true)));body.appendChild(hero);
 }else{
   const empty=document.createElement('div');empty.className='app-empty';
   empty.textContent=T[L].noApps;body.appendChild(empty);
 }
 const sub=document.createElement('div');sub.className='app-catalog-head';
 const label=document.createElement('h3');label.textContent=t.all;sub.appendChild(label);
 const expand=document.createElement('button');expand.className='action catalog-toggle';expand.textContent=t.allBtn+' ('+installed.length+')';
 sub.appendChild(expand);body.appendChild(sub);
 const catalog=document.createElement('div');catalog.className='app-catalog-grid hidden';
 const rest=installed.filter(x=>rank(x)===999);
 installed.forEach(a=>catalog.appendChild(appTile(a,false)));
 expand.onclick=()=>{const expanded=catalog.classList.toggle('hidden')===false;expand.textContent=(expanded?'− ':'+ ')+t.allBtn+' ('+installed.length+')';if(expanded){const b=catalog.querySelector('button');if(b)b.focus()}};
 body.appendChild(catalog);
 const admin=document.createElement('div');admin.className='app-admin-row';
 const settings=button('<span class="admin-lock">♙</span><span><strong>'+clean(T[L].settings)+'</strong><small>'+clean(T[L].settingsDesc)+'</small></span><span>›</span>','admin-setting',openPin);
 admin.innerHTML='<span class="app-overline">'+clean(t.admin)+'</span>';
 admin.appendChild(settings);body.appendChild(admin);
 const copyright=document.createElement('p');copyright.className='sectionnote';copyright.textContent=t.notice;body.appendChild(copyright);
}
"""
js=replace_section(js,"function appsPage(){","function cityPage(){",new_apps,"app catalog")
new_agenda=r"""
let agendaRange='month',agendaCategory='all',agendaIndex=0,agendaPageSize=9;
function agendaPage(){
 const t={
 fr:{caption:'Agenda vivant de Toulouse · concerts, expositions, spectacles et sorties',all:'Tout',today:"Aujourd’hui",week:'7 jours',month:'30 jours',future:'À venir',music:'Musique',family:'Famille',shows:'Spectacles',art:'Culture',free:'Gratuit',count:'sorties trouvées',next:'Suivant',previous:'Précédent',source:'Source : OpenAgenda / OpenDataSoft',details:'Voir la fiche de l’événement',notFound:'Aucune sortie pour ce filtre. Choisissez une autre période.',newData:'Les événements s’actualisent automatiquement. Les organisateurs restent responsables de leurs annonces.',web:'Voir l’agenda officiel',noImage:'Événement à Toulouse'},
 en:{caption:'Live Toulouse events · concerts, exhibitions, shows and outings',all:'All',today:'Today',week:'7 days',month:'30 days',future:'Upcoming',music:'Music',family:'Family',shows:'Shows',art:'Culture',free:'Free',count:'events found',next:'Next',previous:'Previous',source:'Source: OpenAgenda / OpenDataSoft',details:'Open event page',notFound:'No events for this filter. Choose another date range.',newData:'Events update automatically. Organizers are responsible for event details.',web:'Official city calendar',noImage:'Event in Toulouse'},
 es:{caption:'Agenda de Toulouse · conciertos, exposiciones y espectáculos',all:'Todo',today:'Hoy',week:'7 días',month:'30 días',future:'Próximamente',music:'Música',family:'Familia',shows:'Espectáculos',art:'Cultura',free:'Gratis',count:'eventos encontrados',next:'Siguiente',previous:'Anterior',source:'Fuente: OpenAgenda / OpenDataSoft',details:'Ver la ficha',notFound:'No hay eventos con estos filtros.',newData:'Actualización automática; los organizadores son responsables de la información.',web:'Agenda oficial',noImage:'Evento en Toulouse'}
 }[L];
 $('sectionDesc').textContent=t.caption;
 const body=$('sectionBody');body.classList.add('agenda-premium');
 body.innerHTML='';
 const lead=document.createElement('div');lead.className='agenda-lead';
 lead.innerHTML='<div><div class="app-overline">TOULOUSE · AGENDA CULTUREL</div><h3>'+clean(t.caption)+'</h3></div><span id="agendaCount" class="app-count"></span>';
 body.appendChild(lead);
 const ranges=document.createElement('div');ranges.className='agenda-filters';ranges.id='agendaRanges';
 for(const [key,name] of [['today',t.today],['week',t.week],['month',t.month],['all',t.future]]){
  const b=button(clean(name),'premium-chip'+(agendaRange===key?' active':''),()=>{agendaRange=key;agendaIndex=0;drawAgenda()});b.dataset.range=key;ranges.appendChild(b);
 }
 body.appendChild(ranges);
 const kinds=document.createElement('div');kinds.className='agenda-filters categories';kinds.id='agendaCategories';
 for(const [key,name] of [['all',t.all],['music',t.music],['shows',t.shows],['family',t.family],['art',t.art],['free',t.free]]){
  const b=button(clean(name),'premium-chip'+(agendaCategory===key?' active':''),()=>{agendaCategory=key;agendaIndex=0;drawAgenda()});b.dataset.cat=key;kinds.appendChild(b);
 }
 body.appendChild(kinds);
 const status=document.createElement('p');status.id='agendaStatus';status.className='sectionnote';body.appendChild(status);
 const grid=document.createElement('div');grid.className='agenda-tile-grid';grid.id='eventGrid';body.appendChild(grid);
 const pager=document.createElement('div');pager.className='agenda-pager';pager.id='agendaPager';body.appendChild(pager);
 const footer=document.createElement('p');footer.className='sectionnote';footer.textContent=t.newData+' · '+t.source;body.appendChild(footer);
 drawAgenda();
}
function parseDate(v){if(!v)return null;const d=new Date(v);return Number.isFinite(d.getTime())?d:null;}
function eventField(r,keys){for(const k of keys){if(r&&r[k]!=null&&String(r[k]).trim())return r[k]}return'';}
function safeImage(url){return typeof url==='string'&&/^https:\/\/[^<>"']{1,800}$/i.test(url)?url:null;}
function agendaEntries(){
 const rows=(agenda&&Array.isArray(agenda.results))?agenda.results:[];
 const uniq=new Set(),now=Date.now();
 return rows.map(r=>{
  const title=String(eventField(r,['title_fr','title','nom_de_la_manifestation','nom','titre'])||'Sortie à Toulouse');
  const start=parseDate(eventField(r,['display_start','firstdate_begin','date_debut','start_date']));
  const end=parseDate(eventField(r,['display_end','lastdate_end','date_fin','end_date']))||start;
  const city=String(eventField(r,['location_city','commune'])||'Toulouse');
  const place=String(eventField(r,['location_name','lieu_nom','lieu'])||'Toulouse');
  const address=String(eventField(r,['location_address','lieu_adresse_1','adresse'])||place);
  const image=safeImage(eventField(r,['image','thumbnail','image_source']));
  const url=String(eventField(r,['canonicalurl','reservation_site_internet'])||'https://metropole.toulouse.fr/agenda');
  const desc=String(eventField(r,['description_fr','description','descriptif_court'])||'').replace(/<[^>]*>/g,' ').replace(/\s+/g,' ').trim();
  const category=(title+' '+desc+' '+eventField(r,['keywords_fr','keywords','originagenda_title','agenda_title'])).toLowerCase();
  const free=/gratuit|entrée libre|free|gratis/i.test(String(eventField(r,['conditions_fr','description_fr','keywords_fr','conditions','description','keywords']))+' '+title);
  const id=String(eventField(r,['uid','id','identifiant'])||title+':'+(start?start.toISOString():''));
  return {r,title,start,end,city,place,address,image,url,desc,category,free,id};
 }).filter(e=>{
   if(!e.start||!e.end||e.end.getTime()<now-86400000)return false;
   if(e.city.toLowerCase().trim()!=='toulouse')return false;
   if(uniq.has(e.id))return false;uniq.add(e.id);return true;
 }).sort((a,b)=>Math.max(a.start.getTime(),now)-Math.max(b.start.getTime(),now)||a.title.localeCompare(b.title));
}
function drawAgenda(){
 if(page!=='agenda'||!$('eventGrid'))return;
 const body=$('sectionBody'),grid=$('eventGrid'),pager=$('agendaPager'),status=$('agendaStatus');
 const text={
 fr:{found:'sorties',none:'Aucun événement correspondant pour le moment.',view:'Détails',next:'Suivant',prev:'Précédent',free:'Gratuit',official:'Agenda officiel',update:'Actualisation',date:'Date',venue:'Lieu',credit:'Crédit photo',info:'Consulter l’événement'},
 en:{found:'events',none:'No events match these filters yet.',view:'Details',next:'Next',prev:'Previous',free:'Free',official:'Official calendar',update:'Updated',date:'Date',venue:'Venue',credit:'Photo credit',info:'Open event page'},
 es:{found:'eventos',none:'No hay eventos con estos filtros.',view:'Detalles',next:'Siguiente',prev:'Anterior',free:'Gratis',official:'Agenda oficial',update:'Actualizado',date:'Fecha',venue:'Lugar',credit:'Crédito de imagen',info:'Ver evento'}
 }[L];
 const now=new Date(),startDay=new Date(now.getFullYear(),now.getMonth(),now.getDate());
 const maxEnd=agendaRange==='today'?startDay.getTime()+86400000:agendaRange==='week'?now.getTime()+7*86400000:agendaRange==='month'?now.getTime()+30*86400000:Infinity;
 const list=agendaEntries().filter(e=>{
  if(agendaRange==='today'){
    if(e.start.getTime()>maxEnd||e.end.getTime()<startDay.getTime())return false;
  } else if(e.start.getTime()>maxEnd)return false;
  const txt=e.category;
  if(agendaCategory==='music')return /concert|music|musique|jazz|rap|orchestre|festival/.test(txt);
  if(agendaCategory==='shows')return /spectacle|théâtre|theatre|danse|comedie|comédie|humour|opéra/.test(txt);
  if(agendaCategory==='family')return /enfant|famille|kids|jeunesse|atelier|jeune public|parent/.test(txt);
  if(agendaCategory==='art')return /expo|musée|musee|culture|patrimoine|visite|art/.test(txt);
  if(agendaCategory==='free')return e.free;
  return true;
 });
 status.textContent=(offlineStamp.agenda?text.update+' : '+new Date(offlineStamp.agenda).toLocaleString(locale())+' · ':'')+'OpenAgenda · Toulouse';
 $('agendaCount').textContent=list.length+' '+text.found;
 document.querySelectorAll('[data-range]').forEach(x=>x.classList.toggle('active',x.dataset.range===agendaRange));
 document.querySelectorAll('[data-cat]').forEach(x=>x.classList.toggle('active',x.dataset.cat===agendaCategory));
 grid.innerHTML='';pager.innerHTML='';
 if(!list.length){
   const empty=document.createElement('div');empty.className='agenda-empty';empty.innerHTML='<div class="agenda-empty-icon">✧</div><p>'+clean(text.none)+'</p>';
   const a=button(clean(text.official),'action',()=>{if(window.O31)O31.openLink('https://metropole.toulouse.fr/agenda')});empty.appendChild(a);grid.appendChild(empty);return;
 }
 const count=Math.ceil(list.length/agendaPageSize);agendaIndex=Math.max(0,Math.min(agendaIndex,count-1));
 list.slice(agendaIndex*agendaPageSize,(agendaIndex+1)*agendaPageSize).forEach(e=>{
  const b=document.createElement('button');b.className='agenda-event-card';
  const art=document.createElement('span');art.className='agenda-event-art';
  if(e.image){const im=new Image();im.alt='';im.loading='lazy';im.referrerPolicy='no-referrer';im.src=e.image;im.onerror=()=>{im.remove();art.classList.add('no-photo')};art.appendChild(im)}
  else art.classList.add('no-photo');
  const date=document.createElement('span');date.className='agenda-date-stamp';date.textContent=e.start.toLocaleDateString(locale(),{day:'2-digit',month:'short'});art.appendChild(date);
  b.appendChild(art);
  const info=document.createElement('span');info.className='agenda-event-details';
  const heading=document.createElement('strong');heading.textContent=e.title;info.appendChild(heading);
  const where=document.createElement('span');where.textContent=e.place;info.appendChild(where);
  const foot=document.createElement('small');foot.textContent=e.start.toLocaleDateString(locale(),{weekday:'short',day:'numeric',month:'short'})+(e.free?' · '+text.free:'');info.appendChild(foot);
  b.appendChild(info);
  b.onclick=()=>{
    const img=e.image?'<img class="agenda-modal-img" src="'+clean(e.image)+'" alt="">':'';
    const content=img+'<div class="information">'+clean(e.desc||e.title)+'</div>'+
      '<div class="line"><strong>'+clean(text.date)+'</strong> '+clean(e.start.toLocaleString(locale(),{dateStyle:'full',timeStyle:'short'}))+'</div>'+
      '<div class="line"><strong>'+clean(text.venue)+'</strong> '+clean(e.place)+' · '+clean(e.address)+'</div>'+
      (e.r.imagecredits?'<p class="sectionnote">'+clean(text.credit)+': '+clean(e.r.imagecredits)+'</p>':'')+
      '<div class="modal-buttons"><button class="action" id="agendaOpenOfficial">'+clean(text.info)+'</button></div>';
    openModal(e.title,content);
    $('agendaOpenOfficial').onclick=()=>{if(window.O31)O31.openLink(e.url.startsWith('https://')?e.url:'https://metropole.toulouse.fr/agenda')};
  };
  grid.appendChild(b);
 });
 if(count>1){
  const prev=button('← '+text.prev,'action',()=>{agendaIndex--;drawAgenda()});prev.disabled=agendaIndex===0;
  const counter=document.createElement('span');counter.className='agenda-page-index';counter.textContent=(agendaIndex+1)+' / '+count;
  const next=button(text.next+' →','action',()=>{agendaIndex++;drawAgenda()});next.disabled=agendaIndex===count-1;
  pager.append(prev,counter,next);
 }
}
"""
js=replace_section(js,"function agendaPage(){","function openModal(",new_agenda,"agenda system")
js_path.write_text(js)

css=css_path.read_text()+"""
/* V5 premium TV applications */
.apps-premium{padding:0 1.1vw 2vh}.app-showcase-head,.app-catalog-head,.agenda-lead{display:flex;align-items:center;justify-content:space-between;gap:2vw;margin:0 0 1.3vh}
.app-overline{color:#d8b785;font-size:.7vw;letter-spacing:.2em;font-weight:700}
.apps-premium h3,.agenda-premium h3{color:#f5d2a2;font:normal 1.8vw Georgia,serif;margin:.5vh 0}
.app-count{color:#ebd3b2;font-size:.82vw;white-space:nowrap}
.premium-featured-row{display:flex;gap:1.05vw;overflow-x:auto;padding:.8vw .5vw 1.6vw;scroll-snap-type:x proximity;scrollbar-color:#caaa6a #10273c;scrollbar-width:thin;min-height:23vh}
.premium-app{position:relative;flex:0 0 10.7vw;display:flex;align-items:center;flex-direction:column;justify-content:center;background:linear-gradient(146deg,#213e58,#10253c 58%,#0c2035);border-radius:17px;border:1px solid #ae9874a0;min-height:19vh;padding:1.1vw .5vw;box-shadow:0 12px 32px #010d1b66;overflow:hidden;gap:1.3vh;scroll-snap-align:start;transition:transform .18s,border-color .18s}
.premium-app.featured-app{flex-basis:12.7vw;min-height:24vh;background:radial-gradient(circle at 50% 0%,#395678a6,transparent 58%),linear-gradient(155deg,#1d334a,#0a1b2d)}
.premium-app:before{content:'';position:absolute;inset:0;background:linear-gradient(120deg,#fff1 0%,transparent 38%,#ffffff08 100%);pointer-events:none}
.premium-app:focus{z-index:5;border:2px solid #f5d69f;background:linear-gradient(155deg,#2f506a,#0e2d49)}
.premium-app-art{width:6.1vw;height:6.1vw;min-width:60px;min-height:60px;display:flex;align-items:center;justify-content:center;border-radius:20px;background:#ffffff13;box-shadow:0 10px 22px #0006,inset 0 1px #fff2}
.premium-app-icon{font-size:3vw;display:flex;align-items:center;justify-content:center;width:100%;height:100%}
.premium-app-icon img{width:80%;height:80%;object-fit:contain;filter:drop-shadow(0 3px 5px #0004)}
.premium-app-name{font-size:.96vw;text-align:center;max-width:100%;text-wrap:balance;line-height:1.25;color:#f6ecdf}
.premium-app-tag{position:absolute;top:.8vw;right:.75vw;letter-spacing:.15em;font-size:.54vw;color:#f4d293}
.app-catalog-head{margin-top:1vh;padding-top:1vh;border-top:1px solid #e4c29336}.app-catalog-grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:.85vw;padding:1vw .4vw}
.app-catalog-grid .premium-app{width:100%;min-height:16vh;padding:.6vw;flex-basis:auto}.app-catalog-grid .premium-app-art{width:3.8vw;height:3.8vw;min-width:45px;min-height:45px;border-radius:13px}
.app-catalog-grid .premium-app-name{font-size:.82vw}.app-catalog-grid .premium-app-icon{font-size:2.2vw}
.catalog-toggle{padding:.6vw 1vw;font-size:.85vw}.app-admin-row{margin-top:2vh;padding:1vw;border-radius:15px;border:1px solid #d1b48747;background:linear-gradient(110deg,#172f48,#0d2439)}
.admin-setting{display:flex;align-items:center;justify-content:space-between;gap:1vw;border:none;background:transparent;padding:.75vw .7vw;width:100%;text-align:left}
.admin-setting>span:nth-child(2){display:flex;flex-direction:column;gap:.5vh;flex:1}.admin-setting strong{font-size:1.15vw;color:#f0d09e}.admin-setting small{font-size:.8vw;color:#c5d1db}.admin-lock{font-size:2.2vw;color:#e6bf80}.app-empty{background:#ffffff12;border:1px solid #e7c59755;border-radius:14px;padding:2vw}
/* V5 agenda media-rich grid */
.agenda-premium{padding:0 1.1vw 3vh}.agenda-lead h3{font-size:1.5vw}.agenda-filters{display:flex;gap:.65vw;margin-bottom:1.2vh;flex-wrap:wrap}
.premium-chip{padding:.65vw 1.2vw;border:1px solid #bba1767a;border-radius:28px;background:#152f49;color:#ead1aa;font-size:.85vw}
.premium-chip.active{color:#0c2338;background:#edcd98;font-weight:700}
.agenda-tile-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1.05vw;padding:.5vw 0 1vw}
.agenda-event-card{position:relative;display:flex;flex-direction:column;text-align:left;border:1px solid #ad936d9c;border-radius:16px;overflow:hidden;background:linear-gradient(155deg,#20384f,#0c2135);min-height:27vh}
.agenda-event-art{display:block;height:15vh;min-height:110px;position:relative;overflow:hidden;background:radial-gradient(circle at 40% 20%,#bd94697a,transparent 66%),linear-gradient(100deg,#193b4e,#0b2138)}
.agenda-event-art img{width:100%;height:100%;object-fit:cover;filter:saturate(.9) brightness(.84)}
.agenda-event-art:after{content:'';position:absolute;inset:0;background:linear-gradient(0deg,#0b223c6e,transparent 66%)}
.agenda-date-stamp{position:absolute;z-index:2;left:.8vw;top:.7vw;background:#e8c58e;color:#16263a;font-weight:700;font-size:.77vw;border-radius:8px;padding:.5vw .7vw}
.agenda-event-details{display:flex;flex:1;flex-direction:column;gap:.7vh;padding:1vw 1.1vw}
.agenda-event-details strong{font:normal 1.14vw Georgia,serif;color:#ffe1ad;line-height:1.15}
.agenda-event-details>span{font-size:.85vw;color:#d9dbe1}
.agenda-event-details small{font-size:.76vw;color:#b9c6d2;margin-top:auto}
.agenda-empty{grid-column:1/-1;min-height:22vh;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:1vh;background:#ffffff0a;border-radius:14px}
.agenda-empty-icon{font-size:3vw;color:#e8c58e}.agenda-pager{display:flex;align-items:center;justify-content:center;gap:1.5vw;padding:1vw 0}
.agenda-page-index{font:normal 1.3vw Georgia,serif;color:#f0d19d}.agenda-pager .action[disabled]{opacity:.3}
.agenda-modal-img{float:right;width:43%;max-height:38vh;object-fit:contain;border-radius:12px;margin:0 0 1vw 1vw}
/* V5 original video: local, muted, loop, no YouTube controls */
#home .local-film-container{position:relative;flex:1;min-height:0;overflow:hidden;border:1px solid #e7c5889e;border-radius:16px;background:#081a30;box-shadow:0 15px 45px #010f24a0}
#home .local-film-container video{display:block;width:100%;height:100%;object-fit:cover;background:#07182b}
#home .video-feature{justify-content:flex-start;padding-top:.7vh}
@media(max-height:680px){.premium-app.featured-app{min-height:18vh}.premium-featured-row{min-height:21vh}.agenda-event-art{height:12vh}.agenda-event-card{min-height:25vh}}
"""
css_path.write_text(css)

java=java_path.read_text()
native_start="  @JavascriptInterface public void fetch(String type){"
start=java.find(native_start)
end=java.find("\n }\n}",start)
if start<0 or end<0:raise RuntimeError("Native agenda fetch function not found")
new_native=r"""  @JavascriptInterface public void fetch(String type){
    if(!("agenda".equals(type)||"weather".equals(type)))return;
    new Thread(()->{
      try{
        String content;
        if("weather".equals(type)){
          content=getString("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=43.6047&lon=1.4442");
        }else{
          JSONObject combined=new JSONObject();
          JSONArray events=new JSONArray();
          String now=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.FRANCE).format(new java.util.Date());
          String where="location_city = \"Toulouse\" AND lastdate_end >= \""+now+"\"";
          String select="uid,title_fr,description_fr,conditions_fr,keywords_fr,image,imagecredits,thumbnail,canonicalurl,firstdate_begin,lastdate_begin,lastdate_end,location_name,location_city,location_address,originagenda_title,timings,updatedat";
          String endpoint="https://public.opendatasoft.com/api/explore/v2.1/catalog/datasets/evenements-publics-openagenda/records"
             +"?where="+java.net.URLEncoder.encode(where,"UTF-8")
             +"&select="+java.net.URLEncoder.encode(select,"UTF-8")
             +"&order_by=lastdate_end%20ASC&limit=50";
          int total=0;
          for(int offset=0;offset<1500;offset+=50){
            String response=getString(endpoint+"&offset="+offset);
            JSONObject page=new JSONObject(response);
            JSONArray rows=page.optJSONArray("results");
            if(rows==null)throw new java.io.IOException("OpenAgenda response has no results");
            total=page.optInt("total_count",0);
            for(int i=0;i<rows.length();i++){
              JSONObject item=rows.getJSONObject(i);
              String intervals=item.optString("timings","");
              try{
                if(intervals.startsWith("[")){
                  JSONArray times=new JSONArray(intervals);
                  String soonest=null,soonestEnd=null;
                  java.time.Instant threshold=java.time.Instant.now().minusSeconds(300);
                  for(int j=0;j<times.length();j++){
                    JSONObject slot=times.optJSONObject(j);
                    if(slot==null)continue;
                    String begin=slot.optString("begin",""),finish=slot.optString("end","");
                    if(begin.length()<19||finish.length()<19)continue;
                    try{
                      if(java.time.OffsetDateTime.parse(finish).toInstant().isBefore(threshold))continue;
                      if(soonest==null||java.time.OffsetDateTime.parse(begin).toInstant().isBefore(java.time.OffsetDateTime.parse(soonest).toInstant())){
                        soonest=begin;soonestEnd=finish;
                      }
                    }catch(Exception ignored){}
                  }
                  if(soonest!=null){item.put("display_start",soonest);item.put("display_end",soonestEnd);}
                }
              }catch(Exception ignored){}
              item.remove("timings");
              events.put(item);
            }
            if(rows.length()<50)break;
          }
          combined.put("results",events);
          combined.put("total_count",total);
          combined.put("provider","OpenAgenda via Opendatasoft");
          content=combined.toString();
        }
        JSONObject parsed=new JSONObject(content);
        long stamp=System.currentTimeMillis();
        JSONObject cache=new JSONObject();cache.put("data",parsed);cache.put("stamp",stamp);
        state.edit().putString("cache_"+type,cache.toString()).apply();
        String cmd="window.O31_onRemote&&window.O31_onRemote("+JSONObject.quote(type)+","+JSONObject.quote(content)+","+stamp+")";
        runOnUiThread(()->{if(web!=null)web.evaluateJavascript(cmd,null);});
      }catch(Exception ex){
        android.util.Log.w("O31HotelV5","Unable to refresh "+type,ex);
      }
    },"O31_"+type).start();
  }"""
java=java[:start]+new_native+java[end:]
java_path.write_text(java)
gradle=gradle_path.read_text()
gradle=replace_once(gradle,"applicationId 'com.o31languedoc.hotel.v4'","applicationId 'com.o31languedoc.hotel.v5'","V5 package")
gradle=replace_once(gradle,'versionCode 4','versionCode 5','V5 version code')
gradle=replace_once(gradle,"versionName '4.0-youtube'","versionName '5.0-premium-agenda'","V5 name")
gradle_path.write_text(gradle)
manifest=manifest_path.read_text()
manifest=replace_once(manifest,'android:label="Ô31 Languedoc Hôtel V4"','android:label="Ô31 Languedoc Hôtel V5"',"V5 app name")
manifest_path.write_text(manifest)

assert 'OpenAgenda via Opendatasoft' in java
assert 'premium-featured-row' in js
assert '&end=73' in js
print("V5 patch complete - curated TV apps, multi-event data, premium agenda, 73s official YouTube playback, all 3 languages")
