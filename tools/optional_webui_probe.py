#!/usr/bin/env python3
"""OPTIONAL UNVERIFIED probe: blocked by administrator in the build environment.
Desktop Chromium + exact bundled Prism WebUI + actual native MCP server.
LLM inference and search provider are explicit fixtures, not live integrations.
"""
import json, threading, subprocess, time, sys
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from pathlib import Path
from playwright.sync_api import sync_playwright
ROOT=Path(__file__).resolve().parents[1];TMP=ROOT/'build/ui-integration';TMP.mkdir(parents=True,exist_ok=True)
MODEL='Ternary-Bonsai-2-27B-PQ2_0.gguf'
requests=[]
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*a): pass
 def send(self,code,body,mime='application/json'):
  b=body.encode() if isinstance(body,str) else body
  self.send_response(code);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(b)));self.end_headers();self.wfile.write(b)
 def do_GET(self):
  path=self.path.split('?')[0];requests.append(('GET',path))
  if path=='/bonsai-connect':return self.send(200,(TMP/'bootstrap.html').read_bytes(),'text/html; charset=utf-8')
  if path in ['/','/index.html'] or path.startswith('/chat/') or path.startswith('/settings'):return self.send(200,Path('/mnt/data/runtime-ui/file-3734473.dat').read_bytes(),'text/html; charset=utf-8')
  if 'bundle.DsjGxUhq.js' in path:return self.send(200,Path('/mnt/data/runtime-ui/file-1084070.dat').read_bytes(),'application/javascript')
  if 'bundle.CsYLz1sd.css' in path:return self.send(200,Path('/mnt/data/runtime-ui/file-789629.dat').read_bytes(),'text/css')
  if path=='/health':return self.send(200,'{"status":"ok"}')
  if path=='/props':return self.send(200,json.dumps({'role':'model','model_path':MODEL,'model_alias':MODEL,'total_slots':1,'default_generation_settings':{'n_ctx':16384,'params':{'temperature':0.7,'top_p':0.8,'top_k':20,'n_predict':512}},'modalities':{'vision':True,'audio':False,'video':False},'chat_template':'chatml','build_info':'fixture-server','tools':[],'cors_proxy_enabled':False,'n_ctx_train':262144}))
  if path in ['/v1/models','/models']:return self.send(200,json.dumps({'object':'list','data':[{'id':MODEL,'object':'model','owned_by':'llamacpp','model':MODEL,'status':{'value':'loaded'},'meta':{'n_ctx_train':262144},'capabilities':['completion','multimodal'],'modalities':{'vision':True,'audio':False,'video':False}}]}))
  if path=='/tools':return self.send(200,'{"tools":[]}')
  if path=='/slots':return self.send(200,'[]')
  if path=='/manifest.webmanifest':return self.send(200,'{}','application/manifest+json')
  return self.send(404,'{}')
 def do_POST(self):
  data=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or '{}');requests.append(('POST',self.path,data))
  if self.path=='/apply-template':return self.send(200,json.dumps({'prompt':'fixture prompt'}))
  if self.path=='/tokenize':return self.send(200,'{"tokens":[1,2,3]}')
  if self.path in ['/v1/chat/completions','/chat/completions']:
   fs=data.get('tools',[]);tool=next((x.get('function',{}).get('name') for x in fs if x.get('function',{}).get('name','').endswith('web_search')),None)
   has_result=any(x.get('role')=='tool' for x in data.get('messages',[]))
   if tool and not has_result:
    delta={'role':'assistant','tool_calls':[{'index':0,'id':'call-fixture','type':'function','function':{'name':tool,'arguments':'{"query":"PrismML Bonsai","count":2}'}}]};finish='tool_calls'
   else:delta={'role':'assistant','content':'INTEGRATION_OK: https://example.com/source — simulated model answer.'};finish='stop'
   if not data.get('stream',False):return self.send(200,json.dumps({'choices':[{'message':delta,'finish_reason':finish}]}))
   events=[{'id':'fixture','object':'chat.completion.chunk','choices':[{'index':0,'delta':delta,'finish_reason':None}]},{'id':'fixture','object':'chat.completion.chunk','choices':[{'index':0,'delta':{},'finish_reason':finish}]}]
   text=''.join('data: '+json.dumps(x)+'\n\n' for x in events)+'data: [DONE]\n\n';return self.send(200,text,'text/event-stream')
  return self.send(404,'{}')
mode=sys.argv[1] if len(sys.argv)>1 else '1'
proc=subprocess.Popen(['java','-cp',str(ROOT/'build/extensions-host/classes'),'com.prismml.bonsailocal.repair.UiMcpFixture',str(TMP),mode],stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
assert proc.stdout.readline().strip()=='READY'
server=ThreadingHTTPServer(('127.0.0.1',18080),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
errors=[];mcp=[]
try:
 with sync_playwright() as p:
  browser=p.chromium.launch(executable_path='/usr/bin/chromium',headless=True,args=['--no-sandbox','--disable-dev-shm-usage'])
  page=browser.new_page(viewport={'width':450,'height':900})
  page.on('pageerror',lambda e:errors.append(str(e)))
  def req(r):
   if '/mcp/' in r.url:
    try:mcp.append(json.loads(r.post_data or '{}'))
    except:pass
  page.on('request',req)
  page.goto('http://127.0.0.1:18080/bonsai-connect');page.wait_for_timeout(6000)
  (TMP/'first-dom.txt').write_text(page.locator('body').inner_text());page.screenshot(path=str(TMP/'first.png'))
  print('INITIAL',page.locator('body').inner_text()[:1800]);print('ERRORS',errors);print('MCP REQUESTS',mcp)
  field=page.locator('textarea').first
  field.fill('Найди PrismML Bonsai в интернете. Используй web_search.')
  field.press('Enter');page.wait_for_timeout(6000)
  (TMP/'after-send-dom.txt').write_text(page.locator('body').inner_text());page.screenshot(path=str(TMP/'after-send.png'))
  print('AFTER SEND',page.locator('body').inner_text()[-4000:]);print('MCP',mcp);print('ERRORS',errors)
  # Permission gate must really appear in Ask mode before the tool-call POST.
  if mode=='1':
   print('BUTTONS',[x.inner_text() for x in page.get_by_role('button').all() if x.is_visible()])
  (TMP/'requests.json').write_text(json.dumps({'http':requests,'mcp':mcp,'errors':errors},ensure_ascii=False,indent=2))
  browser.close()
finally:server.shutdown();proc.terminate();proc.wait(timeout=10)
