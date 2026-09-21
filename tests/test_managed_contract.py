"""Source-level guards, not Android UI execution tests."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
MAIN=(ROOT/'app/smali/com/prismml/bonsailocal/repair/MainActivity.smali').read_text()
HTML=(ROOT/'app/assets/index.html').read_text()

def method(name):
    return MAIN.split('.method '+name,1)[1].split('.end method',1)[0]

def test_launch_screen_does_not_initialize_native_or_webview():
    launch=method('protected onCreate(')+method('public home(')
    assert 'loadLibrary' not in launch
    assert 'Bridge;->init' not in launch
    assert 'Landroid/webkit/WebView;' not in launch

def test_confirmation_dialog_has_webchromeclient():
    # Without a WebChromeClient Android suppresses confirm(), preventing download consent.
    assert 'confirm(' in HTML
    assert '->setWebChromeClient(Landroid/webkit/WebChromeClient;)V' in MAIN

def test_hidden_controls_remain_hidden_despite_display_block():
    assert '[hidden]{display:none!important}' in HTML.replace(' ','')
