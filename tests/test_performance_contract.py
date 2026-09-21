"""Cross-language packaging invariants; runtime behavior covered by host JNI/adapter tests."""
from pathlib import Path
import os,zipfile,subprocess,tempfile,hashlib
R=Path(__file__).resolve().parents[1]
APK=Path(os.environ.get('TEST_APK',str(R/'build/BonsaiLocal-0.5-arm64.apk')))
P=R/'app/smali/com/prismml/bonsailocal/repair'
def test_six_argument_java_and_native_bridge_signature():
 s=(P/'Bridge.smali').read_text()
 assert 'configureOptions(IIZIII)Z' in s
 assert 'configure(IZ)' not in s
 c=(R/'native/bonsai_app.c').read_text()
 assert 'Bridge_configureOptions' in c
 assert '7206168928LL' in c and '5946648928LL' in c

def test_options_and_benchmarks_in_diagnostics():
 s=(P/'MainActivity.smali').read_text()
 assert 'runtime-options.properties' in s and 'benchmarks.jsonl' in s
 assert 'LocalBenchmark;->cancel()V' in s
 assert 'sput-boolean v0, Lcom/prismml/bonsailocal/repair/MainActivity;->loaded:Z' in s

def test_pq2_arm_matrix_kernels_in_packaged_runtime():
 with zipfile.ZipFile(APK) as z:
  b=z.read('lib/arm64-v8a/libggml-cpu-android_armv8.6_1.so')
  assert b'ggml_gemm_pq2_0_4x8_q8_0' in b
  assert b'ggml_gemv_pq2_0_4x8_q8_0' in b
  assert b'pq2_0' in z.read('lib/arm64-v8a/libggml-base.so')

def test_native_pq_context_thread_thinking_arguments_packaged():
 with zipfile.ZipFile(APK) as z:
  b=z.read('lib/arm64-v8a/libbonsai_app.so')
  for s in (b'PQ2_0.gguf',b'--threads-batch',b'--reasoning-budget',b'--chat-template-kwargs',b'--flash-attn',b'--cache-type-k',b'--cache-type-v'):
   assert s in b,s

def test_no_gpu_claim_or_remote_benchmark_endpoint():
 s=(R/'java/com/prismml/bonsailocal/repair/LocalBenchmark.java').read_text()
 assert 'http://127.0.0.1:18080/completion' in s
 assert 'setInstanceFollowRedirects(false)' in s
 assert '--n-gpu-layers","0"' in (R/'native/bonsai_app.c').read_text()

def test_inference_libraries_unchanged_from_working_04():
 previous=Path(os.environ.get('PREVIOUS_APK','/mnt/data/BonsaiLocal-0.4-arm64.apk'))
 if not previous.exists():return
 with zipfile.ZipFile(previous) as a,zipfile.ZipFile(APK) as b:
  for n in a.namelist():
   if n.startswith('lib/') and n.endswith('.so') and not n.endswith('/libbonsai_app.so'):
    assert hashlib.sha256(a.read(n)).digest()==hashlib.sha256(b.read(n)).digest(),n
