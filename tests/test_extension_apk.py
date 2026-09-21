"""Read actual delivered DEX/ELF, not source references or static signature stubs."""
import os,struct,subprocess,tempfile,zipfile
from pathlib import Path
from test_apk_contract import dex_classes
R=Path(__file__).resolve().parents[1]
APK=Path(os.environ.get('TEST_APK',R/'build/BonsaiLocal-1.0.0-arm64.apk'))

def test_new_web_and_vision_adapters_are_real_packaged_classes():
 with zipfile.ZipFile(APK) as z:
  definitions=dex_classes(z.read('classes.dex'))
  for c in ('McpServer','WebNet','WebTools','MiniJson','Extensions','KeyVault','ChatBootstrap','ModelFiles','BonsaiChromeClient','LocalWebClient'):
   assert 'Lcom/prismml/bonsailocal/repair/'+c+';' in definitions,c
  assert not any('Test;' in c or 'Fixture;' in c or 'Dump;' in c for c in definitions)
  assert not any(c.startswith('Ljavax/') or c.startswith('Ljava/') or c.startswith('Landroid/') for c in definitions)

def test_actual_native_launch_exports_include_vision_configuration():
 with zipfile.ZipFile(APK) as z,tempfile.TemporaryDirectory() as d:
  p=Path(d)/'lib.so';p.write_bytes(z.read('lib/arm64-v8a/libbonsai_app.so'))
  symbols=subprocess.check_output(['readelf','--wide','--dyn-syms',str(p)],text=True)
  assert 'Java_com_prismml_bonsailocal_repair_Bridge_configureVision' in symbols
  b=p.read_bytes()
  assert b'--mmproj\0' in b and b'--no-mmproj-offload\0' in b and b'--image-max-tokens\0' in b

def test_compiled_video_support_is_off_and_ui_does_not_fake_it():
 with zipfile.ZipFile(APK) as z,tempfile.TemporaryDirectory() as d:
  b=z.read('lib/arm64-v8a/libmtmd.so');p=Path(d)/'lib.so';p.write_bytes(b)
  symbols=subprocess.check_output(['readelf','--wide','--dyn-syms',str(p)],text=True)
  row=next(l for l in symbols.splitlines() if l.split()[-1:] == ['mtmd_helper_support_video']).split()
  addr=int(row[1],16);length=int(row[2]);phoff=struct.unpack_from('<Q',b,32)[0];entsize,count=struct.unpack_from('<HH',b,54)
  instructions=None
  for i in range(count):
   typ,flags,offset,vaddr,paddr,filesz,memsz,align=struct.unpack_from('<IIQQQQQQ',b,phoff+i*entsize)
   if typ==1 and vaddr<=addr< vaddr+filesz:instructions=b[offset+addr-vaddr:offset+addr-vaddr+length]
  assert instructions in (bytes.fromhex('00008052c0035fd6'),bytes.fromhex('e0031f2ac0035fd6')), 'AArch64 mov w0,#0 or mov w0,wzr;ret proves this build has no video'
  assert 'MTMD_VIDEO=OFF' in z.read('assets/index.html').decode()
