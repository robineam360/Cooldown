/* CCRM-94 (iOS App) wireframes, rev A — builds design/2026-10-23-ios-wireframes.html.
   Every bar, ring and chart is drawn by the same few functions so the states agree with each other.
   Numbers follow spec/core/BEHAVIOR-SPEC.md: colour breakpoints >80 yellow, >90 orange, >=100 red;
   above pace = more than 3 points over the even-pace line, the overshoot drawn red from the pace tick. */
"use strict";

/* ============ tokens ============ */
const DARK = {name:'dark', bg:'#0E0E0E', card:'#1C1C1E', ink:'#F2F2F2', txt:'#D1D1D6', sub:'#98989F', line:'#38383A',
  sheet:'#1C1C1E', group:'#1C1C1E', fill:'#2C2C2E', Y:'#FDD663', O:'#FFA726', R:'#FF5252', pace:'#FDD663',
  tick:'#C7C7CC', ringTrack:'#55555A', widget:'#1C1C1E', notice:'#3A2A26'};
const LIGHT = {name:'light', bg:'#F2F2F7', card:'#FFFFFF', ink:'#000000', txt:'#3A3A3C', sub:'#6C6C70', line:'#D1D1D6',
  sheet:'#FFFFFF', group:'#FFFFFF', fill:'#E5E5EA', Y:'#F9A825', O:'#F57C00', R:'#C62828', pace:'#E0A000',
  tick:'#8E8E93', ringTrack:'#C7C7CC', widget:'#FFFFFF', notice:'#FBE9E4'};
const ACC = {claude:{d:'#E59980', l:'#D97757'}, chatgpt:{d:'#19C39A', l:'#10A37F'}};
const ACCTS = {
  teams:   {n:'Teams',    p:'claude',  plan:'Team'},
  product: {n:'Product',  p:'claude',  plan:'Max 5×'},
  personal:{n:'Personal', p:'chatgpt', plan:'Plus'},
};
const ORDER = ['teams','product','personal'];
const acc = (a,t) => ACC[ACCTS[a].p][t===LIGHT ? 'l' : 'd'];
function rgba(hex,a){const n=parseInt(hex.slice(1),16);return `rgba(${n>>16},${(n>>8)&255},${n&255},${a})`}
function sev(p,ac,t){if(p==null)return ac;if(p>=100)return t.R;if(p>90)return t.O;if(p>80)return t.Y;return ac}
const pctTxt = p => p==null ? '—' : Math.floor(p)+'%';
const mark = (p,s,c) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" style="flex:none;display:block"><path d="${MARK[p]}" fill="${c}"/></svg>`;
const amark = (a,s,t,c) => mark(ACCTS[a].p, s, c || acc(a,t));

/* ============ icons (SF-Symbol stand-ins) ============ */
const sv = (w,h,inner,extra='') => `<svg width="${w}" height="${h}" viewBox="0 0 24 24" style="flex:none;display:block${extra}" fill="none" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`;
const ico = {
  cal:c=>sv(24,24,`<rect x="3.5" y="5" width="17" height="15" rx="3" stroke="${c}" stroke-width="2"/><path d="M3.5 10h17M8 3v4M16 3v4" stroke="${c}" stroke-width="2"/>`),
  gear:c=>sv(24,24,`<circle cx="12" cy="12" r="3.2" stroke="${c}" stroke-width="2"/><path d="M12 2.8v2.6M12 18.6v2.6M2.8 12h2.6M18.6 12h2.6M5.5 5.5l1.8 1.8M16.7 16.7l1.8 1.8M5.5 18.5l1.8-1.8M16.7 7.3l1.8-1.8" stroke="${c}" stroke-width="2"/>`),
  more:c=>sv(24,24,`<circle cx="12" cy="12" r="9.5" stroke="${c}" stroke-width="1.8"/><circle cx="7.5" cy="12" r="1.3" fill="${c}"/><circle cx="12" cy="12" r="1.3" fill="${c}"/><circle cx="16.5" cy="12" r="1.3" fill="${c}"/>`),
  refresh:c=>sv(18,18,`<path d="M19 12a7 7 0 1 1-2.1-5" stroke="${c}" stroke-width="2.2"/><path d="M19 4v4h-4" stroke="${c}" stroke-width="2.2"/>`),
  up:c=>sv(20,20,`<path d="M6 15l6-6 6 6" stroke="${c}" stroke-width="2.4"/>`),
  down:c=>sv(20,20,`<path d="M6 9l6 6 6-6" stroke="${c}" stroke-width="2.4"/>`),
  back:c=>sv(24,24,`<path d="M15 5l-7 7 7 7" stroke="${c}" stroke-width="2.6"/>`),
  left:c=>sv(20,20,`<path d="M14 6l-6 6 6 6" stroke="${c}" stroke-width="2.4"/>`),
  right:c=>sv(20,20,`<path d="M10 6l6 6-6 6" stroke="${c}" stroke-width="2.4"/>`),
  info:c=>sv(15,15,`<circle cx="12" cy="12" r="9.5" stroke="${c}" stroke-width="2"/><path d="M12 11v6M12 7.5v.2" stroke="${c}" stroke-width="2.4"/>`,';display:inline-block;vertical-align:-2px'),
  chev:c=>sv(14,14,`<path d="M9 5l7 7-7 7" stroke="${c}" stroke-width="2.6"/>`),
  plus:c=>sv(22,22,`<path d="M12 5v14M5 12h14" stroke="${c}" stroke-width="2.4"/>`),
  side:c=>sv(24,24,`<rect x="3" y="4.5" width="18" height="15" rx="3" stroke="${c}" stroke-width="2"/><path d="M9.5 4.5v15" stroke="${c}" stroke-width="2"/>`),
  hour:c=>sv(22,22,`<path d="M6 3h12M6 21h12M7 3c0 5 10 5 10 9s-10 4-10 9M17 3c0 5-10 5-10 9s10 4 10 9" stroke="${c}" stroke-width="1.8"/>`),
  grip:c=>sv(20,20,`<path d="M5 9h14M5 15h14" stroke="${c}" stroke-width="2"/>`),
  warn:c=>sv(16,16,`<path d="M12 4l9 16H3z" stroke="${c}" stroke-width="2"/><path d="M12 10v4M12 17v.2" stroke="${c}" stroke-width="2.2"/>`,';display:inline-block;vertical-align:-2px'),
};
function statusIcons(c){
  return `<span class="sbicons" style="color:${c}"><svg width="18" height="12" viewBox="0 0 18 12"><rect x="0" y="8" width="3" height="4" rx="1" fill="${c}"/><rect x="5" y="5.5" width="3" height="6.5" rx="1" fill="${c}"/><rect x="10" y="3" width="3" height="9" rx="1" fill="${c}"/><rect x="15" y="0" width="3" height="12" rx="1" fill="${c}"/></svg>
  <svg width="17" height="12" viewBox="0 0 17 12"><path d="M8.5 11.5l2.3-2.6a3.4 3.4 0 0 0-4.6 0z" fill="${c}"/><path d="M3.6 6.3a7 7 0 0 1 9.8 0M1 3.6a10.8 10.8 0 0 1 15 0" stroke="${c}" stroke-width="1.7" fill="none" stroke-linecap="round"/></svg>
  <svg width="27" height="13" viewBox="0 0 27 13"><rect x=".5" y=".5" width="23" height="12" rx="3.8" stroke="${c}" stroke-opacity=".4" fill="none"/><rect x="2.5" y="2.5" width="16" height="8" rx="2" fill="${c}"/><path d="M25 4.5v4" stroke="${c}" stroke-opacity=".5" stroke-width="1.6" stroke-linecap="round"/></svg></span>`;
}

/* ============ primitives ============ */
function bar(o){
  const t=o.t, w=o.w, h=o.h||8, ac=o.ac, p=o.p;
  const trk = o.track || rgba(ac, t===LIGHT ? .20 : .26);
  let s='';
  if(p!=null && p>0){
    const pc=Math.min(p,100), col=sev(p,ac,t), fw=Math.max(w*pc/100, h);
    if(o.pace!=null && p<100 && p>o.pace+3){
      const pw=w*o.pace/100;
      s=`<i class="l" style="left:0;width:${pw}px;background:${col}"></i><i class="r" style="left:${pw}px;width:${fw-pw}px;background:${t.R}"></i>`;
    } else s=`<i style="left:0;width:${fw}px;background:${col}"></i>`;
  }
  const ext=Math.max(3,h*.45);
  const tk = (o.pace!=null && p!=null) ? `<b style="left:${Math.min(w-3,Math.max(0,w*o.pace/100-1.5))}px;background:${o.tick||t.tick};top:${-ext}px;bottom:${-ext}px"></b>` : '';
  return `<span class="bar" style="width:${w}px;height:${h}px;background:${trk}${o.dim?';opacity:.45':''}">${s}${tk}</span>`;
}

function ring(o){
  const t=o.t, s=o.s, sw=o.sw||Math.max(4,s*.075), ac=o.ac, p=o.p;
  const r=s/2-sw/2-2, c=2*Math.PI*r, cx=s/2;
  const arc=(f,to,col)=>`<circle cx="${cx}" cy="${cx}" r="${r}" fill="none" stroke="${col}" stroke-width="${sw}" stroke-linecap="round" stroke-dasharray="${Math.max(.01,c*(to-f)/100)} ${c}" stroke-dashoffset="${-c*f/100}" transform="rotate(-90 ${cx} ${cx})"/>`;
  let g=`<circle cx="${cx}" cy="${cx}" r="${r}" fill="none" stroke="${o.track||t.ringTrack}" stroke-width="${o.trackW||Math.max(1.5,sw*.3)}"/>`;
  if(p!=null && p>0){
    const pc=Math.min(p,100), col=o.mono||sev(p,ac,t);
    if(o.pace!=null && p<100 && p>o.pace+3) g+=arc(0,o.pace,col)+arc(o.pace,pc,o.mono||t.R);
    else g+=arc(0,pc,col);
  }
  if(o.pace!=null && p!=null){
    const a=(o.pace/100*360-90)*Math.PI/180, r1=r-sw*.95, r2=r+sw*.95;
    g+=`<line x1="${cx+r1*Math.cos(a)}" y1="${cx+r1*Math.sin(a)}" x2="${cx+r2*Math.cos(a)}" y2="${cx+r2*Math.sin(a)}" stroke="${o.tick||t.tick}" stroke-width="${Math.max(2,sw*.42)}" stroke-linecap="round"/>`;
  }
  const txt = o.label!==undefined ? o.label : pctTxt(p);
  g+=`<text x="${cx}" y="${cx+(o.dy||0)}" text-anchor="middle" dominant-baseline="central" font-size="${o.fs||s*.22}" font-weight="700" fill="${o.tc||o.mono||sev(p,ac,t)}" font-family="-apple-system,system-ui,sans-serif">${txt}</text>`;
  if(o.sub) g+=`<text x="${cx}" y="${cx+s*.2}" text-anchor="middle" dominant-baseline="central" font-size="${s*.13}" font-weight="600" fill="${o.tc||o.mono||t.sub}" font-family="-apple-system,system-ui,sans-serif">${o.sub}</text>`;
  return `<svg width="${s}" height="${s}" style="display:block;flex:none${o.dim?';opacity:.45':''}">${g}</svg>`;
}

/* The trend chart, as shipped (ui/Sparkline.kt): 80/90/100 guides, even-pace diagonal, samples with dots,
   the "now" marker, the dashed projection with its hollow end, and the time axis. Nothing removed. */
function chart(o){
  const t=o.t, W=o.w, H=o.h, ac=o.ac, T=20, B=H-20;
  const X=v=>v*W, Y=v=>B-(v/100)*(B-T), F='font-family="-apple-system,system-ui,sans-serif"';
  const guide=(v,col)=>`<line x1="0" x2="${W}" y1="${Y(v)}" y2="${Y(v)}" stroke="${col}" stroke-width="1" stroke-dasharray="2 4" opacity=".7"/>`;
  let g=guide(80,t.Y)+guide(90,t.O)+guide(100,t.R);
  g+=`<text x="${W}" y="${Y(100)-3}" text-anchor="end" font-size="10" fill="${t.R}" ${F}>100%</text>`;
  g+=`<text x="${W}" y="${Y(90)+3.5}" text-anchor="end" font-size="10" fill="${t.O}" ${F}>90%</text>`;
  g+=`<text x="${W}" y="${Y(80)+10}" text-anchor="end" font-size="10" fill="${t.Y}" ${F}>80%</text>`;
  g+=`<line x1="0" x2="13" y1="${Y(95)}" y2="${Y(95)}" stroke="${t.pace}" stroke-width="2.6" stroke-dasharray="4 3"/><text x="18" y="${Y(95)+3.5}" font-size="10.5" fill="${t.pace}" ${F}>even pace</text>`;
  g+=`<line x1="0" x2="${W}" y1="${B}" y2="${B}" stroke="${t.line}" stroke-width="1"/>`;
  const S=o.s||[], now=o.now;
  if(S.length){
    const pts=S.map(([x,v])=>`${X(x).toFixed(1)},${Y(v).toFixed(1)}`);
    g+=`<path d="M${X(S[0][0])},${B} L${pts.join(' L')} L${X(S[S.length-1][0])},${B}Z" fill="${rgba(ac,.24)}"/>`;
  }
  g+=`<line x1="0" y1="${Y(0)}" x2="${X(1)}" y2="${Y(100)}" stroke="${t.pace}" stroke-width="2.6" stroke-dasharray="5 4"/>`;
  if(now!=null) g+=`<line x1="${X(now)}" x2="${X(now)}" y1="${T-4}" y2="${B}" stroke="${t.sub}" stroke-width="1" opacity=".7"/>`;
  if(S.length){
    const last=S[S.length-1];
    if(o.proj!=null){
      // At 100% or more the projection stops where it crosses 100 and carries no label: the card says "runs out".
      const x0=last[0], v0=last[1], ex = o.proj>100 ? x0+(1-x0)*(100-v0)/(o.proj-v0) : 1, ev=Math.min(o.proj,100), cxp=X(ex)-(ex>=1?4:0);
      g+=`<line x1="${X(x0)}" y1="${Y(v0)}" x2="${cxp}" y2="${Y(ev)}" stroke="${rgba(ac,.75)}" stroke-width="2" stroke-dasharray="4 3"/><circle cx="${cxp}" cy="${Y(ev)}" r="4" fill="none" stroke="${o.proj>=100?t.R:rgba(ac,.85)}" stroke-width="1.8"/>`;
      if(o.proj<100) g+=`<text x="${X(1)-34}" y="${Y(ev)+(ev>=60?17:-9)}" text-anchor="end" font-size="11.5" font-weight="700" fill="${t.txt}" ${F}>~${Math.round(o.proj)}%</text>`;
    }
    g+=`<polyline points="${S.map(([x,v])=>`${X(x).toFixed(1)},${Y(v).toFixed(1)}`).join(' ')}" fill="none" stroke="${o.over?t.R:ac}" stroke-width="2.2" stroke-linejoin="round"/>`;
    S.slice(0,-1).forEach(([x,v])=>g+=`<circle cx="${X(x)}" cy="${Y(v)}" r="2.4" fill="${ac}"/>`);
    g+=`<circle cx="${X(last[0])}" cy="${Y(last[1])}" r="5" fill="${o.over?t.R:ac}"/>`;
    g+=`<text x="${X(last[0])-8}" y="${Y(last[1])-9}" text-anchor="end" font-size="12" font-weight="700" fill="${t.ink}" ${F}>${Math.floor(last[1])}%</text>`;
  }
  g+=`<text x="0" y="${B+15}" font-size="11" fill="${t.sub}" ${F}>${o.x0||''}</text>`;
  if(now!=null) g+=`<text x="${X(now)}" y="${B+15}" text-anchor="middle" font-size="11" fill="${t.sub}" ${F}>now</text>`;
  g+=`<text x="${W}" y="${B+15}" text-anchor="end" font-size="11" fill="${t.sub}" ${F}>${o.x1||''}</text>`;
  return `<svg width="${W}" height="${H}" style="display:block;overflow:visible${o.dim?';opacity:.45':''}">${g}</svg>`;
}

/* ============ sample data ============ */
const D5 = {s:[[0,0],[.05,0],[.12,0],[.2,0],[.27,0],[.29,4],[.32,6],[.38,9],[.45,13],[.49,16],[.51,17],[.55,18],[.6,19],[.64,24],[.66,25],[.68,26]], now:.68, p:26, pace:68, proj:42, x0:'4:50 pm', x1:'9:50 pm'};
const D5b = {s:[[0,0],[.06,2],[.11,5],[.18,11],[.24,19],[.3,27],[.36,34],[.41,40],[.46,46]], now:.46, p:46, pace:46, proj:83, x0:'4:50 pm', x1:'9:50 pm'};
const DUP = {s:[[0,0],[.05,6],[.1,13],[.15,21],[.2,30],[.25,38],[.3,47],[.36,55],[.4,62]], now:.4, p:62, pace:40, proj:155, x0:'4:50 pm', x1:'9:50 pm', run:'7:51 pm'};
const DMAX = {s:[[0,0],[.08,15],[.16,33],[.24,52],[.32,70],[.4,86],[.47,97],[.52,100],[.6,100]], now:.6, p:100, pace:60, proj:null, x0:'4:50 pm', x1:'9:50 pm'};
const DW = {s:[[0,0],[.05,1],[.1,3],[.14,6],[.2,8],[.26,11],[.31,14],[.37,16],[.43,18]], now:.43, p:18, pace:43, proj:null, x0:'13 Oct', x1:'20 Oct'};
// Variants whose samples are scaled so the chart's end point agrees with the number shown.
const wk = (p,pace=43) => ({...DW, p, pace, s:DW.s.map(([x,v])=>[x, v*p/18])});
const sc5 = (p,pace,proj=null) => ({...D5, p, pace, proj, s:D5.s.map(([x,v])=>[x, v*p/26])});
const DWf = {s:[[0,0],[.2,1],[.43,5]], now:.43, p:5, pace:43, proj:null, x0:'13 Oct', x1:'20 Oct'};

/* ============ devices ============ */
function phone(o){
  const t=o.t, w=o.w||402, h=o.h||874;
  return `<figure><div class="dev ${o.z||'z62'}" style="width:${w}px;height:${h}px;background:${o.bg||t.bg};color:${t.ink}">
    ${o.island===false?'':'<div class="island"></div>'}
    <div class="sb"><span>${o.time||'9:41'}</span>${statusIcons(t.ink)}</div>
    ${o.body}
    <div class="home" style="background:${t.ink}"></div></div>
    <figcaption>${o.cap||''}</figcaption></figure>`;
}
function ipad(o){
  const t=o.t, w=o.w||1210, h=o.h||834;
  return `<figure><div class="dev ipad ${o.z||'z54'}" style="width:${w}px;height:${h}px;background:${t.bg};color:${t.ink}">
    <div class="sb"><span>9:41&nbsp;&nbsp;Fri 23 Oct</span>${statusIcons(t.ink)}</div>
    ${o.body}<div class="home" style="background:${t.ink};width:300px"></div></div>
    <figcaption style="max-width:640px">${o.cap||''}</figcaption></figure>`;
}

/* ============ app pieces ============ */
function appHead(t, sel, o={}){
  const ac=acc(sel,t), tint=t.ink;
  const title = o.title || 'Cooldown';
  return `<div class="tool">${o.back?`<span style="margin-right:auto;display:flex;align-items:center;gap:2px;color:${ac};font-size:17px">${ico.back(ac)}Back</span>`:''}${o.tools===false?'':ico.cal(tint)+ico.gear(tint)+ico.more(tint)}</div>
  <div class="ltitle">${o.title?'':mark('claude',26,ACC.claude[t===LIGHT?'l':'d'])+mark('chatgpt',26,ACC.chatgpt[t===LIGHT?'l':'d'])}<span>${title}</span></div>
  ${o.tabs===false?'':tabs(t,sel)}`;
}
function tabs(t, sel){
  return `<div class="tabs" style="--line:${t.line};--acc:${acc(sel,t)}">${ORDER.map(k=>`<div class="tab ${k===sel?'on':''}" style="color:${k===sel?t.ink:t.sub}">${amark(k,20,t)}${ACCTS[k].n}</div>`).join('')}</div>`;
}
function foot(t, txt){ return `<div class="foot" style="color:${t.sub}">${txt||'Checked 3m ago'} ${ico.refresh(t.sub)}</div>`; }

/* 5-hour card. W = inner width. */
function card5(o){
  const t=o.t, ac=acc(o.a,t), W=o.W||338, d=o.d;
  const pc = d.p>=100 ? t.R : t.ink;
  let h=`<div class="card" style="background:${t.card}">
    <div class="ch"><span class="t" style="color:${t.txt}">${o.title||'5-hour window'}</span><span class="p" style="color:${o.dim?t.sub:pc}">${pctTxt(d.p)}</span>${o.exp?ico.up(t.sub):ico.down(t.sub)}</div>
    <div style="margin-top:14px">${bar({t,w:W,h:11,ac,p:d.p,pace:d.pace,dim:o.dim})}</div>`;
  if(o.exp) h+=`<div style="margin-top:14px">${chart({t,w:W,h:o.ch||150,ac,s:d.s,now:d.now,proj:d.proj,x0:d.x0,x1:d.x1,over:d.p>d.pace+3&&d.p<100,dim:o.dim})}</div>`;
  (o.lines||[]).forEach(l=>h+=`<div class="ln" style="color:${t.txt}">${l}</div>`);
  if(o.rin) h+=`<div class="rs" style="color:${t.txt}"><span>${o.rin}</span><span>${o.rat||''}</span></div>`;
  return h+`</div>`;
}
function cardWeek(o){
  const t=o.t, ac=acc(o.a,t), W=o.W||338, two=o.caps;
  const col = (name,d,w,rin) => `<div><div class="ch" style="font-size:16px"><span class="t" style="color:${t.txt}">${name}</span><span class="p" style="font-size:17px;color:${o.dim?t.sub:t.ink}">${pctTxt(d.p)}</span></div>
     <div style="margin-top:12px">${bar({t,w,h:9,ac,p:d.p,pace:d.pace,dim:o.dim})}</div>
     ${o.exp?`<div style="margin-top:12px">${chart({t,w,h:120,ac,s:d.s,now:d.now,proj:d.proj,x0:d.x0,x1:d.x1,over:d.p>d.pace+3&&d.p<100,dim:o.dim})}</div>`:''}
     <div class="ln" style="color:${t.txt};font-size:13.5px">${rin}</div></div>`;
  let h=`<div class="card" style="background:${t.card}"><div class="ch" style="margin-bottom:10px"><span class="t" style="color:${t.txt}">7-day window ${o.exp?'':''}</span>${o.exp?ico.up(t.sub):ico.down(t.sub)}</div>`;
  if(two) h+=`<div class="sub2">${col('All',o.d,(W-16)/2,o.rin||'Resets in 3d 1h')}${col(o.capName||'Opus',o.d2,(W-16)/2,o.rin||'Resets in 3d 1h')}</div>`;
  else h+=col('All',o.d,W,o.rin||'Resets in 3d 1h · Sun 9:30 pm');
  return h+`</div>`;
}
function cardCredits(t){
  return `<div class="card" style="background:${t.card};display:flex;align-items:center;gap:8px;font-size:16px"><span style="color:${t.txt};font-weight:600">Usage credits</span><span style="color:${t.txt}">$123.90 / $400.00</span><span style="margin-left:auto;font-weight:700">31%</span>${ico.info(t.sub)}</div>`;
}
function notice(t, title, body, btn, tone){
  const bg = tone==='err' ? rgba(t.R, t===LIGHT?.10:.16) : rgba(t.O, t===LIGHT?.12:.16);
  return `<div class="notice" style="background:${bg};color:${t.txt}"><b style="color:${t.ink}">${title}</b>${body}${btn?`<div><span class="btn" style="background:${t.ink};color:${t.bg}">${btn}</span></div>`:''}</div>`;
}

/* ============ widgets ============ */
const wbg = t => `background:${t.widget};color:${t.ink}`;
function wNumber(o){
  const t=o.t, ac=acc(o.a,t), d=o.d, dim=o.state==='signin'||o.state==='stale';
  const big = o.state==='signin' ? '—' : pctTxt(d.p);
  const bottom = o.state==='signin' ? `<span style="color:${t.O}">Sign in again</span>` : o.state==='stale' ? `<span style="color:${t.O}">as of 9:12 am</span>` : d.p>=100 ? `<span style="color:${t.R}">Limit · resets ${o.rat||'9:50 pm'}</span>` : (d.p>d.pace+3 ? `~ runs out ${d.run||'7:51 pm'}` : `Resets ${o.rat||'9:50 pm'}`);
  return `<div class="wg wsm" style="${wbg(t)}"><div style="display:flex;gap:6px;align-items:center;font-size:14px;color:${t.txt}">${amark(o.a,15,t)}${ACCTS[o.a].n} · 5h</div>
   <div style="font-size:${d.p>=100&&o.state!=='signin'?40:46}px;font-weight:700;line-height:1;margin:16px 0 14px;color:${dim?t.sub:sev(d.p,ac,t)}">${big}</div>
   ${bar({t,w:140,h:8,ac,p:o.state==='signin'?null:d.p,pace:d.pace,dim})}
   <div style="font-size:12.5px;color:${t.sub};margin-top:13px">${bottom}</div></div>`;
}
function wCountdown(o){
  const t=o.t, ac=acc(o.a,t);
  const st=o.state;
  const big = st==='signin' ? '—:—' : st==='max' ? '0:42:07' : '1:35:12';
  return `<div class="wg wsm" style="${wbg(t)}"><div style="font-size:14px;color:${t.txt}">5h reset</div>
   <div style="font-size:35px;font-weight:700;line-height:1.1;margin:12px 0 4px;font-variant-numeric:tabular-nums;color:${st==='signin'?t.sub:t.ink}">${big}</div>
   <div style="font-size:13.5px;color:${t.sub}">${st==='signin'?'Sign in again':st==='max'?'Limit · opens 9:50 pm':'at 9:50 pm'}</div>
   <div style="position:absolute;left:15px;bottom:15px;display:flex;gap:6px;align-items:center;font-size:13px;color:${t.txt}">${amark(o.a,15,t)}${ACCTS[o.a].n}</div></div>`;
}
function wRing1(o){
  const t=o.t, ac=acc(o.a,t), d=o.d, st=o.state, dim=st==='signin'||st==='stale';
  return `<div class="wg wsm" style="${wbg(t)};display:flex;flex-direction:column;align-items:center;padding-top:12px">
   ${ring({t,s:98,sw:8,ac,p:st==='signin'?null:d.p,pace:d.pace,dim,fs:23})}
   <div style="display:flex;gap:5px;align-items:center;font-size:13.5px;margin-top:9px;color:${t.txt}">${amark(o.a,14,t)}${ACCTS[o.a].n}</div>
   <div style="font-size:12px;color:${st==='stale'||st==='signin'?t.O:t.sub};margin-top:1px">${st==='signin'?'Sign in again':st==='stale'?'as of 9:12 am':d.p>=100?'resets 9:50 pm':'9:50 pm'}</div></div>`;
}
function wRing3(o){
  const t=o.t, ds=o.ds, st=o.state||[];
  return `<div class="wg wmd" style="${wbg(t)}"><div style="display:flex;justify-content:space-around;padding-top:4px">${ORDER.map((a,i)=>{
    const d=ds[i], s=st[i], dim=s==='signin'||s==='stale';
    return `<div style="display:flex;flex-direction:column;align-items:center;width:100px">${ring({t,s:80,sw:6.5,ac:acc(a,t),p:s==='signin'?null:d.p,pace:d.pace,dim,fs:19})}
     <div style="display:flex;gap:5px;align-items:center;font-size:13px;margin-top:7px;color:${t.txt}">${amark(a,13,t)}${ACCTS[a].n}</div>
     <div style="font-size:11.5px;color:${dim?t.O:t.sub}">${s==='signin'?'Sign in again':s==='stale'?'as of 9:12 am':d.at||'9:50 pm'}</div></div>`}).join('')}</div>
   </div>`;
}
function wStrip(o){
  const t=o.t, ac=acc(o.a,t), d=o.d, w=o.dw, st=o.state, dim=st==='signin'||st==='stale';
  return `<div class="wg wmd" style="${wbg(t)}">
   <div style="display:flex;align-items:center;gap:7px;font-size:15px;color:${t.txt}">${amark(o.a,16,t)}${ACCTS[o.a].n} · 5h<span style="margin-left:auto;font-size:34px;font-weight:700;line-height:1;color:${dim?t.sub:sev(d.p,ac,t)}">${st==='signin'?'—':pctTxt(d.p)}</span></div>
   <div style="margin-top:9px">${bar({t,w:332,h:8,ac,p:st==='signin'?null:d.p,pace:d.pace,dim})}</div>
   <div style="font-size:12.5px;color:${t.sub};margin-top:8px">${st==='signin'?`<span style="color:${t.O}">Sign in again</span>`:d.p>=100?`<span style="color:${t.R}">Limit reached</span> · resets in 42m · 9:50 pm`:d.p>d.pace+3?`~ runs out ${d.run||'7:51 pm'} · resets 9:50 pm`:'Resets in 1h 35m · 9:50 pm'}</div>
   <div style="display:flex;align-items:center;gap:10px;margin-top:12px;font-size:13px;color:${t.txt}"><span style="width:52px">Weekly</span>${bar({t,w:214,h:6,ac,p:st==='signin'?null:w.p,pace:w.pace,dim})}<span style="margin-left:auto;font-weight:600">${st==='signin'?'—':pctTxt(w.p)}</span></div>
   ${st==='stale'?`<div style="position:absolute;right:16px;bottom:10px;font-size:11px;color:${t.O}">as of 9:12 am</div>`:''}</div>`;
}
function wLarge(o){
  const t=o.t, ds=o.ds, ws=o.ws;
  const row=(a,i)=>{const ac=acc(a,t),d=ds[i],w=ws[i];return `<div style="margin-top:11px"><div style="display:flex;align-items:center;gap:6px;font-size:14px;color:${t.txt}">${amark(a,15,t)}${ACCTS[a].n}<span style="margin-left:auto;color:${t.sub};font-size:12px">5h resets ${d.at||'9:50 pm'}</span></div>
    <div style="display:flex;align-items:center;gap:10px;margin-top:5px"><span style="width:44px;font-size:12px;color:${t.sub}">5h</span>${bar({t,w:236,h:7,ac,p:d.p,pace:d.pace})}<span style="margin-left:auto;font-weight:700;font-size:14px;color:${sev(d.p,ac,t)}">${pctTxt(d.p)}</span></div>
    <div style="display:flex;align-items:center;gap:10px;margin-top:5px"><span style="width:44px;font-size:12px;color:${t.sub}">Weekly</span>${bar({t,w:236,h:5,ac,p:w.p,pace:w.pace})}<span style="margin-left:auto;font-size:13px;color:${t.txt}">${pctTxt(w.p)}</span></div></div>`};
  return `<div class="wg wlg" style="${wbg(t)}"><div style="display:flex;justify-content:space-around">${ORDER.map((a,i)=>`<div style="display:flex;flex-direction:column;align-items:center">${ring({t,s:62,sw:5.5,ac:acc(a,t),p:ds[i].p,pace:ds[i].pace,fs:15})}<div style="display:flex;gap:4px;align-items:center;font-size:12px;margin-top:5px;color:${t.txt}">${amark(a,12,t)}${ACCTS[a].n}</div></div>`).join('')}</div>
   ${ORDER.map(row).join('')}</div>`;
}

/* lock screen (system-tinted: white at varying opacity, no colour) */
const W1='rgba(255,255,255,1)', W3='rgba(255,255,255,.32)';
function lkCircle(o){
  const st=o.state, p=st==='signin'?null:o.p;
  return `<div style="width:72px;height:72px;border-radius:50%;background:rgba(255,255,255,.14);display:flex;align-items:center;justify-content:center">${ring({t:DARK,s:66,sw:5.5,ac:'#fff',p,pace:o.pace,mono:W1,track:W3,trackW:5.5,tick:'#fff',tc:'#fff',label:st==='signin'?'—':p>=100?'MAX':Math.floor(p),fs:20,dy:-3,sub:'5h'})}</div>`;
}
function lkRect(o){
  const st=o.state;
  return `<div class="lk" style="width:172px;height:76px;padding:5px 2px">
   <div style="display:flex;align-items:center;gap:5px;font-size:14px;font-weight:600">${amark(o.a,14,DARK,'#fff')}${ACCTS[o.a].n} · 5h<span style="margin-left:auto;font-size:17px;font-weight:700">${st==='signin'?'—':pctTxt(o.p)}</span></div>
   <div style="margin:8px 0 7px">${bar({t:DARK,w:168,h:6,ac:'#fff',p:st==='signin'?null:o.p,pace:o.pace,track:W3,tick:'#fff'}).replace(/background:#FF5252/g,'background:#fff;opacity:.6').replace(/background:#(FDD663|FFA726)/g,'background:#fff')}</div>
   <div style="font-size:13px;opacity:.85">${st==='signin'?'Sign in again':o.p>=100?'Limit · resets 9:50 pm':'Resets in 1h 35m'}</div></div>`;
}

/* Live Activity on the lock screen — the "Huge number" Duet panel, ported */
function laRow(t,a,d,o={}){
  const ac=acc(a,t), dim=o.stale, big=o.signin?'—':pctTxt(d.p);
  const line = o.signin ? `<span style="color:${t.O}">Sign-in stopped working · open Cooldown</span>` : d.p>=100 ? `<span style="color:${t.R}">Limit reached</span> · resets in <b style="font-variant-numeric:tabular-nums">0:42:07</b>` : d.p>d.pace+3 ? `~ runs out ${d.run||'7:51 pm'} · resets in <b style="font-variant-numeric:tabular-nums">${o.cd||'2:59:40'}</b>` : `Resets in <b style="font-weight:500;font-variant-numeric:tabular-nums">${o.cd||'1:35:12'}</b> · ${o.at||'9:50 pm'}`;
  return `<div style="display:flex;align-items:center;gap:12px"><div style="flex:1;min-width:0">
     <div style="display:flex;align-items:center;gap:6px;font-size:15px;color:${t.ink}">${amark(a,15,t)}${ACCTS[a].n} · 5h</div>
     <div style="margin:8px 0 7px">${bar({t,w:o.bw||214,h:8,ac,p:o.signin?null:d.p,pace:d.pace,dim})}</div>
     <div style="font-size:12.5px;color:${t.txt}">${line}</div></div>
     <div style="font-size:${d.p>=100&&!o.signin?36:(o.fs||44)}px;font-weight:600;letter-spacing:-.5px;color:${dim||o.signin?t.sub:sev(d.p,ac,t)};font-variant-numeric:tabular-nums">${big}</div></div>`;
}
/* Duet with Weekly (Q2, Robin 23 Oct): Android's expanded Duet shows Weekly for both accounts; the lock screen
   cannot expand and caps a Live Activity at 160 pt, so each row is compressed: countdown (or "runs out") on the label
   line, the 5h bar, then a thin Weekly line. The page measures each panel and prints its height. */
function laRowW(t,a,d,w,o={}){
  const ac=acc(a,t), dim=o.stale;
  const tail = o.signin ? `<span style="color:${t.O}">sign in again</span>` : o.stale ? `<span style="color:${t.O}">as of 2:10 pm</span>` : d.p>=100 ? `<span style="color:${t.R}">limit</span> · <b style="font-weight:500;font-variant-numeric:tabular-nums">0:42:07</b>` : d.p>d.pace+3 ? `runs out ${d.run||'7:51 pm'}` : `<b style="font-weight:500;font-variant-numeric:tabular-nums">${o.cd||'1:35:12'}</b>`;
  return `<div style="display:flex;align-items:center;gap:10px"><div style="flex:1;min-width:0">
     <div style="display:flex;align-items:center;gap:6px;font-size:14px;color:${t.ink};white-space:nowrap">${amark(a,14,t)}${ACCTS[a].n} · 5h<span style="color:${t.txt};font-size:12.5px">· ${tail}</span></div>
     <div style="margin:7px 0 6px">${bar({t,w:o.bw||222,h:7,ac,p:o.signin?null:d.p,pace:d.pace,dim})}</div>
     <div style="display:flex;align-items:center;gap:7px;font-size:11.5px;color:${t.txt}"><span style="width:40px">Weekly</span>${bar({t,w:146,h:4,ac,p:o.signin?null:w.p,pace:w.pace,dim})}<span style="margin-left:auto;font-variant-numeric:tabular-nums">${o.signin?'—':pctTxt(w.p)}</span></div></div>
     <div style="width:${o.nw||96}px;text-align:right;font-size:${d.p>=100&&!o.signin?34:40}px;font-weight:600;letter-spacing:-.5px;color:${dim||o.signin?t.sub:sev(d.p,ac,t)};font-variant-numeric:tabular-nums">${o.signin?'—':pctTxt(d.p)}</div></div>`;
}
function liveDuet(o){
  const t=DARK;
  const rows=o.rows.map((r,i)=>(i?'<div style="height:10px"></div>':'')+laRowW(t,r.a,r.d,r.w,r)).join('');
  return `<div class="la" style="width:370px;border-radius:24px;padding:12px 16px 11px;background:rgba(28,28,30,.86);color:${t.ink}">${rows}
</div>`;
}
function liveActivity(o){
  const t=DARK;
  const rows=o.rows.map((r,i)=>(i?'<div style="height:12px"></div>':'')+laRow(t,r.a,r.d,r)).join('');
  return `<div class="la" style="width:370px;border-radius:24px;padding:14px 16px 12px;background:rgba(28,28,30,.86);color:${t.ink}">${rows}${o.week?`<div style="display:flex;align-items:center;gap:8px;font-size:12.5px;color:${t.txt};margin-top:9px"><span style="width:48px">Weekly</span>${bar({t,w:200,h:5,ac:acc(o.rows[0].a,t),p:o.week.p,pace:o.week.pace})}<span style="margin-left:auto">${pctTxt(o.week.p)} · resets Sun</span></div>`:''}
   ${o.stale?`<div style="font-size:11.5px;color:${t.O};margin-top:8px">as of 2:10 pm — open Cooldown to refresh</div>`:''}</div>`;
}
function lockScreen(o){
  return `<figure><div class="dev z78" style="width:402px;height:${o.h||560}px;color:#fff;background:linear-gradient(170deg,#4e5487,#8a6a92 55%,#c58a6b)">
    <div class="island"></div><div class="sb"><span></span>${statusIcons('#fff')}</div>
    <div style="text-align:center;font-size:20px;font-weight:600;opacity:.9;margin-top:4px">${o.inline||'Friday 23 October'}</div>
    <div style="text-align:center;font-size:92px;font-weight:700;line-height:1.05;opacity:.92;letter-spacing:-2px">9:41</div>
    <div style="display:flex;gap:12px;justify-content:center;align-items:center;margin-top:10px">${o.widgets||''}</div>
    <div style="position:absolute;left:16px;right:16px;bottom:${o.laBottom||30}px">${o.la||''}</div></div>
    <figcaption>${o.cap||''}</figcaption></figure>`;
}

/* Dynamic Island */
function island(kind,o={}){
  const t=DARK, ac=acc(o.a||'teams',t), d=o.d||D5b;
  if(kind==='compact') return `<div style="width:300px;height:37px;border-radius:20px;background:#000;display:flex;align-items:center;padding:0 14px;color:#fff;font-weight:600;font-size:15px">${amark(o.a||'teams',16,t)}<span style="margin-left:6px;color:${sev(d.p,ac,t)}">${pctTxt(d.p)}</span><span style="margin-left:auto;font-variant-numeric:tabular-nums;color:${d.p>=100?t.R:'#fff'}">${o.cd||'1:35'}</span></div>`;
  if(kind==='minimal') return `<div style="width:37px;height:37px;border-radius:50%;background:#000;display:flex;align-items:center;justify-content:center">${ring({t,s:28,sw:3.4,ac,p:d.p,pace:d.pace,label:''})}</div>`;
  return `<div style="width:384px;border-radius:44px;background:#000;color:#fff;padding:16px 24px 18px">
     <div style="display:flex;align-items:center;gap:7px;font-size:15px">${amark(o.a||'teams',18,t)}${ACCTS[o.a||'teams'].n} · 5h<span style="margin-left:auto;font-size:34px;font-weight:600;color:${sev(d.p,ac,t)}">${pctTxt(d.p)}</span></div>
     <div style="margin:8px 0 8px">${bar({t,w:336,h:8,ac,p:d.p,pace:d.pace})}</div>
     <div style="font-size:13px;color:${t.txt}">${d.p>d.pace+3?'~ runs out 7:51 pm · ':''}Resets in <b style="font-weight:500;font-variant-numeric:tabular-nums">1:35:12</b> · 9:50 pm</div></div>`;
}

/* ============ page ============ */
const T=DARK, L=LIGHT;
const S = [];
const add = s => S.push(s);
const row = (...xs) => `<div class="row">${xs.join('')}</div>`;
const fig = (inner,cap,extra='') => `<figure${extra}>${inner}<figcaption>${cap}</figcaption></figure>`;

add(`<h1>Cooldown for iPhone and iPad — wireframes, rev B</h1>
<p class="note">CCRM-94 (iOS App) · <code>docs/ios/PLAN.md</code> step 4 · 23 Oct 2026 · for Robin's approval before any UI is built (CLAUDE.md §2).
Drawn at true iOS points (iPhone 17: 402×874 pt; iPad Pro 11": 1210×834 pt landscape), then scaled down.</p>
<p><b>What this is.</b> A port, not a redesign. The app screens reproduce the Android v1.7 main screen and history as shipped —
the 5-hour card keeps its bar with the pace tick, the full chart (80/90/100 guides, even-pace line, samples, "now", dashed
projection, time axis), the pace lines and both reset lines; nothing on a card is removed. The widgets reuse the v1.8 faces
(Number, Countdown, Ring, Strip). The Live Activity is the Duet "Huge number" panel. What changes is only what iOS forces:
there is <b>no always-on notification</b> and <b>no background polling</b>, so the pinned panel becomes a Live Activity and
the lock-screen widgets, the Quick Settings tile becomes a Control Center control, and every surface says how old its numbers are.</p>
<div class="q"><b>Four decisions</b> (asked one at a time):

<br><b>Q1 · §1</b> answered: tabs. <b>Q2 · §6</b> answered: Weekly with 5h; two accounts when they fit — they do (§6).

<br><b>Q3 · §4</b> answered: offer Large. <b>Q4 · §2</b> answered: sidebar, using the height — one pinned account on top, the selected one below.</div>
<p class="note">Sample accounts: <b>Teams</b> and <b>Product</b> (Claude), <b>Personal</b> (ChatGPT). Colours: each account's provider accent;
bars and rings turn yellow above 80%, orange above 90%, red at 100%; above pace (more than 3 points over the even-pace line) the overshoot
is red from the pace tick. Wording: "5-hour window" / "7-day window" in the app's cards, "5h" / "Weekly" everywhere tight.</p>`);

/* ---- 1 · iPhone app ---- */
const cards5 = (t,a,d,lines,o={}) => card5({t,a,d,exp:true,lines,rin:o.rin||'Resets in 1h 35m',rat:o.rat||'Resets at Thu 9:50 pm',dim:o.dim});
const mainNormal = t => appHead(t,'teams') + `<div class="scroll">` +
  cards5(t,'teams',D5b,['On even pace','At this pace: ~83% when the window resets · 17.7%/h '+ico.info(t.sub)]) +
  cardWeek({t,a:'teams',caps:true,d:DW,d2:DWf,capName:'Opus'}) + cardCredits(t) + foot(t) + `</div>`;
add(`<h2>1 · iPhone — the main screen</h2>
<p>Same information architecture as Android: a large title with both marks, the account tabs (swipe or tap), then the cards in the
user's order (the Android layout sheet ports as-is), then "Checked … ago" with a refresh button; <b>pull to refresh</b> is the iOS addition.
The toolbar holds History (calendar), Settings (gear) and the "…" menu (Layout, Refresh, Share card).</p>
<div class="q"><b>Q1 — answered: tabs</b>, one account per screen, exactly like Android.</div>`);
add(row(
  phone({t:T,body:mainNormal(T),cap:'<b>Dark, on pace.</b> 5h expanded with the full chart; 7-day collapsed to its two halves (All and a model cap, as on Android); credits.'}),
  phone({t:T,body:appHead(T,'teams')+`<div class="scroll">`+card5({t:T,a:'teams',d:DUP,exp:true,lines:[`<span style="color:${T.R}">22% above even pace</span>`,'At this pace: runs out ~7:51 pm · 24.0%/h '+ico.info(T.sub)],rin:'Resets in 2h 59m',rat:'Resets at Thu 9:50 pm'})+cardWeek({t:T,a:'teams',caps:true,d:DW,d2:DWf})+foot(T)+`</div>`,cap:'<b>Above pace.</b> The overshoot is red from the pace tick on the bar; the chart line turns red; the projection says when it runs out.'}),
  phone({t:T,body:appHead(T,'teams')+`<div class="scroll">`+card5({t:T,a:'teams',d:DMAX,exp:true,lines:[`<span style="color:${T.R}">Limit reached</span>`,'Opens again when the window resets'],rin:'Resets in 42m',rat:'Resets at Thu 9:50 pm'})+cardWeek({t:T,a:'teams',caps:true,d:wk(64),d2:DWf})+foot(T)+`</div>`,cap:'<b>At 100%.</b> Number, bar and chart point go red; no projection.'})
));
add(row(
  phone({t:T,body:appHead(T,'personal')+`<div class="scroll">`+card5({t:T,a:'personal',d:{...D5,p:26},exp:true,lines:['42% below even pace','At this pace: ~42% when the window resets · 5.6%/h '+ico.info(T.sub)],rin:'Resets in 1h 35m',rat:'Resets at Thu 9:50 pm'})+cardWeek({t:T,a:'personal',d:wk(9)})+`<div class="card" style="background:${T.card};display:flex;font-size:16px"><span style="color:${T.txt}">Credits</span><span style="margin-left:auto;font-weight:600">$4.20 balance</span></div>`+foot(T)+`</div>`,cap:'<b>A ChatGPT account.</b> Green accent from the mark; 7-day has no model caps; credits read as a balance (CCRM-54 (ChatGPT Account)).'}),
  phone({t:T,body:appHead(T,'product')+`<div class="scroll">`+notice(T,'Product: Sign-in stopped working','The saved sign-in failed and couldn’t renew. The numbers below are the last ones that worked.','Sign in again','err')+card5({t:T,a:'product',d:D5,exp:false,rin:'Resets in 1h 35m',rat:'as of 2:10 pm',dim:true})+cardWeek({t:T,a:'product',d:DW,dim:true})+foot(T,'Last success 2:10 pm')+`</div>`,cap:'<b>Sign-in needed / stale.</b> The condition sits above the cards (the iOS home of Android’s notification strips); the last good numbers stay, dimmed. Stale (no success for 6 h) uses the same notice in orange: “Stale — nothing since 3:12 am”.'}),
  phone({t:T,body:appHead(T,'product')+`<div class="scroll">`+notice(T,'Usage isn’t available on the Free plan','Anthropic only reports usage windows for paid plans. This account stays signed in and will show numbers if the plan changes.',null,'warn')+foot(T)+`</div>`,cap:'<b>Plan refusal.</b> The Free plan’s 403 (spec core §10.1): one quiet notice, no cards, no error tone.'})
));
add(row(
  phone({t:T,body:`<div class="tool"></div><div class="ltitle">Cooldown</div><div style="padding:120px 32px 0;text-align:center"><div style="display:flex;gap:14px;justify-content:center">${mark('claude',54,ACC.claude.d)}${mark('chatgpt',54,ACC.chatgpt.d)}</div><div style="font-size:22px;font-weight:700;margin-top:22px">Track your usage windows</div><div style="color:${T.txt};margin-top:8px;font-size:15.5px;line-height:1.4">See how much of your 5-hour and weekly limits you’ve used, and when they reset — for Claude and ChatGPT.</div><div style="margin-top:28px;display:flex;flex-direction:column;gap:12px;align-items:center"><span class="btn" style="background:${ACC.claude.d};color:#000;width:260px;padding:12px">Add a Claude account</span><span class="btn" style="background:${ACC.chatgpt.d};color:#000;width:260px;padding:12px">Add a ChatGPT account</span></div><div style="color:${T.sub};font-size:12.5px;margin-top:40px">Not affiliated with Anthropic or OpenAI.</div></div>`,cap:'<b>Empty — first launch.</b> No accounts yet; the two add buttons open the sign-in sheets (§3).'}),
  phone({t:L,body:mainNormal(L),cap:'<b>Light.</b> Same screen in light mode; the dynamic accent option follows the iOS accent.'}),
  phone({t:T,w:375,h:812,body:appHead(T,'teams')+`<div class="scroll">`+card5({t:T,a:'teams',d:D5b,exp:true,W:311,lines:['On even pace','At this pace: ~83% when the window resets · 17.7%/h '+ico.info(T.sub)],rin:'Resets in 1h 35m',rat:'Resets at Thu 9:50 pm'})+foot(T)+`</div>`,cap:'<b>Narrowest: 375 pt</b> (iPhone SE, 13 mini). Tabs keep mark + name at three accounts; with four or more they scroll sideways. The chart and both reset lines still fit at 311 pt.'})
));

/* ---- 2 · iPad ---- */
/* Q4 (Robin, 23 Oct): use the iPad's height — one account is pinned to the top half, the selected one fills the
   bottom half. Nothing pinned yet means the first account in your order is pinned. */
const pin = (c,s=16) => `<svg width="${s}" height="${s}" viewBox="0 0 24 24" style="flex:none;display:block"><path d="M15 3l6 6-3 1-4 4 1 5-2 2-4-4-5 5-1-1 5-5-4-4 2-2 5 1 4-4z" fill="${c}"/></svg>`;
const sideRow=(t,a,d,sel,pinned)=>`<div style="display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:10px;margin:0 10px 2px;${sel?`background:${rgba(acc(a,t),.18)}`:''}">${amark(a,22,t)}<div style="flex:1"><div style="font-size:16px;font-weight:${sel||pinned?600:400};display:flex;align-items:center;gap:6px">${ACCTS[a].n} <span class="chip" style="background:${t.fill};color:${t.sub}">${ACCTS[a].plan}</span>${pinned?pin(t.sub,14):''}</div><div style="margin-top:6px">${bar({t,w:160,h:5,ac:acc(a,t),p:d.p,pace:d.pace})}</div></div><span style="font-weight:700;color:${sev(d.p,acc(a,t),t)}">${pctTxt(d.p)}</span></div>`;
const credLine = (t,a) => a==='personal' ? `<span style="color:${t.txt};font-size:14px">Credits $4.20 balance</span>` : `<span style="color:${t.txt};font-size:14px">Usage credits $123.90 / $400.00 · <b>31%</b></span>`;
function half(t,a,d5,dw,dw2,o){
  const W=o.W, ch=o.ch;
  const head=`<div style="display:flex;align-items:center;gap:9px;margin:0 2px 10px">${amark(a,22,t)}<span style="font-size:21px;font-weight:700">${ACCTS[a].n}</span><span class="chip" style="background:${t.fill};color:${t.sub};font-size:12px">${ACCTS[a].plan}</span>${o.pinned?`<span style="display:flex;align-items:center;gap:4px;color:${t.sub};font-size:13px;margin-left:4px">${pin(t.sub,14)}Pinned</span>`:''}<span style="margin-left:auto">${credLine(t,a)}</span></div>`;
  const over = d5.p>d5.pace+3 && d5.p<100;
  const l5 = over ? [`<span style="color:${t.R}">${Math.round(d5.p-d5.pace)}% above even pace</span>`,'At this pace: runs out ~7:51 pm · 24.0%/h '+ico.info(t.sub)] : [d5.p>=d5.pace-3?'On even pace':`${Math.round(d5.pace-d5.p)}% below even pace`,`At this pace: ~${d5.proj}% when the window resets · 17.7%/h `+ico.info(t.sub)];
  return `<div>${head}<div style="display:grid;grid-template-columns:1fr 1fr;gap:14px">
    <div>${card5({t,a,d:d5,exp:true,W,ch,lines:l5,rin:over?'Resets in 2h 59m':'Resets in 1h 35m',rat:'Resets at Thu 9:50 pm'})}</div>
    <div>${cardWeek({t,a,caps:!!dw2,exp:true,d:dw,d2:dw2,W,rin:'Resets in 3d 1h'})}</div></div></div>`;
}
const ipadLand = (t,sel) => `<div style="display:flex;height:806px">
  <div style="width:300px;border-right:1px solid ${t.line};background:${t===LIGHT?'#F7F7F9':'#161617'};position:relative"><div class="tool" style="justify-content:space-between;padding:4px 18px">${ico.side(t.ink)}${ico.plus(t.ink)}</div>
  <div class="ltitle" style="font-size:30px">${mark('claude',22,acc('teams',t))}${mark('chatgpt',22,acc('personal',t))}Cooldown</div>
  <div style="margin-top:10px">${sideRow(t,'teams',D5b,false,true)}${sideRow(t,'product',DUP,sel==='product')}${sideRow(t,'personal',sc5(12,30),sel==='personal')}</div>
  <div style="color:${t.sub};font-size:12.5px;padding:14px 22px;line-height:1.4">Touch and hold an account to pin it to the top.</div>
  <div style="position:absolute;bottom:22px;left:22px;color:${t.sub};font-size:14px;display:flex;gap:8px">Checked 3m ago ${ico.refresh(t.sub)}</div></div>
  <div style="flex:1;padding:0 20px;overflow:hidden">
   <div class="tool" style="padding:4px 0;height:40px">${ico.cal(t.ink)}${ico.gear(t.ink)}${ico.more(t.ink)}</div>
   ${half(t,'teams',D5b,DW,DWf,{W:396,ch:112,pinned:true})}
   <div style="height:1px;background:${t.line};margin:2px 0 12px"></div>
   ${sel==='product'?half(t,'product',DUP,wk(64),null,{W:396,ch:112}):half(t,'personal',sc5(12,30,30),wk(9),null,{W:396,ch:112})}
  </div></div>`;
const ipadPort = t => `<div class="tool" style="padding:4px 22px 0">${ico.cal(t.ink)}${ico.gear(t.ink)}${ico.more(t.ink)}</div>
  <div class="ltitle" style="padding:0 22px">${mark('claude',26,acc('teams',t))}${mark('chatgpt',26,acc('personal',t))}<span>Cooldown</span></div>
  <div style="padding:10px 22px 0">${half(t,'teams',D5b,DW,DWf,{W:356,ch:200,pinned:true})}</div>
  <div style="margin:4px 0 0">${(()=>{const sel='product';return `<div class="tabs" style="--line:${t.line};--acc:${acc(sel,t)};padding:0 22px">${['product','personal'].map(k=>`<div class="tab ${k===sel?'on':''}" style="color:${k===sel?t.ink:t.sub}">${amark(k,20,t)}${ACCTS[k].n}</div>`).join('')}</div>`})()}</div>
  <div style="padding:14px 22px 0">${half(t,'product',DUP,wk(64),null,{W:356,ch:200})}${foot(t)}</div>`;
add(`<h2>2 · iPad — pinned on top, selected below</h2>
<p>Revised for Q4 (your idea): the iPad's height is used for <b>two accounts at once</b>. One account is <b>pinned</b> to the top half; the
account you select fills the bottom half. Each half is the account's 5-hour and 7-day cards side by side with their full charts, and its
credits on the header line. Until you pin one, the first account in your order is pinned. With a single account it fills the whole pane.
<b>Landscape:</b> the sidebar lists every account (pinned one marked) and selects the bottom half. <b>Portrait:</b> no sidebar — the pinned
account on top, then tabs for the others, then the selected one. Split View narrower than ~700 pt uses the iPhone layout.</p>
<div class="q"><b>Q4 — answered:</b> sidebar, and use the vertical space. Drawn here as one pinned + one selected. The other way to fill the
space would be every account stacked in one scrolling pane (the sidebar then jumps to an account); pinning keeps the account you watch
most always in view, so it's drawn as the recommendation.</div>`);
add(row(ipad({t:T,body:ipadLand(T,'product'),cap:'<b>Landscape, dark.</b> Teams pinned on top; Product (above pace) selected below. Pencil and ⌘R (refresh), ⌘1–9 (accounts) come free.'})));
add(row(ipad({t:T,w:834,h:1210,z:'z48',body:ipadPort(T),cap:'<b>Portrait, dark.</b> Teams pinned on top with taller charts; tabs pick the bottom account.'}),
  ipad({t:L,body:ipadLand(L,'personal'),z:'z48',cap:'<b>Landscape, light.</b> Personal (ChatGPT) selected below.'})));

/* ---- 3 · sign-in & settings ---- */
const sheetWrap = (t,inner,under,top=360) => `${under}<div style="position:absolute;inset:0;background:rgba(0,0,0,.5)"></div><div class="sheet" style="background:${t.sheet};top:${top}px"><div class="grab" style="background:${t.sub}"></div>${inner}</div>`;
const under = t => mainNormal(t);
const addSheet = t => `<div style="font-size:20px;font-weight:700;text-align:center;margin-bottom:22px">Add account</div>
  ${[['claude','Claude','Sign in with your Claude account in the browser, then paste the code it shows.'],['chatgpt','ChatGPT','Sign in with a code on any device — no password in this app.']].map(([p,n,s])=>`<div style="display:flex;gap:14px;align-items:center;background:${t.fill};border-radius:14px;padding:14px;margin-bottom:12px">${mark(p,32,ACC[p][t===LIGHT?'l':'d'])}<div style="flex:1"><div style="font-weight:600;font-size:17px">${n}</div><div style="color:${t.txt};font-size:13.5px;line-height:1.35">${s}</div></div>${ico.chev(t.sub)}</div>`).join('')}
  <div style="color:${t.sub};font-size:12.5px;margin-top:14px;line-height:1.4">Cooldown reads the same usage numbers the Claude and ChatGPT apps show. Tokens stay in this device’s Keychain. Not affiliated with Anthropic or OpenAI.</div>`;
const claudeSheet = t => `<div style="font-size:20px;font-weight:700;margin-bottom:8px">Sign in to Claude</div>
  <div style="color:${t.txt};font-size:15px;line-height:1.4">1. Open the sign-in page and approve Cooldown.<br>2. Copy the code Claude shows you and paste it here.</div>
  <div class="btn" style="background:${ACC.claude.d};color:#000;margin:16px 0 18px;padding:10px 16px">Open claude.com sign-in</div>
  <div style="background:${t.fill};border-radius:12px;padding:13px 14px;color:${t.sub};font-size:16px">Paste the code</div>
  <div style="display:flex;gap:10px;margin-top:16px"><span class="btn" style="background:${t.fill};color:${t.ink};flex:1;text-align:center;padding:11px">Cancel</span><span class="btn" style="background:${t.ink};color:${t.bg};flex:1;text-align:center;padding:11px;opacity:.45">Sign in</span></div>
  <div style="color:${t.sub};font-size:12.5px;margin-top:14px">Name this account after signing in (e.g. “Teams”); the plan tag comes from Anthropic.</div>`;
const dcSheet = (t,stage) => {
  const code = stage==='done'?'':`<div style="font:600 34px ui-monospace,Menlo,monospace;letter-spacing:3px;text-align:center;margin:18px 0 6px;${stage==='expired'?'opacity:.35;text-decoration:line-through':''}">QK7M-4RDX</div>`;
  const body = stage==='waiting' ? `<div style="text-align:center;color:${t.sub};font-size:14px;font-variant-numeric:tabular-nums">Expires in 14:32</div><div class="btn" style="display:block;text-align:center;background:${ACC.chatgpt.d};color:#000;margin:18px 0 10px;padding:11px">Open auth.openai.com/codex/device</div><div style="text-align:center;color:${t.sub};font-size:14px">Waiting for you to finish…</div>`
    : stage==='expired' ? `<div style="text-align:center;color:${t.O};font-size:15px;margin-top:6px">That code expired before it was used. Codes last 15 minutes.</div><div class="btn" style="display:block;text-align:center;background:${t.ink};color:${t.bg};margin:18px 0 0;padding:11px">Get a new code</div>`
    : `<div style="display:flex;justify-content:center;margin:22px 0 8px">${mark('chatgpt',48,ACC.chatgpt.d)}</div><div style="text-align:center;font-size:18px;font-weight:600">Signed in</div><div style="text-align:center;color:${t.txt};font-size:14.5px;margin-top:4px">Personal · Plus</div><div class="btn" style="display:block;text-align:center;background:${t.ink};color:${t.bg};margin:22px 0 0;padding:11px">Done</div>`;
  return `<div style="font-size:20px;font-weight:700;margin-bottom:6px">Sign in to ChatGPT</div><div style="color:${t.txt};font-size:14.5px;line-height:1.4">Open the page in any browser — it can be a different device — sign in to ChatGPT, and enter this code.</div>${code}${body}`;
};
const settings = t => `<div class="tool" style="justify-content:flex-start;padding-left:12px;color:${acc('teams',t)};font-size:17px"><span style="margin-left:auto;margin-right:8px;font-weight:600;color:${acc('teams',t)}">Done</span></div><div class="ltitle" style="font-size:32px">Settings</div>
  <div class="ghead" style="color:${t.sub};margin-top:10px">Accounts</div><div class="group" style="background:${t.group}">
   ${ORDER.map(a=>`<div class="li" style="--line:${t.line}">${ico.grip(t.sub)}${amark(a,20,t)}${ACCTS[a].n}<span class="chip" style="background:${t.fill};color:${t.sub}">${ACCTS[a].plan}</span><span class="v" style="color:${a==='product'?t.O:t.sub};font-size:14px">${a==='product'?'Sign in again':'Signed in'}</span>${ico.chev(t.sub)}</div>`).join('')}
   <div class="li" style="--line:${t.line};color:${acc('teams',t)}">${ico.plus(acc('teams',t))}Add account</div></div>
  <div class="ghead" style="color:${t.sub}">Reset alerts</div><div class="group" style="background:${t.group}">
   ${[['teams','5h','If busy'],['teams','Weekly','Off'],['personal','5h','Always']].map(([a,w,m])=>`<div class="li" style="--line:${t.line}">${amark(a,18,t)}${ACCTS[a].n} · ${w}<span class="v seg" style="background:${t.fill}">${['Off','If busy','Always'].map(x=>`<span style="${x===m?`background:${t===LIGHT?'#fff':'#636366'};font-weight:600`:''}">${x}</span>`).join('')}</span></div>`).join('')}
   <div class="li" style="--line:${t.line};color:${t.sub};font-size:13px;line-height:1.35">“If busy” only alerts when the window that closed peaked at 80% or more.</div></div>
  <div class="ghead" style="color:${t.sub}">Lock Screen</div><div class="group" style="background:${t.group}">
   <div class="li" style="--line:${t.line}">Live Activity<span class="v" style="width:51px;height:31px;border-radius:16px;background:#34C759;position:relative"><i style="position:absolute;right:2px;top:2px;width:27px;height:27px;border-radius:50%;background:#fff"></i></span></div>
   <div class="li" style="--line:${t.line}">Shows<span class="v" style="color:${t.sub}">Teams, Personal</span>${ico.chev(t.sub)}</div></div>
  <div class="ghead" style="color:${t.sub}">Appearance</div><div class="group" style="background:${t.group}"><div class="li" style="--line:${t.line}">Theme<span class="v" style="color:${t.sub}">System</span>${ico.chev(t.sub)}</div><div class="li" style="--line:${t.line}">Density · Chart height · Layout<span class="v"></span>${ico.chev(t.sub)}</div></div>`;
add(`<h2>3 · Sign-in, Settings and History</h2>
<p>Ported screen for screen. The sign-in flows are the Android ones: Claude by browser approval and a pasted code (PKCE), ChatGPT by the
OpenAI device code. The ChatGPT sheet keeps all five states — waiting (live m:ss countdown), expired, denied, unavailable and done;
three are drawn, denied and unavailable reuse the expired layout with their own sentence. Settings is an iOS grouped list with the same
sections as Android, plus <b>Lock Screen</b> (the Live Activity switch). There is <b>no polling interval</b> on iOS: the app fetches when it opens
and widgets refresh on iOS's own budget (spec mobile §1), so the setting would promise something iOS won't do.</p>`);
add(row(
  phone({t:T,body:sheetWrap(T,addSheet(T),under(T),430),cap:'<b>Add account.</b> A sheet over the main screen. Two services; Gemini stays out (CCRM-55 (Antigravity Account) dropped).'}),
  phone({t:T,body:sheetWrap(T,claudeSheet(T),under(T)),cap:'<b>Claude sign-in.</b> Opens Safari for approval, then paste. “Sign in” enables once a code is pasted.'}),
  phone({t:T,body:sheetWrap(T,dcSheet(T,'waiting'),under(T),340),cap:'<b>ChatGPT — waiting.</b> Code, live countdown, the link (opens in Safari; any device works).'})
));
add(row(
  phone({t:T,body:sheetWrap(T,dcSheet(T,'expired'),under(T),380),cap:'<b>ChatGPT — expired.</b> Denied: “That sign-in didn’t go through. Start again with a new code.” Unavailable: OpenAI has code sign-in switched off, with the ChatGPT security-settings link.'}),
  phone({t:T,body:sheetWrap(T,dcSheet(T,'done'),under(T),380),cap:'<b>ChatGPT — done.</b> Named from the account, editable in Settings.'}),
  phone({t:T,body:settings(T),cap:'<b>Settings.</b> Accounts reorder by drag (same order everywhere: tabs, sidebar, widgets, Live Activity). Reset alerts per account per window, Off / If busy / Always.'})
));
const hist = t => { const rows=[['Thu 4:50 pm',39,'now'],['Thu 5:00 am',4],['Wed 5:20 pm',100],['Wed 12:00 pm',5],['Wed 4:59 am',25],['Tue 6:40 pm',66],['Tue 4:59 am',7],['Mon 8:59 pm',26],['Mon 4:00 pm',100],['Mon 10:59 am',95],['Mon 5:00 am',4]];
  return appHead(t,'teams',{title:'Usage history',tools:false,back:true}) + `<div class="scroll"><div style="display:flex;gap:10px;margin-bottom:14px"><span class="seg" style="background:${t.fill};font-size:15px"><span style="background:#636366;font-weight:600">5-hour</span><span>7-day</span></span></div>
  <div class="card" style="background:${t.card}"><div style="display:flex;align-items:center">${ico.left(t.ink)}<div style="flex:1;text-align:center"><div style="font-weight:600;font-size:17px">This week</div><div style="color:${t.txt};font-size:14px">20–26 Oct</div></div>${ico.right(t.sub)}</div><div style="text-align:center;color:${t.txt};font-size:14px;margin:8px 0 12px">11 sessions · 2 maxed out</div>
  ${rows.map(([d,p,n])=>`<div style="display:flex;align-items:center;gap:10px;margin:9px 0"><div style="width:104px;font-size:13.5px;color:${t.txt}">${d}${n?`<div style="color:${acc('teams',t)};font-weight:600;font-size:12.5px">now</div>`:''}</div>${bar({t,w:170,h:11,ac:acc('teams',t),p})}<span style="margin-left:auto;font-size:13.5px;color:${p>=100?t.R:t.txt}">${p}%</span></div>`).join('')}</div>
  <div style="color:${t.txt};font-size:13.5px;line-height:1.4">History is recorded as each window closes, so it fills in over time. Peak %s are sampled when the app checks.</div></div>`; };
add(row(phone({t:T,body:hist(T),cap:'<b>History.</b> Android’s screen as shipped: per account, 5-hour / 7-day, week by week. One honest change in the footnote: on iOS peaks are sampled “when the app checks”, not at a polling interval.'})));

/* ---- 4 · home-screen widgets ---- */
const sd = D5b, sw = {p:18,pace:43};
add(`<h2>4 · Home-screen widgets (iPhone and iPad)</h2>
<p>The v1.8 faces in iOS's sizes. Each widget is configured by long-press → Edit Widget: which account (Ring medium and Large show all,
in the app's order) and, for Number and Strip, which window. Tapping opens the app on that account. <b>Freshness:</b> iOS decides when a
widget reloads — typically every 15–60 minutes, more often while the app is in use — so every face that shows a number also shows
<b>“as of”</b> once it's older than 30 minutes, and goes orange (the stale state) after 6 hours. Countdowns and reset clocks always
tick live because they're times, not fetched values.</p>`);
add(`<h3>Small (170 pt) — Number, Countdown, Ring</h3>`+row(
  fig(wNumber({t:T,a:'teams',d:sd}),'<b>Number</b> · on pace'),
  fig(wNumber({t:T,a:'teams',d:DUP}),'<b>Number</b> · above pace — red overshoot, “runs out”'),
  fig(wNumber({t:T,a:'teams',d:DMAX}),'<b>Number</b> · at 100%'),
  fig(wNumber({t:T,a:'product',d:D5,state:'stale'}),'<b>Number</b> · stale (no success in 6 h): dimmed, orange “as of”'),
  fig(wNumber({t:T,a:'product',d:D5,state:'signin'}),'<b>Number</b> · sign-in needed')));
add(row(
  fig(wCountdown({t:T,a:'teams'}),'<b>Countdown</b> · ticks live'),
  fig(wCountdown({t:T,a:'teams',state:'max'}),'<b>Countdown</b> · at 100%'),
  fig(wRing1({t:T,a:'personal',d:{...D5,p:26}}),'<b>Ring</b> · one account'),
  fig(wRing1({t:T,a:'teams',d:DUP}),'<b>Ring</b> · above pace'),
  fig(wNumber({t:L,a:'personal',d:{...D5,p:26}}),'<b>Light</b> — widgets follow the system appearance')));
add(`<h3>Medium (364 × 170 pt) — Ring (all accounts), Strip</h3>`+row(
  fig(wRing3({t:T,ds:[D5b,D5,sc5(12,30)]}),'<b>Ring</b> · three accounts, on pace'),
  fig(wRing3({t:T,ds:[DUP,DMAX,sc5(12,30)],state:[null,null,'signin']}),'<b>Ring</b> · above pace, at 100%, sign-in needed')));
add(row(
  fig(wStrip({t:T,a:'teams',d:sd,dw:sw}),'<b>Strip</b> · one account, 5h big with Weekly under it'),
  fig(wStrip({t:T,a:'teams',d:DUP,dw:sw}),'<b>Strip</b> · above pace'),
  fig(wStrip({t:T,a:'product',d:D5,dw:sw,state:'stale'}),'<b>Strip</b> · stale')));
add(`<div class="q"><b>Q3 — answered: offer it.</b> The Large widget below is new (Android has no face this big); it suits the iPad home screen.</div>`+row(
  fig(wLarge({t:T,ds:[D5b,DUP,sc5(12,30)],ws:[{p:18,pace:43},{p:64,pace:43},{p:9,pace:43}]}),'<b>Large</b> (364 × 382 pt; iPad 1.5× room) · every account, 5h and Weekly'),
  fig(`<div class="wg wsm" style="background:${T.widget};display:flex;flex-direction:column;gap:10px;padding:16px">${'<i style="display:block;height:14px;border-radius:7px;background:#3A3A3C;width:70%"></i><i style="display:block;height:44px;border-radius:10px;background:#3A3A3C;width:55%"></i><i style="display:block;height:8px;border-radius:4px;background:#3A3A3C"></i><i style="display:block;height:12px;border-radius:6px;background:#3A3A3C;width:60%"></i>'}</div>`,'<b>Placeholder</b> — what iOS shows while a widget first loads, and on a locked device with data protection')));

/* ---- 5 · lock screen widgets ---- */
add(`<h2>5 · Lock-screen widgets (iPhone and iPad)</h2>
<p>The closest thing iOS has to Android's status-bar ring. <b>iOS tints lock-screen widgets itself</b> (white on the wallpaper), so colour
can't carry severity here: the ring's fill and the pace tick do, and at 100% the number becomes <b>MAX</b>. Circular: one account's 5h.
Rectangular: account · 5h, number, bar, reset. Inline (the line above the clock): “Teams 5h 46% · resets 9:50 pm”.</p>`);
add(row(
  lockScreen({widgets:lkCircle({p:46,pace:46})+lkCircle({p:26,pace:68})+lkRect({a:'teams',p:46,pace:46}),inline:`${mark('claude',16,'#fff')}&nbsp;Teams 5h 46% · resets 9:50 pm`.replace(/^/,'<span style="display:inline-flex;align-items:center">')+'</span>',h:420,cap:'<b>On pace.</b> Two circulars (Teams, Product) and a rectangular; the inline widget replaces the date line.'}),
  lockScreen({widgets:lkCircle({p:62,pace:40})+lkCircle({p:100,pace:60})+lkRect({a:'teams',p:62,pace:40}),h:420,cap:'<b>Above pace and at 100%.</b> Overshoot shows as the arc running past the tick; 100% reads MAX.'}),
  lockScreen({widgets:lkCircle({state:'signin'})+lkRect({a:'product',state:'signin'}),h:420,cap:'<b>Sign-in needed.</b> A dash and “Sign in again”; tapping opens the app on that account.'})));

/* ---- 6 · live activity ---- */
add(`<h2>6 · Live Activity — the pinned panel's iOS home</h2>
<p>Android's always-on notification becomes a <b>Live Activity</b>: a panel on the lock screen (and the Dynamic Island on iPhone) that stays
while a 5-hour window is running. It's the Duet “Huge number” panel ported: per account, mark + “· 5h”, the bar with its pace tick, the
big number on the right — and, because Android's expanded Duet shows Weekly for both accounts and <b>the lock screen can't expand</b>,
each account also gets a thin <b>Weekly</b> line. iOS caps a Live Activity at <b>160 pt</b> tall, so the 5h countdown moves onto the label
line (“Teams · 5h · 1:35:12”, ticking live; “runs out 7:51 pm” when above pace). Each panel below prints its measured height.
iOS limits: it lives at most 8 hours and only updates when the app or a background refresh runs, so the countdown is exact but the
numbers can lag — after 30 minutes it says so (“as of”). It starts when you switch it on in Settings › Lock Screen, re-arms when the app
sees a new window, and ends at the reset with a final “5h reset” frame.</p>
<div class="q"><b>Q2 — answered:</b> show Weekly with the 5h window; two accounts if both fit, one otherwise. <b>Both fit</b> — see the
measured heights. One account (with Weekly) remains the layout when only one is signed in or chosen.</div>`);
const W18={p:18,pace:43}, W64={p:64,pace:43}, W9={p:9,pace:43}, W5={p:5,pace:43};
add(row(
  lockScreen({la:liveDuet({rows:[{a:'personal',d:{...D5,p:2,pace:1},w:W9,cd:'4:59:10'},{a:'teams',d:{...D5b,p:11,pace:62},w:W18,cd:'1:55:02'}]}),h:560,cap:'<b>Duet with Weekly, on pace.</b> Both accounts, both windows. <span class="mh"></span>'}),
  lockScreen({la:liveDuet({rows:[{a:'teams',d:DUP,w:W18},{a:'product',d:DMAX,w:W64}]}),h:560,cap:'<b>Above pace, and one at 100%.</b> “runs out” replaces the countdown when above pace; the maxed account counts down to its reset. <span class="mh"></span>'}),
  lockScreen({la:liveDuet({rows:[{a:'teams',d:D5b,w:W18,stale:true},{a:'product',d:D5,w:W5,signin:true}]}),h:560,cap:'<b>Stale and sign-in needed.</b> Numbers dim and “as of 2:10 pm” takes the countdown’s place; a broken sign-in says so on its label line. <span class="mh"></span>'})));
add(row(
  lockScreen({la:liveActivity({rows:[{a:'teams',d:D5b,fs:52,bw:200}],week:W18}),h:520,cap:'<b>One account</b> (only one signed in, or one chosen): the full 5h row with the clock time, and Weekly. <span class="mh"></span>'}),
  lockScreen({la:`<div class="la" style="width:370px;border-radius:24px;padding:16px;background:rgba(28,28,30,.86);display:flex;gap:12px;align-items:center">${amark('teams',26,DARK)}<div><div style="font-size:16px;font-weight:600">Teams · 5h reset</div><div style="font-size:13.5px;color:${DARK.txt}">Usage is back at 0%. The last window peaked at 87%.</div></div></div>`,h:520,cap:'<b>Ended.</b> At the reset the activity shows this final frame, then iOS removes it; it re-arms when the app next sees the new window.'})));
add(`<h3>Dynamic Island (iPhone only)</h3>`+row(
  fig(island('compact'),'<b>Compact</b> · mark and number left of the camera, countdown right'),
  fig(island('minimal'),'<b>Minimal</b> · when another app shares the island: a mini ring'),
  fig(island('expanded'),'<b>Expanded</b> · long-press: the first account’s full row'),
  fig(island('compact',{d:DMAX,cd:'0:42'}),'<b>Compact at 100%</b> · red number and countdown')));

/* ---- 7 · control center & alerts ---- */
const ctl = (t,top,bot,col) => `<div class="ctl" style="background:${t===LIGHT?'rgba(255,255,255,.75)':'rgba(60,60,64,.75)'};color:${t.ink}"><div style="width:44px;height:44px;border-radius:50%;background:${t===LIGHT?'#fff':'#1C1C1E'};display:flex;align-items:center;justify-content:center">${amark('teams',22,t)}</div><div><div style="font-size:14px;font-weight:600">${top}</div><div style="font-size:14px;color:${col||t.sub}">${bot}</div></div></div>`;
const banner = (t,title,body) => `<div class="notif" style="background:${t===LIGHT?'rgba(255,255,255,.88)':'rgba(44,44,46,.9)'};color:${t.ink}"><div class="appicon"><i style="background:#D97757"></i><i style="background:#10A37F"></i></div><div style="flex:1"><div style="display:flex;font-size:15px;font-weight:600">${title}<span style="margin-left:auto;color:${t.sub};font-weight:400;font-size:13px">now</span></div><div style="font-size:14.5px;line-height:1.35">${body}</div></div></div>`;
add(`<h2>7 · Control Center and reset alerts</h2>
<p><b>Control Center</b> (iOS 18): the Quick Settings tile's counterpart — one account's 5h number, tap opens the app on it. Controls refresh
like widgets, so the number carries the same freshness rules. <b>Reset alerts</b> are the reset pings: same text as Android, per account and
window, Off / If busy / Always. iOS can't poll at the reset, so the app schedules each alert ahead from the known reset time and cancels or
moves it whenever a check shows the window changed; “If busy” uses the peak seen by the last check.</p>`);
add(row(
  `<div class="wall2" style="display:flex;flex-direction:column;gap:12px">${ctl(T,'Teams · 5h','46%',acc('teams',T))}${ctl(T,'Teams · 5h','Limit reached',T.R)}${ctl(T,'Teams · 5h','Sign in again',T.O)}</div>`,
  `<div class="wall" style="display:flex;flex-direction:column;gap:12px">${banner(T,'Teams: 5h reset','Usage is back at 0%.')}${banner(T,'Personal: Weekly reset','Usage is back at 3%. Next reset in 6d 23h.')}${banner(L,'Teams: 5h reset','Usage is back at 0%.')}</div>`));
add(`<p class="note">Control states: normal (number in the account’s colour), at 100% (“Limit reached”), sign-in needed, and stale (number with “as of 9:12 am”).
Alert text is Android’s <code>ResetRollover</code> copy unchanged; the app icon is the Pulse tile.</p>`);

/* ---- 8 · state matrix ---- */
add(`<h2>8 · Every state, every surface</h2><div class="tablewrap"><table>
<tr><th>State</th><th>App (iPhone / iPad)</th><th>Home widgets</th><th>Lock-screen widgets</th><th>Live Activity · Island</th><th>Control</th></tr>
<tr><td>On pace</td><td>§1, §2</td><td>§4 all faces</td><td>§5</td><td>§6</td><td>§7</td></tr>
<tr><td>Above pace</td><td>red overshoot, “runs out”</td><td>red overshoot, “~ runs out”</td><td>arc past the tick</td><td>red overshoot, “runs out”</td><td>number in severity colour</td></tr>
<tr><td>At 100%</td><td>red, no projection</td><td>red, “Limit · resets”</td><td>MAX</td><td>red, countdown to reset</td><td>“Limit reached”</td></tr>
<tr><td>Above 80 / 90%</td><td colspan="5">yellow / orange fill everywhere colour exists (spec core §5); lock screen stays white (iOS tint)</td></tr>
<tr><td>Stale (6 h)</td><td>orange notice, dimmed numbers</td><td>dimmed, orange “as of”</td><td>last numbers, no colour</td><td>dimmed, orange “as of”</td><td>“as of”</td></tr>
<tr><td>Sign-in needed</td><td>red notice + “Sign in again”</td><td>dash + “Sign in again”</td><td>dash</td><td>line replaced by the notice</td><td>“Sign in again”</td></tr>
<tr><td>Plan refused (Free)</td><td>one quiet notice, no cards</td><td colspan="4">the account is left out (“No usage on the Free plan” if it is the only one)</td></tr>
<tr><td>No accounts</td><td>empty screen, two add buttons</td><td colspan="4">“Add an account in Cooldown” in each widget; no Live Activity</td></tr>
<tr><td>No data yet</td><td>cards with “—”</td><td>placeholder, then “—”</td><td>“—”</td><td>not started</td><td>“—”</td></tr>
<tr><td>Narrowest width</td><td>iPhone SE 375 pt (§1); Split View ⅓ on iPad uses the iPhone layout</td><td colspan="4">iOS sizes are fixed per device; text is sized to the smallest (iPhone SE)</td></tr>
</table></div>
<p class="note">Not drawn, because they port unchanged: the layout sheet (card order and fold), Appearance (density, chart height, accent per account),
the share card, About (version, licences, “not affiliated”). They’ll be the Android screens as iOS grouped lists.</p>`);

document.getElementById('app').innerHTML = S.join('\n');
// Print each Live Activity panel's height in points next to its caption (limit: 160 pt).
document.querySelectorAll('figure').forEach(f=>{const la=f.querySelector('.la'), mh=f.querySelector('.mh'); if(la&&mh){const h=la.offsetHeight; mh.innerHTML=`Measured: <b style="color:${h<=160?'#2e9d55':'#d33'}">${h} pt</b> of 160.`;}});
