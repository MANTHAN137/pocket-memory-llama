#!/usr/bin/env python3
"""Download the pinned Llama GGUF with size/SHA-256 verification; no tokens or account files."""
import hashlib,json,os,urllib.request
from pathlib import Path
root=Path(__file__).resolve().parents[1]
m=json.loads((root/'app/src/main/assets/models.json').read_text())[0]
folder=root/'verification';folder.mkdir(exist_ok=True)
target=folder/m['filename'];partial=target.with_suffix('.gguf.part')
def verified(path):
 if not path.exists() or path.stat().st_size!=m['bytes']:return False
 h=hashlib.sha256()
 with path.open('rb') as f:
  for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()==m['sha256']
if not verified(target):
 print('Download only after reviewing the bundled Llama licence and acceptable-use policy.')
 request=urllib.request.Request(m['url'],headers={'User-Agent':'PocketPlans-build/0.6.0'})
 with urllib.request.urlopen(request,timeout=90) as response,partial.open('wb') as out:
  total=0
  for chunk in iter(lambda:response.read(1024*1024),b''):
   total+=len(chunk)
   if total>m['bytes']:raise RuntimeError('Download exceeds pinned size')
   out.write(chunk)
 if not verified(partial):raise RuntimeError('Model size or SHA-256 verification failed')
 os.replace(partial,target)
print('Verified:',target.name,m['sha256'])
