#!/usr/bin/env python3
"""Run the real RuntimeService with Android/native boundaries replaced, not an emulator."""
from pathlib import Path
import runpy, shutil, subprocess
R=Path(__file__).resolve().parents[1]
runpy.run_path(str(R/'make_api_stubs.py'))
D=R/'build/runtime-service-host';shutil.rmtree(D,ignore_errors=True)
shutil.copytree(R/'build/api-signatures', D/'src')
P='com/prismml/bonsailocal/repair/'
def put(name,body):
 p=D/'src'/(name+'.java');p.parent.mkdir(parents=True,exist_ok=True)
 p.write_text('package '+name.rsplit('/',1)[0].replace('/','.')+';\n'+body)
put('android/os/Build','public class Build {public static class VERSION {public static int SDK_INT=35;}}')
put('android/app/Service','''public abstract class Service extends android.content.Context {public static final int START_NOT_STICKY=2;public static boolean deny;public boolean foreground,stopped;public void onCreate(){}public int onStartCommand(android.content.Intent i,int f,int id){return 2;}public void onDestroy(){}public abstract android.os.IBinder onBind(android.content.Intent i);public void startForeground(int id,Notification n){if(deny)throw new SecurityException("denied");foreground=true;}public void startForeground(int id,Notification n,int type){if(type!=0x40000000)throw new AssertionError("wrong service type");startForeground(id,n);}public void stopForeground(boolean b){foreground=false;}public void stopSelf(){stopped=true;}public void stopSelf(int id){stopped=true;}public Object getSystemService(String s){return s.equals("notification")?new NotificationManager():new android.os.PowerManager();}}''')
put('android/content/Intent','''public class Intent {public static final int FLAG_ACTIVITY_SINGLE_TOP=0x20000000,FLAG_ACTIVITY_CLEAR_TOP=0x4000000,FLAG_GRANT_READ_URI_PERMISSION=1;String action;public Intent(Context c,Class<?> t){}public Intent setAction(String s){action=s;return this;}public String getAction(){return action;}public Intent addFlags(int f){return this;}}''')
put('android/os/Handler','''public class Handler {static final java.util.ArrayList<Runnable> queue=new java.util.ArrayList<>();public Handler(Looper l){}public boolean post(Runnable r){queue.add(r);return true;}public boolean postDelayed(Runnable r,long ms){queue.add(r);return true;}public void removeCallbacks(Runnable r){queue.removeIf(x->x==r);}public static void tick(){if(!queue.isEmpty())queue.remove(0).run();}public static void clear(){queue.clear();}}''')
put('android/os/PowerManager','''public class PowerManager {public static final int PARTIAL_WAKE_LOCK=1;public static int held;public WakeLock newWakeLock(int level,String tag){return new WakeLock();}public class WakeLock {boolean active;public void setReferenceCounted(boolean b){}public void acquire(){if(!active){active=true;held++;}}public void acquire(long ms){acquire();}public boolean isHeld(){return active;}public void release(){if(active){active=false;held--;}}}}''')
put('android/app/NotificationManager','public class NotificationManager {public static final int IMPORTANCE_LOW=2;public static boolean deny;public void createNotificationChannel(NotificationChannel c){}public void notify(int id,Notification n){if(deny)throw new SecurityException("notification update denied");}}')
put('android/R','public class R {public static class drawable {public static int stat_notify_sync=1,ic_media_pause=2,stat_sys_download=3;}}')
put(P+'RuntimeEnvironment','public class RuntimeEnvironment {public static void ensure(android.content.Context c){} }')
put(P+'UiSession','public class UiSession {}')
put(P+'Extensions','public class Extensions {public static boolean prepare(Bridge b){return true;} }')
put(P+'Transfers','public class Transfers {public static boolean isBusy(){return false;}public static boolean readyFile(){return true;} }')
put(P+'LocalBenchmark','public class LocalBenchmark {public static boolean isBusy(){return false;}public static void cancel(){} }')
put(P+'Bridge','''public class Bridge {public static int starts,stops,state=5,pending;public static boolean alive;public static android.app.Service service;public String status(){return "{\\"state\\":"+state+",\\"pending\\":"+pending+",\\"alive\\":"+alive+"}";}public void start(){if(!service.foreground||android.os.PowerManager.held==0)throw new AssertionError("unprotected start");starts++;state=2;pending=1;}public void selfTest(){start();state=6;}public void stop(){stops++;pending=2;} }''')
put(P+'RuntimeServiceHostTest','''public class RuntimeServiceHostTest {
 static int checks;static void ok(boolean b,String m){if(!b)throw new AssertionError(m);checks++;}
 static RuntimeService create(){android.os.Handler.clear();Bridge.state=5;Bridge.pending=0;Bridge.alive=false;Bridge.starts=0;Bridge.stops=0;RuntimeService s=new RuntimeService();Bridge.service=s;s.onCreate();return s;}
 static void send(RuntimeService s,String action){s.onStartCommand(new android.content.Intent(s,RuntimeService.class).setAction(action),0,1);}
 public static void main(String[] args){
  android.app.Service.deny=false;RuntimeService s=create();send(s,RuntimeService.START);android.os.Handler.tick();
  ok(((android.app.Service)s).foreground&&!s.stopped&&Bridge.starts==1,"load must run in foreground");ok(android.os.PowerManager.held==1,"loading missing wake lock");
  send(s,RuntimeService.START);ok(Bridge.starts==1,"duplicate start restarted model");
  Bridge.state=3;Bridge.pending=0;Bridge.alive=true;android.os.Handler.tick();ok(((android.app.Service)s).foreground&&android.os.PowerManager.held==1,"ready/background inference lost protection");
  android.app.NotificationManager.deny=true;Bridge.state=6;android.os.Handler.tick();ok(((android.app.Service)s).foreground&&android.os.PowerManager.held==1,"notification update failure ended active inference protection");android.app.NotificationManager.deny=false;
  send(s,RuntimeService.STOP);android.os.Handler.tick();ok(((android.app.Service)s).foreground,"stop released before native process exited");
  Bridge.pending=0;Bridge.alive=false;Bridge.state=5;android.os.Handler.tick();ok(s.stopped&&!((android.app.Service)s).foreground&&android.os.PowerManager.held==0,"stop leaked resources");s.onDestroy();
  android.app.Service.deny=true;s=create();send(s,RuntimeService.START);ok(Bridge.starts==0&&s.stopped,"denied foreground started hidden work");ok(!RuntimeService.error().isEmpty(),"foreground failure invisible to UI");s.onDestroy();
  android.app.Service.deny=false;s=create();send(s,RuntimeService.TEST);ok(Bridge.state==6,"runtime test bypassed service");s.onDestroy();ok(Bridge.stops>0&&android.os.PowerManager.held==0,"service destruction leaked runtime protection");
  System.out.println(checks+" RuntimeService lifecycle assertions passed; Android/native boundaries simulated");
 }
}''')
for name in ('RuntimeService','RuntimeSnapshot','MiniJson'):
 shutil.copy(R/'java'/P/(name+'.java'), D/'src'/P)
C=D/'classes';C.mkdir()
subprocess.run(['javac','--release','8','-d',str(C)]+[str(p) for p in (D/'src').rglob('*.java')],check=True)
subprocess.run(['java','-cp',str(C),'com.prismml.bonsailocal.repair.RuntimeServiceHostTest'],check=True)
