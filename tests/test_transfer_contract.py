"""Static Android integration guards; not an Android runtime test."""
from pathlib import Path
import xml.etree.ElementTree as ET
R=Path(__file__).resolve().parents[1]
A='{http://schemas.android.com/apk/res/android}'

def test_foreground_service_declared_private_and_kept_on_task_removal():
 root=ET.parse(R/'app/AndroidManifest.xml').getroot()
 service=root.find('./application/service')
 assert service is not None, 'No background transfer service'
 assert service.get(A+'exported')=='false'
 assert service.get(A+'stopWithTask')=='false'
 assert service.get(A+'foregroundServiceType')=='dataSync'
 perms={x.get(A+'name') for x in root.findall('uses-permission')}
 assert {'android.permission.FOREGROUND_SERVICE','android.permission.WAKE_LOCK'}<=perms

def test_document_picker_accepts_unknown_gguf_mime_and_has_result_handler():
 s=(R/'app/smali/com/prismml/bonsailocal/repair/MainActivity.smali').read_text()
 assert 'android.intent.action.OPEN_DOCUMENT' in s, 'No system picker'
 assert '"*/*"' in s
 assert 'onActivityResult(IILandroid/content/Intent;)V' in s
 assert 'takePersistableUriPermission' in s
 assert 'TransferFacade;-><init>' in s

def test_update_preserves_package_and_raises_version():
 root=ET.parse(R/'app/AndroidManifest.xml').getroot()
 assert root.get('package')=='com.prismml.bonsailocal.repair'
 assert int(root.get(A+'versionCode'))>2

def test_pinned_model_identity_not_mutable_main():
 s=(R/'java/com/prismml/bonsailocal/repair/ModelCatalog.java').read_text()
 assert '5946648928L' in s
 assert '7206168928L' in s
 assert '3907dc1658db1f78a9826bf8d5bcb8dc65db0d466388937af57f2294fae62ec1' in s
 assert '53107f530aa52eb00912263ab1ee29bd199261c87cd7b4ad4ca1318c1fe33ee3' in s
 assert '/resolve/main/' not in s
