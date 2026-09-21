"""Cross-boundary integration guards in addition to executable Java/HTTP/JS tests."""
from pathlib import Path
import zipfile,os
R=Path(__file__).resolve().parents[1]
P=R/'app/smali/com/prismml/bonsailocal/repair'
def test_native_projector_is_a_real_launch_argument():
 s=(R/'native/bonsai_app.c').read_text()
 assert 'Bridge_configureVision' in s and '"--mmproj"' in s and '"--no-mmproj-offload"' in s
 assert '629246976' in s, 'Verify exact installed projector before starting'
def test_file_chooser_is_wired_to_native_activity_result():
 s=(P/'MainActivity.smali').read_text()
 assert 'BonsaiChromeClient;-><init>' in s
 assert 'BonsaiChromeClient;->result' in s
 assert 'BonsaiChromeClient;->cancel' in s
 assert 'setAllowContentAccess(Z)V' in s
 defseq=s[s.index('setAllowUniversalAccessFromFileURLs'):s.index('setWebViewClient')]
 assert 'const/4 v2, 0x1' in defseq

def test_old_chat_origin_and_browser_storage_are_preserved():
 h=(R/'app/assets/index.html').read_text()
 assert 'http://127.0.0.1:18080/bonsai-connect' in h
 assert 'Удалить' in h and 'mmproj' in h and 'Интернет' in h
 s=(R/'java/com/prismml/bonsailocal/repair/ChatBootstrap.java').read_text()
 assert 'LlamaUi.config' in s and 'LlamaUi.alwaysAllowedTools' in s
 assert 'web_search' in s and 'web_fetch' in s
 assert 'localStorage.clear' not in s
