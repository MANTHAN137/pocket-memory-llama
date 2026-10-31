#!/usr/bin/env python3
"""Verify every packaged ARM64 ELF LOAD segment has at least 16 KB alignment."""
import argparse,struct,zipfile
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('archive');args=parser.parse_args();count=0
with zipfile.ZipFile(args.archive) as archive:
 for name in archive.namelist():
  if '/arm64-v8a/' not in name or not name.endswith('.so'):continue
  body=archive.read(name)
  if body[:6]!=b'\x7fELF\x02\x01':raise SystemExit('Unsupported native binary: '+name)
  offset=struct.unpack_from('<Q',body,32)[0];size,total=struct.unpack_from('<HH',body,54)
  headers=[struct.unpack_from('<IIQQQQQQ',body,offset+i*size) for i in range(total)];loads=[p for p in headers if p[0]==1]
  if not loads or any(p[-1]<16384 or p[2]%16384!=p[3]%16384 for p in loads):raise SystemExit('16 KB alignment failed: '+name)
  count+=1;print('PASS:',name)
if count<1:raise SystemExit('Expected the Llama inference native library')
print('All packaged ARM64 LOAD segments meet 16 KB alignment. A runtime test is still required.')
