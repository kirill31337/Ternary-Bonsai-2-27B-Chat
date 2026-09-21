#!/usr/bin/env python3
"""Build the ARM64 JNI bootstrap. Import stubs are link-time-only, never runtime code."""
import os, pathlib, subprocess, re
ROOT=pathlib.Path(__file__).resolve().parent
B=ROOT/'build'; B.mkdir(exist_ok=True)
clang=os.environ.get('CLANG','clang')
base=[clang,'--target=aarch64-linux-android28','-fuse-ld=lld','-fPIC','-ffreestanding','-O2','-std=c11','-mno-outline-atomics','-fstack-protector-strong','-Wall','-Wextra','-Werror','-Wno-unused-command-line-argument']
obj=B/'bonsai_app.o'
subprocess.run(base+['-I'+str(ROOT/'native/jni'),'-c',str(ROOT/'native/bonsai_app.c'),'-o',str(obj)],check=True)
sym=subprocess.check_output(['readelf','-Ws',str(obj)],text=True)
undefined={l.split()[-1] for l in sym.splitlines() if ' UND ' in l and len(l.split())>=8}
libc={'pthread_create','pthread_join','usleep','open','close','read','write','lseek','unlink','snprintf','strlen','memcpy','memset','memcmp','strtoll','getpagesize','__stack_chk_fail','__stack_chk_guard'}
liblog={'__android_log_write'}
assert undefined <= libc|liblog, 'Unexpected imports: '+str(undefined-libc-liblog)
for lib, names in [('libc',libc&undefined),('liblog',liblog&undefined)]:
    stub=B/(lib+'_import.c')
    stub.write_text('\n'.join('unsigned long __stack_chk_guard;' if n=='__stack_chk_guard' else 'void '+n+'(void) {}' for n in sorted(names)))
    subprocess.run([clang,'--target=aarch64-linux-android28','-fuse-ld=lld','-shared','-nostdlib','-fPIC','-fno-builtin','-Wl,-soname,'+lib+'.so',str(stub),'-o',str(B/(lib+'.so'))],check=True)
libdir=ROOT/'app/lib/arm64-v8a';libdir.mkdir(parents=True,exist_ok=True)
subprocess.run(base+['-shared','-nostdlib','-Wl,--no-undefined','-Wl,-z,relro','-Wl,-z,now','-Wl,-z,max-page-size=16384','-Wl,-z,common-page-size=4096','-Wl,-soname,libbonsai_app.so',str(obj),'-L'+str(B),'-Wl,--no-as-needed','-l:libc.so','-l:liblog.so','-o',str(libdir/'libbonsai_app.so')],check=True)
print('Built JNI bootstrap:',libdir/'libbonsai_app.so')
print('Explicit Android imports:', ', '.join(sorted(undefined)))
