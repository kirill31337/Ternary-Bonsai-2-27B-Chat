#!/usr/bin/env python3
"""Host state-machine test of real TransferService with minimal Android boundary doubles.
This is not instrumentation, an emulator, or a device test. stopSelf(id) models the
published Android contract: an old startId must not stop a newer request.
"""
from pathlib import Path
import runpy,subprocess,shutil
R=Path(__file__).resolve().parents[1];runpy.run_path(str(R/'make_api_stubs.py'))
d=R/'build/service-host';shutil.rmtree(d,ignore_errors=True);d.mkdir(parents=True)
shutil.copytree(R/'build/api-signatures',d/'src')
def put(name,body):
 p=d/'src'/(name+'.java');p.parent.mkdir(parents=True,exist_ok=True);p.write_text('package '+name.rsplit('/',1)[0].replace('/','.')+';\n'+body)
put('android/content/Intent','''public class Intent {private String action;private android.net.Uri uri;public Intent(Context c,Class<?> k){}public Intent setAction(String s){action=s;return this;}public String getAction(){return action;}public Intent setData(android.net.Uri u){uri=u;return this;}public android.net.Uri getData(){return uri;}public Intent addFlags(int f){return this;}public static final int FLAG_GRANT_READ_URI_PERMISSION=1;}''')
put('android/app/Service','''public abstract class Service extends android.content.Context {public static final int START_NOT_STICKY=2;public int latestId;public volatile boolean stopped,foreground;public static boolean deny;public void onCreate(){}public int onStartCommand(android.content.Intent i,int f,int id){return 2;}public void onDestroy(){}public abstract android.os.IBinder onBind(android.content.Intent i);public final void startForeground(int i,Notification n){if(deny)throw new SecurityException("denied");foreground=true;}public final void stopForeground(boolean b){foreground=false;}public final void stopSelf(){stopped=true;}public final void stopSelf(int id){if(id==latestId)stopped=true;}@Override public Object getSystemService(String s){return s.equals("notification")?new NotificationManager():new android.os.PowerManager();}}''')
put('android/os/Handler','''public class Handler {private static final java.util.concurrent.ConcurrentLinkedQueue<Runnable> q=new java.util.concurrent.ConcurrentLinkedQueue<>();public Handler(Looper l){}public final boolean post(Runnable r){q.add(r);return true;}public final boolean postDelayed(Runnable r,long ms){return true;}public final void removeCallbacks(Runnable r){q.remove(r);}public static void drain(){Runnable r;while((r=q.poll())!=null)r.run();}}''')
put('android/os/PowerManager','''public final class PowerManager {public static final int PARTIAL_WAKE_LOCK=1;public static int held;public WakeLock newWakeLock(int l,String t){return new WakeLock();}public final class WakeLock {boolean active;public void setReferenceCounted(boolean b){}public void acquire(long ms){active=true;held++;}public boolean isHeld(){return active;}public void release(){if(active){held--;active=false;}}}}''')
put('com/prismml/bonsailocal/repair/Transfers','''public class Transfers {public static final String PAUSE="pause",DOWNLOAD="download",IMPORT="import";public static volatile boolean busy,paused,release;public static volatile int performed,failed;public static void reset(){busy=paused=release=false;performed=failed=0;}public static void configure(android.content.Context c){}public static void fail(Throwable e){failed++;}public static String describe(){return "test";}public static int percent(){return 0;}public static void pause(){paused=true;}public static boolean begin(android.content.Context c){if(busy)return false;busy=true;return true;}public static void end(){busy=false;}public static void perform(String a,android.net.Uri u){performed++;while(!paused&&!release)try{Thread.sleep(5);}catch(InterruptedException e){break;}}public static void release(android.net.Uri u){}}''')
put('com/prismml/bonsailocal/repair/ServiceHostTest','''public class ServiceHostTest {
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 static TransferService make(){Transfers.reset();android.app.Service.deny=false;TransferService s=new TransferService();s.onCreate();return s;}
 static void send(TransferService s,String a,int id){s.latestId=id;s.onStartCommand(new android.content.Intent(s,TransferService.class).setAction(a),0,id);}
 static void await(java.util.function.BooleanSupplier f)throws Exception{long until=System.currentTimeMillis()+3000;while(!f.getAsBoolean()&&System.currentTimeMillis()<until){android.os.Handler.drain();Thread.sleep(5);}android.os.Handler.drain();ok(f.getAsBoolean(),"Timed out");}
 public static void main(String[]x)throws Exception {
  TransferService s=make();ok(((android.app.Service)s).foreground,"notification must precede work");send(s,Transfers.DOWNLOAD,1);await(()->Transfers.performed==1);send(s,Transfers.PAUSE,2);await(()->!Transfers.busy);ok(s.stopped,"Pause has newer startId; completing original request MUST still stop the service");ok(!((android.app.Service)s).foreground&&android.os.PowerManager.held==0,"pause leaked notification/wake lock");System.out.println("PASS pause with newer startId cleans up service");
  s=make();send(s,Transfers.DOWNLOAD,3);await(()->Transfers.performed==1);send(s,Transfers.DOWNLOAD,4);ok(Transfers.performed==1,"duplicate started second worker");Transfers.release=true;await(()->!Transfers.busy);ok(s.stopped,"duplicate start must not leak service");System.out.println("PASS duplicate request is ignored and completion stops service");
  s=make();send(s,Transfers.IMPORT,5);await(()->Transfers.performed==1);Transfers.release=true;await(()->!Transfers.busy);ok(s.stopped&&android.os.PowerManager.held==0,"import cleanup");System.out.println("PASS import completes and releases wake lock");
  Transfers.reset();android.app.Service.deny=true;s=new TransferService();s.onCreate();send(s,Transfers.DOWNLOAD,6);ok(s.stopped&&Transfers.performed==0,"foreground failure must not launch invisible work");System.out.println("PASS foreground permission failure prevents worker");
  System.out.println("4 host service lifecycle tests passed (Android boundary doubles, not device test)");
 }
}''')
shutil.copy(R/'java/com/prismml/bonsailocal/repair/TransferService.java',d/'src/com/prismml/bonsailocal/repair/')
classes=d/'classes';classes.mkdir()
subprocess.run(['javac','--release','8','-Xlint:-options','-d',str(classes)]+[str(p) for p in (d/'src').rglob('*.java')],check=True)
subprocess.run(['java','-cp',str(classes),'com.prismml.bonsailocal.repair.ServiceHostTest'],check=True)
