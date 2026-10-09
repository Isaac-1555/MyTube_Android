package com.example.mytube.adblock

data class Scriptlet(
    val domain: String?,
    val name: String,
    val args: List<String>,
)

object UblockScriptlets {
    /**
     * Ad container / field names stripped from YouTube API payloads.
     * Single source of truth shared by the document-start JS and the
     * native `/youtubei/` interception in WebViewManager.
     */
    val AD_KEYS = listOf(
        "playerAds",
        "adPlacements",
        "adSlots",
        "adBreak",
        "adBreaks",
        "adSlotRenderer",
        "promotedSparklesWebRenderer",
        "promotedSparklesTextSearchRenderer",
        "promotedVideoRenderer",
        "compactPromotedVideoRenderer",
        "displayAdRenderer",
        "inFeedAdLayoutRenderer",
        "searchPyvRenderer",
        "playerLegacyDesktopWatchAdsRenderer",
        "enforcementMessageViewModel",
        "auxiliaryUi",
    )

    private fun jsString(s: String): String = s
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    /**
     * Universal script injected via addDocumentStartJavaScript for YouTube.
     * Runs before ANY page script — equivalent to uBlock's "run_at": "document_start".
     * Patches fetch + XMLHttpRequest + JSON.parse, prunes inline
     * ytInitialData/ytInitialPlayerResponse, and hides ad containers via CSS.
     *
     * @param css cosmetic CSS to hide ad containers before first paint.
     */
    fun getDocumentStartJs(css: String = ""): String {
        val keys = AD_KEYS.joinToString(",") { "'$it'" }
        return """
(function(){
if(window.__yt_adblock)return;
window.__yt_adblock=true;
if(!location.hostname.includes('youtube.com'))return;
var AD_KEYS=[$keys];
try{window.yt=window.yt||{};window.yt.config_=window.yt.config_||{};window.yt.config_.ADS_ENABLED=false;}catch(e){}
try{window.ytplayer=window.ytplayer||{};window.ytplayer.config=window.ytplayer.config||{};window.ytplayer.config.args=window.ytplayer.config.args||{};window.ytplayer.config.args.ad_easy_enabled=false;}catch(e){}
try{Object.defineProperty(Object.prototype,'hasAllowedInstreamAd',{value:true,writable:true,configurable:false});}catch(e){}
try{Object.defineProperty(Object.prototype,'adBlocksFound',{value:0,writable:true,configurable:false});}catch(e){}
function hasAd(t){if(typeof t!=='string')return false;for(var i=0;i<AD_KEYS.length;i++){if(t.indexOf('"'+AD_KEYS[i]+'"')!==-1)return true;}return false;}
function stripAdFields(t){if(!hasAd(t))return t;for(var i=0;i<AD_KEYS.length;i++){var k=AD_KEYS[i];t=t.split('"'+k+'"').join('"__mytube_no_'+k+'"');}return t;}
function pruneObj(o,depth){
if(!o||typeof o!=='object'||depth>40)return o;
if(Array.isArray(o)){for(var i=0;i<o.length;i++)pruneObj(o[i],depth+1);return o;}
for(var k in o){if(!Object.prototype.hasOwnProperty.call(o,k))continue;
if(AD_KEYS.indexOf(k)!==-1){try{delete o[k];}catch(e){}continue;}
pruneObj(o[k],depth+1);}
return o;}
try{['ytInitialData','ytInitialPlayerResponse','ytInitialGuideData'].forEach(function(name){
var store=window[name];
Object.defineProperty(window,name,{configurable:true,get:function(){return store;},set:function(v){store=pruneObj(v,0);}});
if(store)store=pruneObj(store,0);
});}catch(e){}
var _jp=JSON.parse;JSON.parse=function(){var t=arguments[0];var hit=hasAd(t);if(hit)t=stripAdFields(t);return _jp.call(this,t,arguments[1]);};
var _f=window.fetch.bind(window);window.fetch=function(){return _f.apply(this,arguments).then(function(rs){if(!rs||!rs.ok||!rs.clone)return rs;var c=rs.clone();if(!c)return rs;return c.text().then(function(t){var s=stripAdFields(t);return s!==t?new Response(s,{status:rs.status,statusText:rs.statusText,headers:rs.headers}):rs;}).catch(function(){return rs;});});};
var _o=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){this._u=u;return _o.apply(this,arguments);};
var _s=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(){var x=this,oc=x.onreadystatechange;x.onreadystatechange=function(){if(x.readyState===4){try{if(typeof x.responseText==='string'){var t=x.responseText;if(hasAd(t)){var s=stripAdFields(t);Object.defineProperty(x,'responseText',{configurable:true,get:function(){return s;}});Object.defineProperty(x,'response',{configurable:true,get:function(){return s;}});}else if(x.response&&typeof x.response==='object'){pruneObj(x.response,0);}}}catch(e){}}if(oc)oc.apply(x,arguments);};return _s.apply(this,arguments);};
var CSSTEXT='${jsString(css)}';
function applyCss(){try{var s=document.getElementById('mytube-adblock-style');if(!s){s=document.createElement('style');s.id='mytube-adblock-style';s.textContent=CSSTEXT;var h=document.head||document.documentElement;if(!h)return false;h.appendChild(s);}return true;}catch(e){return true;}}
(function waitHead(){if(applyCss())return;setTimeout(waitHead,0);})();
try{document.addEventListener('yt-navigate-finish',function(){applyCss();pruneObj(window.ytInitialData,0);pruneObj(window.ytInitialPlayerResponse,0);},false);}catch(e){}
})();
""".trimIndent()
    }

    fun generate(domain: String, scriptlets: List<Scriptlet>): String {
        val applicable = scriptlets.filter { it.domain == null || domain.contains(it.domain!!, ignoreCase = true) }
        if (applicable.isEmpty()) return ""

        val ops = mutableListOf<String>()
        for (s in applicable) {
            when (s.name) {
                "json-prune" -> {
                    for (prop in s.args) ops.add("t=d.$prop;if(t!==undefined){delete d.$prop;c=true;}")
                }
                "trusted-replace-fetch-response" -> {
                    if (s.args.size >= 2) {
                        val n = s.args[0].trim('/').replace("\\", "\\\\").replace("'", "\\'")
                        val r = s.args[1].replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
                        ops.add("try{var re=new RegExp('$n','g');if(re.test(t)){t=t.replace(re,'$r');c=true;}}catch(e){}")
                    }
                }
                "set-constant" -> {
                    if (s.args.size >= 2) {
                        ops.add("try{if(d.${s.args[0]}!==undefined){d.${s.args[0]}=${s.args[1]};c=true;}}catch(e){}")
                    }
                }
            }
        }
        if (ops.isEmpty()) return ""

        return """
(function(){
if(window.__yt_sf)return;window.__yt_sf=true;
var F=window.fetch.bind(window);
window.fetch=function(i,r){return F(i,r).then(function(rs){
var c=rs.clone();if(!c)return rs;
return c.text().then(function(t){var p=P(t);return p!==t?new Response(p,{status:rs.status,statusText:rs.statusText,headers:rs.headers}):rs;});});};
var O=XMLHttpRequest.prototype.open;
XMLHttpRequest.prototype.open=function(m,u){this._u=u;return O.apply(this,arguments);};
var S=XMLHttpRequest.prototype.send;
XMLHttpRequest.prototype.send=function(){
var x=this,oc=x.onreadystatechange;
x.onreadystatechange=function(){
if(x.readyState===4){try{var t=x.responseText,p=P(t);if(p!==t){Object.defineProperty(x,'responseText',{value:p});}}catch(e){}}
if(oc)oc.apply(x,arguments);};
return S.apply(this,arguments);};
function P(t){if(!t)return t;try{var d=JSON.parse(t),c=false;${ops.joinToString(" ")}return c?JSON.stringify(d):t;}catch(e){return t;}}
})();
""".trimIndent()
    }
}
