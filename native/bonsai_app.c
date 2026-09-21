#include "platform.h"
#include <jni.h>
#include <stdatomic.h>

// No NativeActivity, UI JNI calls, raw fork(), or bootstrap HTTP server.
// The managed Activity remains usable if this library or llama-server fails.
static JavaVM *vm;
static jobject app_context;
static jobject child;
static pthread_t worker;
static atomic_int initialized;
static atomic_int stop_flag, command, state;
static atomic_llong completed_bytes, total_bytes;
static atomic_flag error_lock = ATOMIC_FLAG_INIT;
static char error_text[2048];
static char model_path[2048], lib_path[2048], files_path[2048], log_path[2200];
static long long download_id=-1;
static char pq_model_path[2048], vision_path[2048];
static atomic_int vision_enabled, runtime_alive;
static atomic_int model_choice, ctx_size=16384, kv_q4, threads=4, batch_threads=8, thinking;
static atomic_int gpu_layers, gpu_offloaded;
static atomic_flag settings_lock=ATOMIC_FLAG_INIT;
static void lock_settings(void){while(atomic_flag_test_and_set(&settings_lock))usleep(1000);}
static void unlock_settings(void){atomic_flag_clear(&settings_lock);}
static const char *chosen_path(void){return atomic_load(&model_choice)?pq_model_path:model_path;}
static const char *chosen_marker(void){return atomic_load(&model_choice)?"pq2.complete":"model.complete";}
static long long chosen_size(void){return atomic_load(&model_choice)?7206168928LL:5946648928LL;}
static const char *chosen_sha(void){return atomic_load(&model_choice)?"3907dc1658db1f78a9826bf8d5bcb8dc65db0d466388937af57f2294fae62ec1":"53107f530aa52eb00912263ab1ee29bd199261c87cd7b4ad4ca1318c1fe33ee3";}

static int child_is_test;
static atomic_int last_exit=-999;
static const char MODEL_NAME[]="Ternary-Bonsai-2-27B-PTQ1_0.gguf";
static const char MODEL_URL[]="https://huggingface.co/prism-ml/Ternary-Bonsai-2-27B-gguf/resolve/main/Ternary-Bonsai-2-27B-PTQ1_0.gguf?download=true";
// 0=idle, 1=download, 2=loading, 3=ready, 4=error, 5=model on disk, 6=self-test.

static void lock_error(void) { while(atomic_flag_test_and_set(&error_lock)) usleep(1000); }
static void unlock_error(void) { atomic_flag_clear(&error_lock); }
static void log_line(const char *s) {
    __android_log_write(4,"BonsaiLocal",s);
    if(!log_path[0]) return;
    int fd=open(log_path,O_WRONLY|O_CREAT|O_APPEND,0600);
    if(fd<0) return;
    if(lseek(fd,0,SEEK_END)>1048576) { close(fd); fd=open(log_path,O_WRONLY|O_CREAT|O_TRUNC,0600); }
    if(fd>=0) { (void)write(fd,s,strlen(s)); (void)write(fd,"\n",1); close(fd); }
}
static void fail(const char *s) {
    lock_error(); snprintf(error_text,sizeof(error_text),"%s",s); unlock_error();
    log_line(s); atomic_store(&state,4);
}
static void clear_error(void) { lock_error(); error_text[0]=0;unlock_error(); }

// Android JNI calls must not continue while an exception is pending.
static int jerr(JNIEnv *e,const char *step) {
    if(!(*e)->ExceptionCheck(e)) return 0;
    jthrowable ex=(*e)->ExceptionOccurred(e); (*e)->ExceptionClear(e);
    char msg[2048]; snprintf(msg,sizeof(msg),"JNI: %s",step);
    jclass tc=(*e)->FindClass(e,"java/lang/Throwable");
    if(tc) {
        jmethodID ts=(*e)->GetMethodID(e,tc,"toString","()Ljava/lang/String;");
        if(ts && !(*e)->ExceptionCheck(e)) {
            jstring text=(*e)->CallObjectMethod(e,ex,ts);
            if(text && !(*e)->ExceptionCheck(e)) {
                const char *s=(*e)->GetStringUTFChars(e,text,0);
                if(s) { snprintf(msg,sizeof(msg),"%s: %s",step,s);(*e)->ReleaseStringUTFChars(e,text,s); }
                (*e)->DeleteLocalRef(e,text);
            }
        }
        (*e)->DeleteLocalRef(e,tc);
    }
    if((*e)->ExceptionCheck(e)) (*e)->ExceptionClear(e);
    if(ex) (*e)->DeleteLocalRef(e,ex);
    fail(msg); return 1;
}
static jclass jc(JNIEnv *e,const char *name) {
    jclass c=(*e)->FindClass(e,name); if(jerr(e,name)) return 0;
    if(!c) fail(name); return c;
}
static jmethodID jm(JNIEnv *e,jclass c,const char *name,const char *sig) {
    if(!c) return 0;
    jmethodID m=(*e)->GetMethodID(e,c,name,sig); if(jerr(e,name)) return 0;
    if(!m) fail(name); return m;
}
static jstring js(JNIEnv *e,const char *s) {
    jstring r=(*e)->NewStringUTF(e,s); if(jerr(e,"NewStringUTF")) return 0; return r;
}
static jobject obj(JNIEnv *e,jobject o,jmethodID m,jvalue *a,const char *step) {
    if(!o || !m) { fail(step);return 0; }
    jobject r=(*e)->CallObjectMethodA(e,o,m,a); if(jerr(e,step)) return 0; return r;
}
static jobject make(JNIEnv *e,jclass c,jmethodID m,jvalue *a,const char *step) {
    if(!c || !m) {fail(step);return 0;}
    jobject r=(*e)->NewObjectA(e,c,m,a); if(jerr(e,step)) return 0;return r;
}
static int void_call(JNIEnv *e,jobject o,jmethodID m,jvalue *a,const char *step) {
    if(!o || !m) {fail(step);return 0;}
    (*e)->CallVoidMethodA(e,o,m,a);return !jerr(e,step);
}
static jobject dm(JNIEnv *e) {
    jclass c=jc(e,"android/content/Context");
    jmethodID m=jm(e,c,"getSystemService","(Ljava/lang/String;)Ljava/lang/Object;");
    jvalue a[1]={{.l=js(e,"download")}};
    if(!a[0].l || !m) return 0;
    jobject r=obj(e,app_context,m,a,"getSystemService(download)");
    if(!r) fail("Системный DownloadManager недоступен.");return r;
}
static void save_id(void) {
    char p[2200],v[64];snprintf(p,sizeof(p),"%s/download.id",files_path);
    snprintf(v,sizeof(v),"%lld",download_id);
    int fd=open(p,O_WRONLY|O_CREAT|O_TRUNC,0600);
    if(fd>=0){(void)write(fd,v,strlen(v));close(fd);}
}
static void restore_id(void) {
    char p[2200],v[64];snprintf(p,sizeof(p),"%s/download.id",files_path);
    int fd=open(p,O_RDONLY);if(fd<0)return;
    ssize_t n=read(fd,v,sizeof(v)-1);close(fd);if(n>0){v[n]=0;download_id=strtoll(v,0,10);}
}
static int complete_model(void) {
    char p[2200],mark[80]={0};snprintf(p,sizeof(p),"%s/%s",files_path,chosen_marker());
    int fd=open(p,O_RDONLY);if(fd<0)return 0;
    ssize_t m=read(fd,mark,sizeof(mark)-1);close(fd);
    int valid=m>=64&&memcmp(mark,chosen_sha(),64)==0;
    // Preserve the old DownloadManager marker only for the original PTQ file.
    if(!atomic_load(&model_choice)&&m>=2&&memcmp(mark,"ok",2)==0)valid=1;
    if(!valid)return 0;
    fd=open(chosen_path(),O_RDONLY);if(fd<0)return 0;
    char magic[4];ssize_t n=read(fd,magic,4);off_t len=lseek(fd,0,SEEK_END);close(fd);
    return n==4&&memcmp(magic,"GGUF",4)==0&&len==chosen_size();
}
static int complete_vision(void) {
    char p[2200],mark[80]={0};snprintf(p,sizeof(p),"%s/vision.complete",files_path);
    int fd=open(p,O_RDONLY);if(fd<0)return 0;ssize_t m=read(fd,mark,sizeof(mark)-1);close(fd);
    if(m<64||memcmp(mark,"6807ede61d570bb86ba34b756a0fa109edc33668604de867c6ea6d8f1d631903",64)!=0)return 0;
    fd=open(vision_path,O_RDONLY);if(fd<0)return 0;char magic[4];ssize_t n=read(fd,magic,4);off_t len=lseek(fd,0,SEEK_END);close(fd);
    return n==4&&memcmp(magic,"GGUF",4)==0&&len==629246976LL;
}
static int mark_complete(void) {
    int fd=open(model_path,O_RDONLY);if(fd<0){fail("Модель скачана, но файл не открывается.");return 0;}
    char magic[4];ssize_t n=read(fd,magic,4);off_t len=lseek(fd,0,SEEK_END);close(fd);
    if(n!=4 || memcmp(magic,"GGUF",4)!=0 || len<1000000000LL ||
       (atomic_load(&total_bytes)>0 && len!=atomic_load(&total_bytes))) {
        fail("Файл модели неполный или не является GGUF. Запуск отменён.");return 0;
    }
    char p[2200];snprintf(p,sizeof(p),"%s/model.complete",files_path);
    fd=open(p,O_WRONLY|O_CREAT|O_TRUNC,0600);if(fd>=0){(void)write(fd,"ok\n",3);close(fd);}
    return 1;
}
static int download_status(JNIEnv *e) {
    jobject manager=dm(e);if(!manager)return -1;
    jclass qc=jc(e,"android/app/DownloadManager$Query");
    jobject q=make(e,qc,jm(e,qc,"<init>","()V"),0,"DownloadManager.Query");if(!q)return -1;
    jlongArray ids=(*e)->NewLongArray(e,1);if(jerr(e,"NewLongArray") || !ids)return -1;
    jlong id=(jlong)download_id;(*e)->SetLongArrayRegion(e,ids,0,1,&id);if(jerr(e,"SetLongArrayRegion"))return -1;
    jvalue a[1]={{.l=ids}};
    if(!obj(e,q,jm(e,qc,"setFilterById","([J)Landroid/app/DownloadManager$Query;"),a,"setFilterById"))return -1;
    jclass dc=jc(e,"android/app/DownloadManager");a[0].l=q;
    jobject cur=obj(e,manager,jm(e,dc,"query","(Landroid/app/DownloadManager$Query;)Landroid/database/Cursor;"),a,"DownloadManager.query");
    if(!cur){if(atomic_load(&state)!=4)fail("DownloadManager.query вернул пустой Cursor.");return -1;}
    jclass cc=jc(e,"android/database/Cursor");
    jmethodID first=jm(e,cc,"moveToFirst","()Z"), closem=jm(e,cc,"close","()V");
    jmethodID idx=jm(e,cc,"getColumnIndexOrThrow","(Ljava/lang/String;)I"), lng=jm(e,cc,"getLong","(I)J");
    int result=-1;
    if(!first||!closem||!idx||!lng)goto done;
    jboolean found=(*e)->CallBooleanMethod(e,cur,first);if(jerr(e,"Cursor.moveToFirst"))goto done;
    if(!found)goto done;
    const char *names[]={"status","bytes_so_far","total_size","reason"};
    long long values[4]={0};
    for(int i=0;i<4;i++){
        a[0].l=js(e,names[i]);if(!a[0].l)goto done;
        jint col=(*e)->CallIntMethodA(e,cur,idx,a);if(jerr(e,"Cursor.getColumnIndexOrThrow"))goto done;
        a[0].i=col;values[i]=(*e)->CallLongMethodA(e,cur,lng,a);if(jerr(e,"Cursor.getLong"))goto done;
    }
    result=(int)values[0];atomic_store(&completed_bytes,values[1]);atomic_store(&total_bytes,values[2]);
    if(result==16){char msg[200];snprintf(msg,sizeof(msg),"DownloadManager: загрузка не удалась, код %lld. Диагностика доступна в меню.",values[3]);fail(msg);}
    done: if(closem)void_call(e,cur,closem,0,"Cursor.close");return result;
}
static int begin_download(JNIEnv *e) {
    jobject manager=dm(e);if(!manager)return 0;
    // Cancel/remove a previous failed download before a deliberate retry.
    if(download_id>=0){
        jclass dc=jc(e,"android/app/DownloadManager");
        jmethodID rm=jm(e,dc,"remove","([J)I");
        jlongArray ids=(*e)->NewLongArray(e,1);if(jerr(e,"NewLongArray")||!ids||!rm)return 0;
        jlong id=download_id;(*e)->SetLongArrayRegion(e,ids,0,1,&id);if(jerr(e,"SetLongArrayRegion"))return 0;
        jvalue a[1]={{.l=ids}};(*e)->CallIntMethodA(e,manager,rm,a);if(jerr(e,"DownloadManager.remove"))return 0;
    }
    // This path belongs only to this app's fixed model, not to a user-selected file.
    (void)unlink(model_path);
    jclass uc=jc(e,"android/net/Uri");if(!uc)return 0;
    jmethodID parse=(*e)->GetStaticMethodID(e,uc,"parse","(Ljava/lang/String;)Landroid/net/Uri;");if(jerr(e,"Uri.parse")||!parse)return 0;
    jstring url=js(e,MODEL_URL);if(!url)return 0;
    jobject uri=(*e)->CallStaticObjectMethod(e,uc,parse,url);if(jerr(e,"Uri.parse")||!uri)return 0;
    jclass rc=jc(e,"android/app/DownloadManager$Request");jvalue a[3]={{.l=uri}};
    jobject req=make(e,rc,jm(e,rc,"<init>","(Landroid/net/Uri;)V"),a,"DownloadManager.Request");if(!req)return 0;
    a[0].l=js(e,"Bonsai 2 27B PTQ1_0");if(!a[0].l)return 0;
    if(!obj(e,req,jm(e,rc,"setTitle","(Ljava/lang/CharSequence;)Landroid/app/DownloadManager$Request;"),a,"setTitle"))return 0;
    a[0].i=1;
    if(!obj(e,req,jm(e,rc,"setNotificationVisibility","(I)Landroid/app/DownloadManager$Request;"),a,"setNotificationVisibility"))return 0;
    a[0].z=JNI_FALSE;
    if(!obj(e,req,jm(e,rc,"setAllowedOverRoaming","(Z)Landroid/app/DownloadManager$Request;"),a,"setAllowedOverRoaming"))return 0;
    a[0].l=app_context;a[1].l=0;a[2].l=js(e,MODEL_NAME);if(!a[2].l)return 0;
    if(!obj(e,req,jm(e,rc,"setDestinationInExternalFilesDir","(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Landroid/app/DownloadManager$Request;"),a,"setDestinationInExternalFilesDir"))return 0;
    jclass dc=jc(e,"android/app/DownloadManager");
    jmethodID enqueue=jm(e,dc,"enqueue","(Landroid/app/DownloadManager$Request;)J");if(!enqueue)return 0;
    a[0].l=req;download_id=(*e)->CallLongMethodA(e,manager,enqueue,a);if(jerr(e,"DownloadManager.enqueue"))return 0;
    save_id();atomic_store(&state,1);log_line("Download queued (model is not loaded automatically).");return 1;
}
static int process_alive(JNIEnv *e) {
    if(!child)return 0;
    jclass pc=jc(e,"java/lang/Process");jmethodID m=jm(e,pc,"isAlive","()Z");if(!m)return 0;
    jboolean alive=(*e)->CallBooleanMethod(e,child,m);if(jerr(e,"Process.isAlive"))return 0;return alive;
}
static int dispose_child(JNIEnv *e,int terminate) {
    if(!child)return 1;
    jclass pc=jc(e,"java/lang/Process");
    if(terminate){
        if(!void_call(e,child,jm(e,pc,"destroy","()V"),0,"Process.destroy"))return 0;
        // Do not enable a second multi-GB model while the previous process exits.
        // All waiting is on the worker, never the Android/UI thread.
        for(int i=0;i<25&&process_alive(e);i++)usleep(100000);
        if(process_alive(e)){
            if(!obj(e,child,jm(e,pc,"destroyForcibly","()Ljava/lang/Process;"),0,"Process.destroyForcibly"))return 0;
            for(int i=0;i<25&&process_alive(e);i++)usleep(100000);
        }
        if(process_alive(e)){fail("Движок ещё завершает работу. Подождите; новый запуск заблокирован.");return 0;}
    }
    (*e)->DeleteGlobalRef(e,child);child=0;atomic_store(&runtime_alive,0);return 1;
}
static int spawn_child(JNIEnv *e,int test) {
    if(child){fail("Движок уже запущен. Сначала остановите его.");return 0;}
    char exe[2200],outpath[2200];snprintf(exe,sizeof(exe),"%s/libllama_server_exec.so",lib_path);
    snprintf(outpath,sizeof(outpath),"%s/server.log",files_path);
    int gpu=atomic_load(&gpu_layers);
    char ctx[24],th[16],tb[16],ngl[16],gpu_path[2200];
    snprintf(gpu_path,sizeof(gpu_path),"%s/libbonsai_vulkan.so",lib_path);
    if(gpu){int fd=open(gpu_path,O_RDONLY);if(fd<0){fail("Библиотека Vulkan недоступна. Выберите CPU или переустановите APK.");return 0;}close(fd);}
    snprintf(ngl,sizeof(ngl),"%d",gpu);
    snprintf(ctx,sizeof(ctx),"%d",atomic_load(&ctx_size));
    snprintf(th,sizeof(th),"%d",atomic_load(&threads));
    snprintf(tb,sizeof(tb),"%d",atomic_load(&batch_threads));
    int mode=atomic_load(&thinking),q4=atomic_load(&kv_q4);
    const char *args[80]={exe,"--model",chosen_path(),"--host","127.0.0.1","--port","18080",
        "--ctx-size",ctx,"--threads",th,"--threads-batch",tb,"--n-gpu-layers",ngl,"--device",gpu?"Vulkan0":"none","--parallel","1",
        "--batch-size","256","--ubatch-size","128","--flash-attn","on",
        "--cache-type-k",q4?"q4_0":"f16","--cache-type-v",q4?"q4_0":"f16","--jinja",
        "--chat-template-kwargs",mode?"{\"enable_thinking\":true}":"{\"enable_thinking\":false}",
        "--reasoning-budget",mode?"-1":"0","--reasoning-format",mode?"deepseek":"none",
        "--reasoning-effort",mode==1?"medium":"xhigh",
        "--temp",mode?"1.0":"0.7","--top-p",mode?"0.95":"0.80","--top-k","20","--min-p","0",
        "--presence-penalty",mode?"0.0":"1.5","--repeat-penalty","1.0"};
    int count=0;while(count<80&&args[count])count++;
    if(!test&&atomic_load(&vision_enabled)){
        if(!complete_vision()){fail("Модуль изображений отсутствует или не прошёл проверку. Скачайте mmproj или отключите изображения.");return 0;}
        args[count++]="--mmproj";args[count++]=vision_path;args[count++]="--no-mmproj-offload";
        args[count++]="--image-max-tokens";args[count++]="512";
    }
    const char *check[]={exe,"--version",0,0};
    if(gpu){check[1]="--device";check[2]="Vulkan0";check[3]="--list-devices";}
    int n=test?(gpu?4:2):count;
    for(int i=0;i<n;i++)log_line(test?check[i]:args[i]);
    jclass sc=jc(e,"java/lang/String");if(!sc)return 0;
    jobjectArray ar=(*e)->NewObjectArray(e,n,sc,0);if(jerr(e,"NewObjectArray")||!ar)return 0;
    for(int i=0;i<n;i++){
        jstring s=js(e,test?check[i]:args[i]);if(!s)return 0;
        (*e)->SetObjectArrayElement(e,ar,i,s);(*e)->DeleteLocalRef(e,s);if(jerr(e,"SetObjectArrayElement"))return 0;
    }
    jclass bc=jc(e,"java/lang/ProcessBuilder");jvalue a[2]={{.l=ar}};
    jobject pb=make(e,bc,jm(e,bc,"<init>","([Ljava/lang/String;)V"),a,"ProcessBuilder");if(!pb)return 0;
    jobject env=obj(e,pb,jm(e,bc,"environment","()Ljava/util/Map;"),0,"ProcessBuilder.environment");if(!env)return 0;
    jclass mc=jc(e,"java/util/Map");jmethodID put=jm(e,mc,"put","(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");if(!put)return 0;
    a[0].l=js(e,"LD_LIBRARY_PATH");a[1].l=js(e,lib_path);if(!a[0].l||!a[1].l)return 0;
    (void)obj(e,env,put,a,"Map.put");if(atomic_load(&state)==4)return 0;
    a[0].l=js(e,"GGML_BACKEND_PATH");if(!a[0].l)return 0;
    if(gpu){
        a[1].l=js(e,gpu_path);if(!a[1].l)return 0;
        (void)obj(e,env,put,a,"Map.put(GPU)");
    }else{
        (void)obj(e,env,jm(e,mc,"remove","(Ljava/lang/Object;)Ljava/lang/Object;"),a,"Map.remove(GPU)");
    }
    if(atomic_load(&state)==4)return 0;
    a[0].z=JNI_TRUE;
    if(!obj(e,pb,jm(e,bc,"redirectErrorStream","(Z)Ljava/lang/ProcessBuilder;"),a,"redirectErrorStream"))return 0;
    jclass fc=jc(e,"java/io/File");a[0].l=js(e,outpath);if(!a[0].l)return 0;
    jobject f=make(e,fc,jm(e,fc,"<init>","(Ljava/lang/String;)V"),a,"File(server.log)");if(!f)return 0;
    a[0].l=f;if(!obj(e,pb,jm(e,bc,"redirectOutput","(Ljava/io/File;)Ljava/lang/ProcessBuilder;"),a,"redirectOutput"))return 0;
    jobject proc=obj(e,pb,jm(e,bc,"start","()Ljava/lang/Process;"),0,"ProcessBuilder.start");if(!proc)return 0;
    child=(*e)->NewGlobalRef(e,proc);if(jerr(e,"NewGlobalRef(Process)")||!child)return 0;
    atomic_store(&runtime_alive,1);atomic_store(&gpu_offloaded,gpu?-1:0);child_is_test=test;last_exit=-999;atomic_store(&state,test?6:2);
    log_line(test?"Runtime self-test started.":gpu?"Model loading with experimental Vulkan; offload will be checked before ready.":"Model loading on CPU; optional Vulkan plugin is not loaded.");return 1;
}
static int positive_mib(const char *p) {
    while(*p==' ')p++;
    int digits=0,dots=0,positive=0;
    while((*p>='0'&&*p<='9')||*p=='.'){
        if(*p=='.'){if(++dots>1)return 0;}else{digits++;if(*p!='0')positive=1;}
        p++;
    }
    return digits&&positive&&strlen(p)>=4&&memcmp(p," MiB",4)==0;
}
static int read_gpu_offload(void) {
    char path[2200],buf[8321];snprintf(path,sizeof(path),"%s/server.log",files_path);
    int fd=open(path,O_RDONLY);if(fd<0)return -1;
    int result=-1,model_buffer=0,compute_buffer=0;size_t carry=0,total=0;
    while(total<1048576){
        ssize_t n=read(fd,buf+carry,8192);if(n<=0)break;total+=(size_t)n;
        size_t length=carry+(size_t)n;buf[length]=0;
        for(size_t i=0;i+10<length;i++){
          if(i+27<length&&memcmp(buf+i,"Vulkan0 model buffer size =",27)==0)model_buffer|=positive_mib(buf+i+27);
          if(i+29<length&&memcmp(buf+i,"Vulkan0 compute buffer size =",29)==0)compute_buffer|=positive_mib(buf+i+29);
          if(memcmp(buf+i,"offloaded ",10)==0){
            char *end=0;long long count=strtoll(buf+i+10,&end,10);
            if(end>buf+i+10&&*end=='/'&&count>=0&&count<=1024){
                char *tail=0;long long all=strtoll(end+1,&tail,10);
                if(all>=count&&tail>end+1&&(size_t)(buf+length-tail)>=14&&memcmp(tail," layers to GPU",14)==0)result=(int)count;
            }
          }
        }
        carry=length<128?length:128;
        for(size_t i=0;i<carry;i++)buf[i]=buf[length-carry+i];
    }
    close(fd);return model_buffer&&compute_buffer?result:-1;
}
static int healthy(JNIEnv *e) {
    // Health HTTP status, NOT merely whether a TCP port is open.
    jclass uc=jc(e,"java/net/URL");jvalue a[1]={{.l=js(e,"http://127.0.0.1:18080/health")}};if(!a[0].l)return 0;
    jobject u=make(e,uc,jm(e,uc,"<init>","(Ljava/lang/String;)V"),a,"URL");if(!u)return 0;
    jobject conn=obj(e,u,jm(e,uc,"openConnection","()Ljava/net/URLConnection;"),0,"URL.openConnection");if(!conn)return 0;
    jclass cc=jc(e,"java/net/URLConnection");a[0].i=300;
    if(!void_call(e,conn,jm(e,cc,"setConnectTimeout","(I)V"),a,"setConnectTimeout"))return 0;
    if(!void_call(e,conn,jm(e,cc,"setReadTimeout","(I)V"),a,"setReadTimeout"))return 0;
    jclass hc=jc(e,"java/net/HttpURLConnection");jmethodID response=jm(e,hc,"getResponseCode","()I"),disc=jm(e,hc,"disconnect","()V");
    if(!response||!disc)return 0;
    jint code=(*e)->CallIntMethod(e,conn,response);
    // Connection refusal is normal while the server is starting.
    if((*e)->ExceptionCheck(e)){(*e)->ExceptionClear(e);code=0;}
    (*e)->CallVoidMethod(e,conn,disc);if((*e)->ExceptionCheck(e))(*e)->ExceptionClear(e);
    return code==200;
}
static void *run(void *unused) {
    (void)unused;JNIEnv *e=0;
    if((*vm)->AttachCurrentThread(vm,(void**)&e,0)!=JNI_OK){fail("Не удалось подключить рабочий поток к JVM.");return 0;}
    // No automatic model load or automatic download on reopening the app.
    int initial_query=download_id>=0 && !complete_model();
    int ticks=0;
    while(!atomic_load(&stop_flag)){
        if((*e)->PushLocalFrame(e,128)<0){jerr(e,"PushLocalFrame");break;}
        int cmd=atomic_exchange(&command,0);
        if(cmd==2&&dispose_child(e,1)){clear_error();atomic_store(&state,complete_model()?5:0);log_line("Runtime stopped by user; process termination confirmed.");}
        if(cmd==3){clear_error();spawn_child(e,1);}
        if(cmd==1){
            clear_error();
            if(complete_model())spawn_child(e,0);
            else if(!atomic_load(&model_choice))begin_download(e);
            else fail("Выбранный PQ2_0 отсутствует или не прошёл проверку. Сначала скачайте или импортируйте файл.");
        }
        if(initial_query || atomic_load(&state)==1){
            initial_query=0;
            int s=download_status(e);
            if(s==8){if(mark_complete()){atomic_store(&state,5);log_line("Download complete; awaiting manual model load.");}}
            else if(s==1||s==2||s==4)atomic_store(&state,1);
            else if(s<0 && atomic_load(&state)!=4){download_id=-1;save_id();atomic_store(&state,0);}
        }
        if(child){
            if(!process_alive(e)){
                jclass pc=jc(e,"java/lang/Process");jmethodID m=jm(e,pc,"exitValue","()I");
                if(m){last_exit=(*e)->CallIntMethod(e,child,m);jerr(e,"Process.exitValue");}
                dispose_child(e,0);
                if(child_is_test && last_exit==0){clear_error();atomic_store(&state,complete_model()?5:0);log_line("Runtime self-test passed, exit code 0. This is not a model inference test.");}
                else {char msg[220];snprintf(msg,sizeof(msg),"llama-server завершился, код %d. Откройте «Диагностика»: подробности в server.log.",last_exit);fail(msg);}
            }else if(!child_is_test && atomic_load(&state)==2 && (ticks%2)==0 && healthy(e)){
                int actual=atomic_load(&gpu_layers)?read_gpu_offload():0;
                atomic_store(&gpu_offloaded,actual);
                if(atomic_load(&gpu_layers)&&actual<=0){
                    dispose_child(e,1);
                    fail("Vulkan не подтвердил перенос слоёв на GPU. Выберите CPU; подробности в диагностике.");
                }else{atomic_store(&state,3);log_line("llama-server /health returned HTTP 200; requested backend verified.");}
            }
        }
        (*e)->PopLocalFrame(e,0);ticks++;usleep(500000);
    }
    if((*e)->PushLocalFrame(e,32)>=0){dispose_child(e,1);(*e)->PopLocalFrame(e,0);}
    (*vm)->DetachCurrentThread(vm);return 0;
}

__attribute__((visibility("default")))
size_t bonsai_json_escape(const char *src,char *out,size_t cap) {
    size_t j=0;if(!cap)return 0;
    for(size_t i=0;src[i];i++){
        unsigned char c=(unsigned char)src[i];char tmp[7];const char *p=tmp;size_t n;
        if(c=='"'||c=='\\'){tmp[0]='\\';tmp[1]=(char)c;n=2;}
        else if(c<32){snprintf(tmp,sizeof(tmp),"\\u%04x",c);n=6;}
        else{tmp[0]=(char)c;n=1;}
        if(j+n>=cap)break;memcpy(out+j,p,n);j+=n;
    }
    out[j]=0;return j;
}
static int copy_arg(JNIEnv *e,jstring s,char *dest,size_t cap) {
    if(!s)return 0;const char *p=(*e)->GetStringUTFChars(e,s,0);
    if(jerr(e,"GetStringUTFChars")||!p)return 0;
    size_t n=strlen(p);int ok=n>0&&n<cap;
    if(ok)memcpy(dest,p,n+1);(*e)->ReleaseStringUTFChars(e,s,p);return ok;
}
JNIEXPORT void JNICALL Java_com_prismml_bonsailocal_repair_Bridge_init(JNIEnv *e,jclass c,jobject context,jstring ext,jstring libs,jstring files) {
    (void)c;if(initialized)return;
    if(!context||!ext||!libs||!files){jclass ex=(*e)->FindClass(e,"java/lang/IllegalArgumentException");if(ex)(*e)->ThrowNew(e,ex,"Bonsai: context/storage paths must not be null");return;}
    char external[2048];
    if(!copy_arg(e,ext,external,sizeof(external))||!copy_arg(e,libs,lib_path,sizeof(lib_path))||!copy_arg(e,files,files_path,sizeof(files_path))){
        jclass ex=(*e)->FindClass(e,"java/lang/IllegalArgumentException");if(ex)(*e)->ThrowNew(e,ex,"Bonsai: invalid or overly long storage path");return;
    }
    if(snprintf(model_path,sizeof(model_path),"%s/%s",external,MODEL_NAME)>=(int)sizeof(model_path)){
        jclass ex=(*e)->FindClass(e,"java/lang/IllegalArgumentException");if(ex)(*e)->ThrowNew(e,ex,"Bonsai: model path too long");return;
    }
    if(snprintf(pq_model_path,sizeof(pq_model_path),"%s/Ternary-Bonsai-2-27B-PQ2_0.gguf",external)>=(int)sizeof(pq_model_path)){
        jclass ex=(*e)->FindClass(e,"java/lang/IllegalArgumentException");if(ex)(*e)->ThrowNew(e,ex,"Bonsai: model path too long");return;
    }
    if(snprintf(vision_path,sizeof(vision_path),"%s/Ternary-Bonsai-2-27B-mmproj-Q8_0.gguf",external)>=(int)sizeof(vision_path)){
        jclass ex=(*e)->FindClass(e,"java/lang/IllegalArgumentException");if(ex)(*e)->ThrowNew(e,ex,"Bonsai: projector path too long");return;
    }
    atomic_store(&vision_enabled,0);atomic_store(&runtime_alive,0);
    atomic_store(&model_choice,0);atomic_store(&ctx_size,16384);atomic_store(&kv_q4,0);atomic_store(&threads,4);atomic_store(&batch_threads,8);atomic_store(&thinking,0);
    snprintf(log_path,sizeof(log_path),"%s/native.log",files_path);
    char msg[240];snprintf(msg,sizeof(msg),"Bonsai Local 1.2.0 native init; page size=%d; Prism runtime 9a9394a",getpagesize());log_line(msg);
    if((*e)->GetJavaVM(e,&vm)!=JNI_OK){fail("GetJavaVM failed");return;}
    app_context=(*e)->NewGlobalRef(e,context);if(jerr(e,"NewGlobalRef(Context)")||!app_context)return;
    atomic_store(&stop_flag,0);atomic_store(&command,0);clear_error();restore_id();atomic_store(&state,complete_model()?5:0);
    if(pthread_create(&worker,0,run,0)!=0){(*e)->DeleteGlobalRef(e,app_context);app_context=0;fail("pthread_create failed");return;}
    initialized=1;
}
JNIEXPORT jstring JNICALL Java_com_prismml_bonsailocal_repair_Bridge_status(JNIEnv *e,jobject o) {
    (void)o;char raw[2048],escaped[12300],json[12800];lock_error();snprintf(raw,sizeof(raw),"%s",error_text);unlock_error();
    bonsai_json_escape(raw,escaped,sizeof(escaped));
    snprintf(json,sizeof(json),"{\"state\":%d,\"done\":%lld,\"total\":%lld,\"exit\":%d,\"pending\":%d,\"modelIndex\":%d,\"ctxSize\":%d,\"kvQ4\":%s,\"threads\":%d,\"batchThreads\":%d,\"thinking\":%d,\"gpuLayers\":%d,\"gpuReportedLayers\":%d,\"vision\":%s,\"alive\":%s,\"error\":\"%s\"}",
        atomic_load(&state),atomic_load(&completed_bytes),atomic_load(&total_bytes),atomic_load(&last_exit),atomic_load(&command),atomic_load(&model_choice),atomic_load(&ctx_size),atomic_load(&kv_q4)?"true":"false",atomic_load(&threads),atomic_load(&batch_threads),atomic_load(&thinking),atomic_load(&gpu_layers),atomic_load(&gpu_offloaded),atomic_load(&vision_enabled)?"true":"false",atomic_load(&runtime_alive)?"true":"false",escaped);
    return (*e)->NewStringUTF(e,json);
}
static int valid_ctx(int n){return n==4096||n==8192||n==16384||n==32768||n==65536||n==131072||n==262144;}
static int valid_threads(int n){return n==2||n==4||n==6||n==8;}
JNIEXPORT jboolean JNICALL Java_com_prismml_bonsailocal_repair_Bridge_configureRuntime(JNIEnv *e,jobject o,jint model,jint ctx,jboolean q4,jint th,jint tb,jint reason,jint gpu){
    (void)e;(void)o;
    if(model<0||model>1||!valid_ctx(ctx)||!valid_threads(th)||!valid_threads(tb)||reason<0||reason>2)return JNI_FALSE;
    if((gpu!=0&&gpu!=8&&gpu!=16&&gpu!=32&&gpu!=99)||(gpu&&model!=0))return JNI_FALSE;
    lock_settings();int st=atomic_load(&state);
    if(!initialized||atomic_load(&runtime_alive)||atomic_load(&command)!=0||st==1||st==2||st==3||st==6){unlock_settings();return JNI_FALSE;}
    atomic_store(&model_choice,model);atomic_store(&ctx_size,ctx);atomic_store(&kv_q4,q4?1:0);
    atomic_store(&threads,th);atomic_store(&batch_threads,tb);atomic_store(&thinking,reason);
    atomic_store(&gpu_layers,gpu);atomic_store(&gpu_offloaded,gpu?-1:0);
    clear_error();atomic_store(&state,complete_model()?5:0);unlock_settings();return JNI_TRUE;
}
JNIEXPORT jboolean JNICALL Java_com_prismml_bonsailocal_repair_Bridge_configureOptions(JNIEnv *e,jobject o,jint model,jint ctx,jboolean q4,jint th,jint tb,jint reason){
    return Java_com_prismml_bonsailocal_repair_Bridge_configureRuntime(e,o,model,ctx,q4,th,tb,reason,0);
}
JNIEXPORT jboolean JNICALL Java_com_prismml_bonsailocal_repair_Bridge_configureVision(JNIEnv *e,jobject o,jboolean on){
    (void)e;(void)o;lock_settings();int st=atomic_load(&state);
    if(!initialized||atomic_load(&runtime_alive)||atomic_load(&command)!=0||st==1||st==2||st==3||st==6||(on&&!complete_vision())){unlock_settings();return JNI_FALSE;}
    atomic_store(&vision_enabled,on?1:0);unlock_settings();return JNI_TRUE;
}
JNIEXPORT void JNICALL Java_com_prismml_bonsailocal_repair_Bridge_start(JNIEnv *e,jobject o){
    (void)e;(void)o;lock_settings();int s=atomic_load(&state);
    if(initialized&&atomic_load(&command)==0&&(s==0||s==4||s==5)){atomic_store(&state,2);atomic_store(&command,1);}unlock_settings();
}
JNIEXPORT void JNICALL Java_com_prismml_bonsailocal_repair_Bridge_stop(JNIEnv *e,jobject o){(void)e;(void)o;lock_settings();if(initialized)atomic_store(&command,2);unlock_settings();}
JNIEXPORT void JNICALL Java_com_prismml_bonsailocal_repair_Bridge_selfTest(JNIEnv *e,jobject o){
    (void)e;(void)o;lock_settings();int s=atomic_load(&state);
    if(initialized&&atomic_load(&command)==0&&(s==0||s==4||s==5)){atomic_store(&state,6);atomic_store(&command,3);}unlock_settings();
}
JNIEXPORT void JNICALL Java_com_prismml_bonsailocal_repair_Bridge_shutdown(JNIEnv *e,jclass c) {
    (void)c;if(!initialized)return;atomic_store(&stop_flag,1);pthread_join(worker,0);
    if(app_context)(*e)->DeleteGlobalRef(e,app_context);app_context=0;initialized=0;child=0;
}
