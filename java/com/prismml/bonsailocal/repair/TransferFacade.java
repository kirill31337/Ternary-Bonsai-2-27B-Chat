package com.prismml.bonsailocal.repair;
import android.webkit.JavascriptInterface;
import java.lang.ref.WeakReference;
public final class TransferFacade {
 private volatile WeakReference<MainActivity> activity;private final Bridge nativeBridge=new Bridge();
 public TransferFacade(MainActivity a){attach(a);Transfers.configure(a);Transfers.restoreRuntime(nativeBridge);Extensions.configure(a);Extensions.prepare(nativeBridge);}
 public void attach(MainActivity a){activity=new WeakReference<>(a);}
 private MainActivity owner(){return activity.get();}
 @JavascriptInterface public String status(){return "{\"backgroundError\":"+MiniJson.write(RuntimeService.error())+",\"engine\":"+nativeBridge.status()+",\"transfer\":"+Transfers.json()+",\"hasModel\":"+Transfers.readyFile()+",\"options\":"+Transfers.options().json()+",\"models\":"+Transfers.modelsJson()+",\"benchmark\":"+LocalBenchmark.status()+",\"extensions\":"+Extensions.status()+"}";}
 @JavascriptInterface public String configure(int m,int ctx,boolean q4,int t,int tb,int reason){
  return configureRuntime(m,ctx,q4,t,tb,reason,0);
 }
 @JavascriptInterface public String configureRuntime(int m,int ctx,boolean q4,int t,int tb,int reason,int gpuLayers){
  try{if(LocalBenchmark.isBusy())return "Сначала дождитесь завершения замера или остановите его.";
   return Transfers.updateOptions(new RuntimeOptions(m,ctx,q4,t,tb,reason,gpuLayers),nativeBridge)?"":"Сначала остановите движок или дождитесь завершения передачи.";
  }catch(Exception e){return e.toString();}
 }
 @JavascriptInterface public void start(){requestRuntime(RuntimeService.START);}
 @JavascriptInterface public void stop(){requestRuntime(RuntimeService.STOP);}
 @JavascriptInterface public void selfTest(){requestRuntime(RuntimeService.TEST);}
 private void requestRuntime(final String action){final MainActivity a=owner();if(a==null)return;a.runOnUiThread(new Runnable(){public void run(){try{RuntimeService.request(a,action);}catch(Throwable e){a.showError(e);}}});}
 @JavascriptInterface public void pause(){Transfers.pause();}
 @JavascriptInterface public void benchmark(){if(!Transfers.isBusy()&&nativeBridge.status().contains("\"state\":3,"))LocalBenchmark.start(Transfers.internalDir(),Transfers.options().json());}
 @JavascriptInterface public String runtimeInfo(){return LocalBenchmark.runtimeInfo(Transfers.internalDir());}
 private boolean engineBusy(){String s=nativeBridge.status();return s.contains("\"alive\":true")||s.contains("\"pending\":1")||s.contains("\"pending\":2")||s.contains("\"pending\":3")||s.contains("\"state\":2,")||s.contains("\"state\":3,")||s.contains("\"state\":6,");}
 @JavascriptInterface public void download(){final MainActivity activity=owner();if(activity==null)return;if(engineBusy()||LocalBenchmark.isBusy())return;activity.runOnUiThread(new Runnable(){public void run(){try{if(Transfers.selectAsset(Transfers.options().modelIndex))Transfers.request(activity,Transfers.DOWNLOAD,null);}catch(Throwable e){Transfers.fail(e);activity.showError(e);}}});}
 @JavascriptInterface public void chooseFile(){final MainActivity activity=owner();if(activity==null)return;if(engineBusy()||LocalBenchmark.isBusy())return;activity.runOnUiThread(new Runnable(){public void run(){try{if(Transfers.selectAsset(Transfers.options().modelIndex))activity.pickModel();}catch(Throwable e){activity.showError(e);}}});}

 @JavascriptInterface public String setVision(boolean enabled){return Extensions.setVision(enabled,nativeBridge);}
 @JavascriptInterface public String deleteAsset(int id){return Extensions.deleteAsset(id,nativeBridge);}
 @JavascriptInterface public String configureWeb(int mode,String provider,String key,boolean clear){return Extensions.setWeb(mode,provider,key,clear);}
 @JavascriptInterface public void testWeb(){Extensions.test();}
 @JavascriptInterface public void downloadVision(){final MainActivity activity=owner();if(activity==null)return;if(engineBusy()||LocalBenchmark.isBusy())return;activity.runOnUiThread(new Runnable(){public void run(){try{if(Transfers.selectAsset(2))Transfers.request(activity,Transfers.DOWNLOAD,null);}catch(Throwable e){Transfers.fail(e);activity.showError(e);}}});}
 @JavascriptInterface public void chooseVision(){final MainActivity activity=owner();if(activity==null)return;if(engineBusy()||LocalBenchmark.isBusy())return;activity.runOnUiThread(new Runnable(){public void run(){try{if(Transfers.selectAsset(2))activity.pickModel();}catch(Throwable e){activity.showError(e);}}});}
}
