#!/usr/bin/env python3
"""Offline checks for the single, pinned Meta language model in this edition."""
import json,re
from pathlib import Path
from urllib.parse import urlsplit
root=Path(__file__).resolve().parents[1]
models=json.loads((root/'app/src/main/assets/models.json').read_text())
assert len(models)==1
m=models[0]
assert m['id']=='llama-3.2-3b' and m['upstream']=='meta-llama/Llama-3.2-3B-Instruct'
assert m['licenseAsset']=='llama-3.2.txt' and not m['premium'] and not m['productId']
assert re.fullmatch('[a-f0-9]{40}',m['revision']) and re.fullmatch('[a-f0-9]{64}',m['sha256'])
assert m['bytes']==2019377696
u=urlsplit(m['url'])
assert u.scheme=='https' and u.hostname=='huggingface.co'
assert u.path==f"/{m['repo']}/resolve/{m['revision']}/{m['filename']}"
for name in ('llama-3.2.txt','llama-3.2-use-policy.txt'):
 assert (root/'app/src/main/assets/licenses'/name).is_file()
print('PASS: one free Meta Llama 3.2 3B model, pinned identity, size, SHA-256 and licence assets.')
