package com.example.mytube.adblock

data class Scriptlet(
    val domain: String?,
    val name: String,
    val args: List<String>,
)

object UblockScriptlets {
    /**
     * Universal script injected via addDocumentStartJavaScript for YouTube.
     * Runs before ANY page script — equivalent to uBlock's "run_at": "document_start".
     * Patches fetch + XMLHttpRequest to strip ad fields from API responses.
     */
    fun getDocumentStartJs(): String = """
(function(){
if(window.__yt_adblock)return;
window.__yt_adblock=true;
if(!location.hostname.includes('youtube.com'))return;
try{window.yt=window.yt||{};window.yt.config_=window.yt.config_||{};window.yt.config_.ADS_ENABLED=false;}catch(e){}
try{window.ytplayer=window.ytplayer||{};window.ytplayer.config=window.ytplayer.config||{};window.ytplayer.config.args=window.ytplayer.config.args||{};window.ytplayer.config.args.ad_easy_enabled=false;}catch(e){}
try{Object.defineProperty(Object.prototype,'hasAllowedInstreamAd',{value:true,writable:true,configurable:false});}catch(e){}
try{Object.defineProperty(Object.prototype,'adBlocksFound',{value:0,writable:true,configurable:false});}catch(e){}
function stripAdFields(t){
if(typeof t!=='string')return t;
t=t.replace(/"adPlacements"/g,'"no_ads"');
t=t.replace(/"adSlots"/g,'"no_ads"');
t=t.replace(/"playerAds"/g,'"no_ads"');
t=t.replace(/"adBreak"/g,'"no_ads"');
t=t.replace(/"adBreaks"/g,'"no_ads"');
t=t.replace(/"enforcementMessageViewModel"/g,'"__no_ads"');
return t;
}
var _jp=JSON.parse;JSON.parse=function(){var t=arguments[0];if(typeof t==='string'&&(t.indexOf('"adPlacements"')!==-1||t.indexOf('"playerAds"')!==-1||t.indexOf('"adSlots"')!==-1||t.indexOf('"adBreak"')!==-1||t.indexOf('"adBreaks"')!==-1)){t=stripAdFields(t);}var r=_jp.call(this,t,arguments[1]);if(r&&typeof r==='object'&&!Array.isArray(r)){var ch=false;['playerAds','adPlacements','adSlots','adBreak','adBreaks'].forEach(function(k){if(r.hasOwnProperty(k)){delete r[k];ch=true;}});if(r.playerResponse&&typeof r.playerResponse==='object'){['playerAds','adPlacements','adSlots','adBreak','adBreaks'].forEach(function(k){if(r.playerResponse.hasOwnProperty(k)){delete r.playerResponse[k];ch=true;}});}if(r.auxiliaryUi){delete r.auxiliaryUi;}}return r;};
var _f=window.fetch.bind(window);window.fetch=function(){return _f.apply(this,arguments).then(function(rs){if(!rs||!rs.ok||!rs.clone)return rs;var c=rs.clone();if(!c)return rs;return c.text().then(function(t){var s=stripAdFields(t);return s!==t?new Response(s,{status:rs.status,statusText:rs.statusText,headers:rs.headers}):rs;});});};
var _o=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){this._u=u;return _o.apply(this,arguments);};var _s=XMLHttpRequest.prototype.send;XMLHttpRequest.prototype.send=function(){var x=this,oc=x.onreadystatechange;x.onreadystatechange=function(){if(x.readyState===4){try{var t=x.responseText;if(typeof t==='string'&&t.indexOf('adPlacements')!==-1){var s=stripAdFields(t);if(s!==t){Object.defineProperty(x,'responseText',{value:s});}}}catch(e){}}if(oc)oc.apply(x,arguments);};return _s.apply(this,arguments);};
})();
""".trimIndent()

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
