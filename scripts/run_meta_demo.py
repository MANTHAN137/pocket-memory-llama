#!/usr/bin/env python3
"""Install and run fictional fixtures only in an isolated Android emulator."""
import argparse,hashlib,json,os,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);args=p.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('Refusing a physical-device serial. Use an isolated emulator.')
adb=str(Path(os.environ['ANDROID_HOME'])/'platform-tools/adb')
def run(*parts,**kwargs):return subprocess.run([adb,'-s',args.serial,*parts],check=True,**kwargs)
fingerprint=run('shell','getprop','ro.build.fingerprint',capture_output=True,text=True).stdout
if not any(s in fingerprint for s in ['generic','sdk_gphone','emulator']):raise SystemExit('Target is not a recognized emulator.')
m=json.loads((root/'app/src/main/assets/models.json').read_text())[0]
model=root/'verification'/m['filename'];h=hashlib.sha256()
with model.open('rb') as f:
 for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
assert model.stat().st_size==m['bytes'] and h.hexdigest()==m['sha256'],'Run scripts/download_model.py first'
run('install','-r',str(root/'app/build/outputs/apk/debug/app-debug.apk'))
run('install','-r','-t',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
package='com.pocketmemory.llama'
local='files/models/'+m['id']+'-'+m['filename']
run('shell','run-as',package,'mkdir','-p','files/models')
# Stream once into the app sandbox, avoiding a second full-size emulator copy.
with model.open('rb') as source:
 run('shell',"run-as "+package+" sh -c 'cat > "+local+"'",stdin=source)
run('shell',"run-as "+package+" sh -c 'cat > "+local+".verified'",input=m['sha256'].encode())
size=int(run('shell','run-as',package,'stat','-c','%s',local,capture_output=True,text=True).stdout.strip())
assert size==m['bytes'],'Emulator model copy was truncated; free test-emulator storage'
# This changes only the test emulator's connectivity; the walkthrough intentionally remains offline.
run('shell','cmd','connectivity','airplane-mode','enable');run('shell','svc','wifi','disable');run('shell','svc','data','disable')
log=root/'verification/test-meta-llama.txt'
with log.open('w') as out:run('shell','am','instrument','-w',package+'.test/com.pocketmemory.app.OfflineInstrumentation',stdout=out,stderr=subprocess.STDOUT)
body=log.read_text();print(body)
if 'ALL CHECKS PASSED' not in body or '\nFAIL:' in body:raise SystemExit('Planner workflow failed; inspect '+str(log))
run('shell','am','force-stop',package)
restart=root/'verification/test-planner-restart.txt'
with restart.open('w') as out:run('shell','am','instrument','-w','-e','stage','restart',package+'.test/com.pocketmemory.app.OfflineInstrumentation',stdout=out,stderr=subprocess.STDOUT)
text=restart.read_text();print(text)
if 'ALL CHECKS PASSED' not in text or '\nFAIL:' in text:raise SystemExit('Restart verification failed')
images=root/'docs/images';images.mkdir(parents=True,exist_ok=True)
for name in ['01-sources','02-review','03-evidence','04-stale','05-ready-to-apply','06-undo','07-created-tasks','08-llama-model','09-restarted-tasks']:
 result=run('exec-out','run-as',package,'cat','files/plan-'+name+'.png',capture_output=True)
 (images/(name+'.png')).write_bytes(result.stdout)
print('Passed. The new Llama planner remains open with its reviewed tasks.')
