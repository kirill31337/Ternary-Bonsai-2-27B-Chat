"""Packaging regression checks. These do not substitute for an Android device test."""
import os, struct, subprocess, tempfile, zipfile
from pathlib import Path
APK = Path(os.environ.get('TEST_APK', str(Path(__file__).resolve().parents[1]/'build/unsigned.apk')))

def test_resource_table_can_be_memory_mapped_by_android_installer():
    # Required for target SDK >= 30; valid signatures alone do not imply installability.
    # Inspect the delivered ZIP payload, not the requested Apktool configuration.
    with zipfile.ZipFile(APK) as z, APK.open('rb') as raw:
        entry = z.getinfo('resources.arsc')
        assert entry.compress_type == zipfile.ZIP_STORED, 'Android 11+ rejects compressed resources.arsc'
        raw.seek(entry.header_offset)
        header = raw.read(30)
        assert header[:4] == b'PK\x03\x04'
        assert struct.unpack_from('<H', header, 8)[0] == zipfile.ZIP_STORED
        name_size, extra_size = struct.unpack_from('<HH', header, 26)
        data_offset = entry.header_offset + 30 + name_size + extra_size
        assert data_offset % 4 == 0, f'resources.arsc payload is not 4-byte aligned: {data_offset}'

def test_managed_entry_point_available_before_native_load():
    with zipfile.ZipFile(APK) as z:
        assert 'classes.dex' in z.namelist(), 'No managed recovery screen: launch depends on NativeActivity loading successfully'
        dex = z.read('classes.dex')
        assert b'com/prismml/bonsailocal/repair/MainActivity' in dex

def test_bootstrap_explicitly_links_android_libc():
    with zipfile.ZipFile(APK) as z, tempfile.TemporaryDirectory() as d:
        p=Path(d)/'lib.so';p.write_bytes(z.read('lib/arm64-v8a/libbonsai_app.so'))
        out=subprocess.check_output(['readelf','-d',str(p)],text=True)
        assert 'Shared library: [libc.so]' in out, 'Native bootstrap has unresolved libc symbols but no DT_NEEDED libc.so'

def test_bootstrap_explicitly_links_android_logging():
    with zipfile.ZipFile(APK) as z, tempfile.TemporaryDirectory() as d:
        p=Path(d)/'lib.so';p.write_bytes(z.read('lib/arm64-v8a/libbonsai_app.so'))
        out=subprocess.check_output(['readelf','-d',str(p)],text=True)
        assert 'Shared library: [liblog.so]' in out

def test_all_arm64_load_segments_are_16k_compatible():
    with zipfile.ZipFile(APK) as z:
        for n in z.namelist():
            if not n.endswith('.so'): continue
            b=z.read(n)
            assert b[:6]==b'\x7fELF\x02\x01', n
            assert struct.unpack_from('<H',b,18)[0]==183,n
            off=struct.unpack_from('<Q',b,32)[0];size,count=struct.unpack_from('<HH',b,54)
            for i in range(count):
                typ,flags,fo,va,pa,fs,ms,align=struct.unpack_from('<IIQQQQQQ',b,off+i*size)
                if typ==1:
                    assert align>=16384,(n,align)
                    assert (va-fo)%16384==0,n

def test_runtime_is_not_replaced_with_stock_llama():
    with zipfile.ZipFile(APK) as z:
        assert b'ptq1_0' in z.read('lib/arm64-v8a/libggml-base.so').lower()

def dex_classes(dex):
    """Read actual DEX class definitions, not references to compile-only signatures."""
    nstr,ostr,ntype,otype=struct.unpack_from('<IIII',dex,56)
    def get_string(i):
        off=struct.unpack_from('<I',dex,ostr+i*4)[0]
        while dex[off]&128: off+=1
        off+=1
        return dex[off:dex.index(b'\0',off)].decode('utf-8','replace')
    types=[get_string(struct.unpack_from('<I',dex,otype+i*4)[0]) for i in range(ntype)]
    n,off=struct.unpack_from('<II',dex,96)
    return {types[struct.unpack_from('<I',dex,off+i*32)[0]] for i in range(n)}

def test_transfer_classes_are_packaged_and_api_signatures_are_not():
    with zipfile.ZipFile(APK) as z:
        classes=dex_classes(z.read('classes.dex'))
    prefix='Lcom/prismml/bonsailocal/repair/'
    for name in ('MainActivity','Bridge','TransferEngine','Transfers','TransferService','TransferFacade','ModelCatalog','RuntimeOptions','LocalBenchmark'):
        assert prefix+name+';' in classes, name
    assert not any(c.startswith('Landroid/') for c in classes), 'Compile-only Android signatures packaged!'
    assert not any('Test;' in c for c in classes), 'Host test fixture packaged!'

def test_archive_crc_and_transfer_controls():
    with zipfile.ZipFile(APK) as z:
        assert z.testzip() is None
        html=z.read('assets/index.html').decode('utf-8')
        assert 'Выбрать скачанный GGUF' in html
        assert 'Скачать в 8 соединений' in html
        assert 'TransferEngine' in str(dex_classes(z.read('classes.dex')))
