#!/usr/bin/env python3
"""Run fictional-data checks only on an isolated, already-configured emulator."""
import argparse,os,pathlib,subprocess
root=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--serial',required=True)
p.add_argument('--stage',choices=['show','core','web','download','restart','guards'],default='guards')
a=p.parse_args()
if not a.serial.startswith('emulator-'):
    raise SystemExit('Refusing a physical device. Use an isolated emulator.')
adb=str(pathlib.Path(os.environ['ANDROID_HOME'])/'platform-tools/adb')
base=[adb,'-s',a.serial]
def run(*args,**kwargs):return subprocess.run(base+list(args),check=True,**kwargs)
fingerprint=run('shell','getprop','ro.build.fingerprint',capture_output=True,text=True).stdout
if not any(x in fingerprint for x in ['generic','sdk_gphone']):raise SystemExit('Emulator fingerprint required.')
run('install','-r',str(root/'releases/PocketMemory-Llama-0.8.0.apk'))
run('install','-r',str(root/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
result=run('shell','am','instrument','-w','-e','stage',a.stage,'com.pocketmemory.llama.test/com.pocketmemory.app.EditionInstrumentation',capture_output=True,text=True)
print(result.stdout,end='')
if 'ALL CHECKS PASSED' not in result.stdout:raise SystemExit('Stage failed. Review output.')
