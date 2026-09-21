package com.prismml.bonsailocal.repair;
import android.webkit.JavascriptInterface;
public final class TransferFacade {
 private final MainActivity activity;private final Bridge nativeBridge=new Bridge();
 public TransferFacade(MainActivity a){activity=a;Transfers.configure(a);Transfers.restoreRuntime(nativeBridge);Extensions.configure(a);Extensions.prepare(nativeBridge);}
 @JavascriptInterface public String status(){return "{\"engine\":"+nativeBridge.status()+",\"transfer\":"+Transfers.json()+",\"hasModel\":"+Transfers.readyFile()+",\"options\":"+Transfers.options().json()+",\"models\":"+Transfers.modelsJson()+",\"benchmark\":"+LocalBenchmark.status()+",\"extensions\":"+Extensions.status()+"}";}
 @JavascriptInterface public String configure(int m,int ctx,boolean q4,int t,int tb,int reason){
  try{if(LocalBenchmark.isBusy())return "Сначала дождитесь завершения замера или остановите его.";
   return Transfers.updateOptions(new RuntimeOptions(m,ctx,q4,t,tb,reason),nativeBridge)?"":"Сначала остановите движок или дождитесь завершения передачи.";
  }catch(Exception e){return e.toString();}
 }
 @JavascriptInterface public void start(){if(!Transfers.isBusy()&&!LocalBenchmark.isBusy()&&Transfers.readyFile()&&Extensions.prepare(nativeBridge))nativeBridge.start();}
 @JavascriptInterface public void stop(){LocalBenchmark.cancel();nativeBridge.stop();}
 @JavascriptInterface public void selfTest(){if(!Transfers.isBusy()&&!LocalBenchmark.isBusy())nativeBridge.selfTest();}
 @JavascriptInterface public void pause(){Transfers.pause();}
 @JavascriptInterface public void benchmark(){if(!Transfers.isBusy()&&nativeBridge.status().contains("\"state\":3,"))LocalBenchmark.start(Transfers.internalDir(),Transfers.options().json());}
 @JavascriptInterface public String runtimeInfo(){return LocalBenchmark.runtimeInfo(Transfers.internalDir());}
 private boolean engineBusy(){String s=nativeBridge.status();return s.contains("\"alive\":true")||s.contains("\"pending\":1")||s.contains("\"pending\":2")||s.contains("\"pending\":3")||s.contains("\"state\":2,")||s.contains("\"state\":3,")||s.contains("\"state\":6,");}
 @JavascriptInterface public void download(){if(engineBusy()||LocalBenchmark.isBusy())return;activity.runOnUiThread(new Runnable(){public void run(){try{if(Transfers.selectAsset(Transfers.options().modelIndex))Transfers.request(activity,Transfers.DOWNLOAD,null);}catch(Throwable e){Transfers.fail(e);activity.showError(e);}}});}
 @JavascriptInterface public void chooseFile(){if(engineBusy()||LocalBenchmark.isBusy())return;activity.runOnUiThread(new Runnable(){public void run(){try{if(Transfers.selectAsset(Transfers.options().modelIndex))activity.pickModel();}catch(Throwable e){activity.showError(e);}}});}

 @JavascriptInterface public String setVision(boolean enabled){return Extensions.setVision(enabled,nativeBridge);}
 @JavascriptInterface public String deleteAsset(int id){return Extensions.deleteAsset(id,nativeBridge);}
 @JavascriptInterface public String configureWeb(int mode,String provider,String key,boolean clear){return Extensions.setWeb(mode,provider,key,clear);}
 @JavascriptInterface public void testWeb(){Extensions.test();}
 @JavascriptInterface public void downloadVision(){if(engineBusy()||LocalBenchmark.isBusy())return;activity.runOnUiThread(new Runnable(){public void run(){try{if(Transfers.selectAsset(2))Transfers.request(activity,Transfers.DOWNLOAD,null);}catch(Throwable e){Transfers.fail(e);activity.showError(e);}}});}
 @JavascriptInterface public void chooseVision(){if(engineBusy()||LocalBenchmark.isBusy())return;activity.runOnUiThread(new Runnable(){public void run(){try{if(Transfers.selectAsset(2))activity.pickModel();}catch(Throwable e){activity.showError(e);}}});}
}