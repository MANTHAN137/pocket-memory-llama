#!/usr/bin/env python3
"""Build an explicitly edited walkthrough from verified, real Android captures."""
from pathlib import Path
import json,subprocess,textwrap,re,hashlib,os,shutil
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'.tooling/media-work';WORK.mkdir(parents=True,exist_ok=True)
OUT=ROOT/'docs/images';VIDEO=ROOT/'releases';VIDEO.mkdir(exist_ok=True)
FONT='/System/Library/Fonts/Supplemental/Arial.ttf'
BOLD='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
INK='#201e25';PAPER='#f4f0f9';MUTED='#5e5667';PINK='#ff8bd2';AQUA='#adf2eb';YELLOW='#f6ec6b'
def font(size,bold=False):return ImageFont.truetype(BOLD if bold else FONT,size)
def wrapped(draw,text,xy,size=37,width=850,bold=False,fill=INK):
 f=font(size,bold);words=text.split();lines=[];line=''
 for w in words:
  trial=(line+' '+w).strip()
  if draw.textlength(trial,font=f)>width and line:lines.append(line);line=w
  else:line=trial
 if line:lines.append(line)
 x,y=xy
 for line in lines:draw.text((x,y),line,font=f,fill=fill);y+=int(size*1.35)
 return y
SCENES=[
 ('08-llama-model.png','BUILT WITH LLAMA','Saved instructions. Reviewed action.','Pocket Memory’s Llama hackathon edition uses Meta Llama 3.2 3B for source-backed task planning. Inference runs locally.',
  'Meet Pocket Memory, Llama hackathon edition. It uses Meta Llama three point two, three B, on Android. These are actual app captures, edited into a walkthrough with fictional data.'),
 ('01-sources.png','01 / READ THE SOURCES','One goal. Five scattered notes.','Arrival, equipment, venue, follow-up — plus a separate party that shares the same client name.',
  'Our fictional Lakeview client demo has arrival instructions, a packing list, a venue and a follow-up. A separate team party shares the same name. Keyword matching supplies candidates, but it cannot decide which event belongs in this plan.'),
 ('02-review.png','02 / LLAMA PROPOSES','Select. Order phases. Review.','Llama chooses candidate IDs. The app copies exact source actions. Planning creates zero tasks.',
  'Llama selects relevant candidate identifiers. The app enforces explicit workflow phases, putting preparation before arrival, access and after-event follow-up. A grammar restricts its output, and Java checks the identifiers and duplicates. The app copies original source action text. The model cannot invent executable task text. Planning alone creates no tasks. You review the proposal.'),
 ('03-evidence.png','03 / INSPECT EVIDENCE','Every action has a source.','Inspect the original instruction and its revision. Select only the actions you want to add.',
  'Open the evidence behind an action. The proposal keeps the source wording and revision used during planning. You can inspect the context and uncheck actions. These checks constrain model authority, but they do not prove that its relevance or ordering is correct.'),
 ('04-stale.png','04 / BLOCK STALE PLANS','A changed room stops the batch.','Edit Cedar to Orchid before applying. The old proposal is blocked and the transaction creates zero tasks.',
  'Now the venue changes from room Cedar to Orchid. The saved source version no longer matches the proposal. The review screen blocks apply. The same check runs inside the database transaction, so an outdated plan cannot create a partial batch.'),
 ('05-ready-to-apply.png','05 / REBUILD AND APPROVE','Current evidence. Explicit approval.','Build again with Llama, inspect the new source and add the reviewed actions atomically.',
  'Build again with real Llama inference. The fresh proposal uses the current source and still excludes the unrelated party. Review the selected steps and approve. SQLite commits the whole local task batch atomically. Repeating apply does not duplicate the work.'),
 ('06-undo.png','06 / RECOVER SAFELY','Undo protects work already done.','Only unchanged, incomplete tasks created by this batch are removed. Reused tasks are preserved.',
  'The new batch can be undone while its created tasks remain unchanged and incomplete. Completing one created task blocks the entire undo. Reused tasks are preserved. Reapplying an undone plan requires another explicit review. Recovery is application logic, not a promise from the model.'),
 ('09-restarted-tasks.png','07 / KEEP THE RESULT','Reopen. Your tasks are still there.','Verified in airplane mode after an actual process restart. Source, tests, APK and documentation are public.',
  'After an actual Android process restart, the approved tasks and batch links remain. The app does not rerun Llama or execute again automatically. This offline emulator flow verifies the new planner, not phone speed. Physical-device profiling and broader quality testing remain pending. Explore the source, evidence and standalone preview in the public repository.')
]
frames=[]
for i,(shot,kicker,title,body,narration) in enumerate(SCENES):
 im=Image.new('RGB',(1920,1080),PAPER);d=ImageDraw.Draw(im)
 for x in range(0,1920,48):d.line((x,0,x,1080),fill='#e1dce8',width=1)
 for y in range(0,1080,48):d.line((0,y,1920,y),fill='#e1dce8',width=1)
 d.rounded_rectangle((76,72,1114,964),30,fill=INK)
 d.rounded_rectangle((64,60,1102,952),30,fill='#fffdfd',outline=INK,width=4)
 d.rounded_rectangle((114,112,620,166),24,fill=AQUA,outline=INK,width=3)
 d.text((140,122),kicker,font=font(29,True),fill=INK)
 y=wrapped(d,title,(116,225),size=73,width=870,bold=True)
 y=wrapped(d,body,(118,y+42),size=36,width=850,fill=MUTED)
 d.rounded_rectangle((118,757,1010,854),20,fill=PINK,outline=INK,width=3)
 wrapped(d,'PROPOSE  >  REVIEW  >  APPLY',(150,782),size=36,width=830,bold=True)
 d.text((118,892),'Pocket Memory | Llama hackathon preview',font=font(23),fill=MUTED)
 phone=Image.open(OUT/shot).convert('RGB');phone.thumbnail((560,962),Image.Resampling.LANCZOS)
 px=1275+(540-phone.width)//2;py=55+(962-phone.height)//2
 d.rounded_rectangle((px-10,py-10,px+phone.width+15,py+phone.height+18),32,fill=INK)
 im.paste(phone,(px,py))
 d.text((66,1015),'ACTUAL APP CAPTURES | EDITED WALKTHROUGH | FICTIONAL DATA | NO SPEED CLAIM',font=font(23,True),fill=INK)
 d.text((1170,1015),f'{i+1:02d} / 08',font=font(23,True),fill=INK)
 path=WORK/f'scene-{i:02d}.png';im.save(path,optimize=True);frames.append(im)
 (WORK/f'voice-{i:02d}.txt').write_text(narration)
# A short animated overview. Each card comes from the real app; transitions do not simulate clicks.
gif=[];durations=[]
small=[im.resize((960,540),Image.Resampling.LANCZOS) for im in frames]
for i,im in enumerate(small):
 gif.append(im);durations.append(1700)
 nxt=small[(i+1)%len(small)]
 for alpha in (.25,.5,.75):gif.append(Image.blend(im,nxt,alpha));durations.append(80)
gif[0].save(OUT/'walkthrough.gif',save_all=True,append_images=gif[1:],duration=durations,loop=0,optimize=True,disposal=2)
# Speech and MP4 use only system narration and original app captures. No soundtrack or reference media.
ffmpeg=Path(os.environ.get('FFMPEG') or shutil.which('ffmpeg') or 'ffmpeg')
clips=[];timeline=[];at=0
for i,scene in enumerate(SCENES):
 voice=WORK/f'voice-{i:02d}.aiff'
 subprocess.run(['/usr/bin/say','-v','Samantha','-r','170','-f',str(WORK/f'voice-{i:02d}.txt'),'-o',str(voice)],check=True)
 probe=subprocess.run([str(ffmpeg),'-i',str(voice)],capture_output=True,text=True)
 match=re.search(r'Duration: (\d+):(\d+):(\d+\.\d+)',probe.stderr)
 assert match,probe.stderr
 duration=int(match[1])*3600+int(match[2])*60+float(match[3])+1.5
 clip=WORK/f'clip-{i:02d}.mp4'
 subprocess.run([str(ffmpeg),'-y','-loglevel','error','-loop','1','-i',str(WORK/f'scene-{i:02d}.png'),'-i',str(voice),'-t',str(duration),'-vf','fade=t=in:st=0:d=0.35,format=yuv420p','-r','24','-c:v','libx264','-preset','veryfast','-crf','23','-threads','2','-af','apad,loudnorm=I=-16:TP=-1.5:LRA=9','-c:a','aac','-b:a','160k',str(clip)],check=True)
 clips.append(clip);timeline.append({'start':round(at,2),'duration':round(duration,2),'image':scene[0],'narration':scene[4]});at+=duration
assert at<180,f'Demo exceeds three minutes: {at}'
def stamp(t):
 ms=round(t*1000);return f"{ms//3600000:02d}:{ms//60000%60:02d}:{ms//1000%60:02d}.{ms%1000:03d}"
captions=['WEBVTT','']
for row in timeline:
 words=row['narration'].split();span=row['duration']-1.5
 for offset in range(0,len(words),16):
  piece=words[offset:offset+16];start=row['start']+offset/len(words)*span;end=row['start']+min(offset+16,len(words))/len(words)*span
  captions.extend([stamp(start)+' --> '+stamp(end),' '.join(piece),''])
(OUT.parent/'PocketMemory-Plans-Demo.vtt').write_text('\n'.join(captions))
listing=WORK/'clips.txt';listing.write_text(''.join("file '"+str(p)+"'\n" for p in clips))
video=VIDEO/'PocketMemory-Plans-Demo.mp4'
subprocess.run([str(ffmpeg),'-y','-loglevel','error','-f','concat','-safe','0','-i',str(listing),'-c','copy','-movflags','+faststart',str(video)],check=True)
subprocess.run([str(ffmpeg),'-v','error','-i',str(video),'-f','null','-'],check=True)
manifest={'description':'Edited walkthrough of actual Android captures, fictional data, real Meta Llama inference; not continuous video or a speed benchmark.','narration':'macOS Samantha system voice, synthesized locally; no music','duration_seconds':round(at,2),'dimensions':[1920,1080],'timeline':timeline,'video_sha256':hashlib.sha256(video.read_bytes()).hexdigest(),'screenshot_sha256':{s[0]:hashlib.sha256((OUT/s[0]).read_bytes()).hexdigest() for s in SCENES}}
(ROOT/'docs/evidence').mkdir(parents=True,exist_ok=True)
frames[0].save(OUT/'preview-poster.jpg',quality=92)
(ROOT/'docs/evidence/media-provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'video':str(video),'duration_seconds':round(at,2),'gif':str(OUT/'walkthrough.gif')}))
