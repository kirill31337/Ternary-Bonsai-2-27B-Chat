package com.prismml.bonsailocal.repair;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;

/** The service, not the Activity, owns the transfer. Reopening UI never restarts work.
 * On OS process termination, offsets survive and the user can explicitly resume.
 */
public final class TransferService extends Service {
 private static final String CHANNEL="bonsai_transfers";private static final int NOTICE=3003;
 private Handler handler;private Thread worker;private PowerManager.WakeLock wake;private boolean foreground;
 private final Runnable ticker=new Runnable(){public void run(){if(foreground){try{((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notice(Transfers.describe()));}catch(RuntimeException e){Transfers.fail(e);}handler.postDelayed(this,1000);}}};
 @Override public void onCreate(){super.onCreate();handler=new Handler(Looper.getMainLooper());
  try{Transfers.configure(this);NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);n.createNotificationChannel(new NotificationChannel(CHANNEL,"Загрузка модели",NotificationManager.IMPORTANCE_LOW));startForeground(NOTICE,notice("Подготовка передачи"));foreground=true;}
  catch(Throwable e){Transfers.fail(e);Transfers.end();stopSelf();}
 }
 private Notification notice(String text){
  Intent open=new Intent(this,MainActivity.class);PendingIntent view=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Intent pause=new Intent(this,TransferService.class).setAction(Transfers.PAUSE);PendingIntent stop=PendingIntent.getService(this,1,pause,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Bonsai Local · передача модели").setContentText(text).setContentIntent(view).setOngoing(true).setOnlyAlertOnce(true).setProgress(100,Transfers.percent(),false).addAction(android.R.drawable.ic_media_pause,"Пауза",stop).build();
 }
 @Override public int onStartCommand(Intent intent,int flags,final int startId){
  if(!foreground){stopSelf(startId);return START_NOT_STICKY;}
  if(intent==null){stopForeground(true);foreground=false;stopSelf(startId);return START_NOT_STICKY;}
  final String action=intent.getAction();
  if(Transfers.PAUSE.equals(action)){Transfers.pause();if(worker==null){stopForeground(true);foreground=false;stopSelf(startId);}return START_NOT_STICKY;}
  if(!Transfers.DOWNLOAD.equals(action)&&!Transfers.IMPORT.equals(action)){stopSelf(startId);return START_NOT_STICKY;}
  if(worker!=null&&worker.isAlive())return START_NOT_STICKY;
  if(!Transfers.begin(this))return START_NOT_STICKY;
  final Uri uri=intent.getData();
  try{
   PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);wake=p.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"BonsaiLocal:transfer");wake.setReferenceCounted(false);wake.acquire(6L*60*60*1000);
   handler.removeCallbacks(ticker);handler.post(ticker);
   worker=new Thread(new Runnable(){public void run(){
    try{Transfers.perform(action,uri);}catch(Throwable e){Transfers.fail(e);}
    finally{Transfers.release(uri);handler.post(new Runnable(){public void run(){finishTransfer(startId);}});}
   }},"Bonsai-transfer");worker.start();
  }catch(Throwable e){Transfers.fail(e);finishTransfer(startId);}
  return START_NOT_STICKY;
 }
 private void finishTransfer(int id){handler.removeCallbacks(ticker);if(wake!=null&&wake.isHeld())wake.release();wake=null;foreground=false;stopForeground(true);stopSelf();Transfers.end();worker=null;}
 @Override public void onDestroy(){handler.removeCallbacks(ticker);if(worker!=null&&worker.isAlive())Transfers.pause();if(wake!=null&&wake.isHeld())wake.release();foreground=false;super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
