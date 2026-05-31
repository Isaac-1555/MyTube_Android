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
if(window.__yt_scriptlets)return;
window.__yt_scriptlets=true;
if(!location.hostname.includes('youtube.com'))return;
try{window.yt=window.yt||{};window.yt.config_=window.yt.config_||{};Object.defineProperty(window.yt.config_,'ADS_ENABLED',{value:false,writable:false,configurable:false});}catch(e){}
try{window.ytplayer=window.ytplayer||{};window.ytplayer.config=window.ytplayer.config||{};Object.defineProperty(window.ytplayer.config,'args',{value:{},writable:false});try{Object.defineProperty(window.ytplayer.config.args,'ad_easy_enabled',{value:false,writable:false,configurable:false});}catch(ee){}}catch(e){}
var F=window.fetch.bind(window);
window.fetch=function(i,r){return F(i,r).then(function(rs){
var c=rs.clone();if(!c)return rs;
return c.text().then(function(t){
try{var d=JSON.parse(t),ch=false;
if(d.playerAds!==undefined){delete d.playerAds;ch=true;}
if(d.adPlacements!==undefined){delete d.adPlacements;ch=true;}
if(d.adSlots!==undefined){delete d.adSlots;ch=true;}
if(d.adBreak!==undefined){delete d.adBreak;ch=true;}
if(d.adBreaks!==undefined){delete d.adBreaks;ch=true;}
if(d.playerResponse&&typeof d.playerResponse==='object'){
if(d.playerResponse.adPlacements){delete d.playerResponse.adPlacements;ch=true;}
if(d.playerResponse.playerAds){delete d.playerResponse.playerAds;ch=true;}
if(d.playerResponse.adBreak){delete d.playerResponse.adBreak;ch=true;}
if(d.playerResponse.adBreaks){delete d.playerResponse.adBreaks;ch=true;}
}
return ch?new Response(JSON.stringify(d),{status:rs.status,statusText:rs.statusText,headers:rs.headers}):rs;
}catch(e){return rs;}});});};
var O=XMLHttpRequest.prototype.open;
XMLHttpRequest.prototype.open=function(m,u){this._u=u;return O.apply(this,arguments);};
var S=XMLHttpRequest.prototype.send;
XMLHttpRequest.prototype.send=function(){
var x=this,oc=x.onreadystatechange;
x.onreadystatechange=function(){
if(x.readyState===4){try{var t=x.responseText,d=JSON.parse(t),ch=false;
if(d.playerAds!==undefined){delete d.playerAds;ch=true;}
if(d.adPlacements!==undefined){delete d.adPlacements;ch=true;}
if(d.adSlots!==undefined){delete d.adSlots;ch=true;}
if(d.adBreak!==undefined){delete d.adBreak;ch=true;}
if(d.adBreaks!==undefined){delete d.adBreaks;ch=true;}
if(d.playerResponse&&typeof d.playerResponse==='object'){
if(d.playerResponse.adPlacements){delete d.playerResponse.adPlacements;ch=true;}
if(d.playerResponse.playerAds){delete d.playerResponse.playerAds;ch=true;}
if(d.playerResponse.adBreak){delete d.playerResponse.adBreak;ch=true;}
if(d.playerResponse.adBreaks){delete d.playerResponse.adBreaks;ch=true;}
}
if(ch)Object.defineProperty(x,'responseText',{value:JSON.stringify(d)});
}catch(e){}}
if(oc)oc.apply(x,arguments);};
return S.apply(this,arguments);};
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
