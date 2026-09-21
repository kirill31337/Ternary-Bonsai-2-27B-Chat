package com.prismml.bonsailocal.repair;
/** Runs on the existing chat origin before the bundled WebUI initializes its stores. */
public final class ChatBootstrap {
 private ChatBootstrap(){}
 public static String html(String endpoint,int mode){
  // Nothing from a search result, webpage, model or API secret is inserted here.
  String config=MiniJson.write(MiniJson.map("url",endpoint,"mode",mode));
  return "<!doctype html><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'\"><title>Bonsai Web</title><body style='background:#11151b;color:#eee;font:18px system-ui;padding:24px'><p id='message'>Подключение инструментов Bonsai…</p><script>"+
   "const setup="+config+";try{const key='LlamaUi.config',permissions='LlamaUi.alwaysAllowedTools';const raw=localStorage.getItem(key);const cfg=raw?JSON.parse(raw):{};if(!cfg||Array.isArray(cfg)||typeof cfg!=='object')throw new Error('Invalid saved settings');"+
   "let list=typeof cfg.mcpServers==='string'?JSON.parse(cfg.mcpServers||'[]'):(cfg.mcpServers||[]);if(!Array.isArray(list))throw new Error('Invalid MCP settings');"+
   "const id='bonsai-local-web';const own={id,displayName:'Bonsai Web',name:'bonsai-web',enabled:setup.mode!==0,url:setup.url,useProxy:false};cfg.mcpServers=JSON.stringify([...list.filter(s=>s.id!==id),own]);"+
   "let allowed=JSON.parse(localStorage.getItem(permissions)||'[]');if(!Array.isArray(allowed))throw new Error('Invalid permissions');const names=['web_search','web_fetch'].map(n=>'mcp-'+id+':'+n);allowed=allowed.filter(k=>!names.includes(k));if(setup.mode===2)allowed.push(...names);"+
   "localStorage.setItem(key,JSON.stringify(cfg));localStorage.setItem(permissions,JSON.stringify(allowed));location.replace('/');"+
   "}catch(e){document.getElementById('message').textContent='Не удалось подключить веб-инструменты. Настройки чатов не удалены. Вернитесь назад и проверьте настройки. '+String(e);}</script></body>";
 }
}
