"""Structural gates complement the Forge integration tests; no local Java compilation."""
import json,math,re
from pathlib import Path
r=Path(__file__).resolve().parents[1]
for p in (r/'src/main/resources').rglob('*.json'):json.loads(p.read_text())
rig=json.loads((r/'src/main/resources/assets/hexsadaharu/models/entity/sadaharu.json').read_text())
names=set()
for b in rig['bones']:
 assert b['name'] not in names
 assert b['parent'] is None or b['parent'] in names
 names.add(b['name'])
 for c in b['cubes']:
  assert all(x>0 for x in c['size'])
  w,h,d=c['size']
  assert 2*(w+d)<=128 and h+d<=128, b['name']    # one flat 128px palette cell per cuboid
 for m in b.get('meshes',[]):
  assert 0<=m['material']<len(rig['palette'])
  if 'vertices' in m:
   for face in m['faces']:
    assert 3<=len(face)<=4 and all(0<=i<len(m['vertices']) for i in face)
   assert all(math.isfinite(v) for point in m['vertices'] for v in point)
  else:
   assert all(v>0 for v in m['size']) and 2<=m['power']<=2.25
   assert 8<=m['rings']<=16 and 12<=m['segments']<=24
   assert all(v>0 for v in m['taper'])
for b in ['root' ,'body','chest','neck','head','collar','upper_jaw','lower_jaw','tongue','nose','ear_left','ear_right','tail_0','tail_1','tail_2','tail_3','brow_left','brow_right','eye_left','eye_right','eyelid_left','eyelid_right']:
 assert b in names,b
for s in ['front_left','front_right','rear_left','rear_right']:
 assert s+'_leg' in names and s+'_paw' in names

# The coat is one connected manifold per anatomical shell, not overlapping primitives.
from collections import Counter,defaultdict
assert len(rig['coats'])==2
for coat in rig['coats']:
 assert set(coat['bones'])<=names
 count=len(coat['vertices']);surface=coat['surface_vertices']
 assert count==len(coat['normals'])==len(coat['weights'])==len(coat['shade'])
 edges=Counter();neighbors=defaultdict(set)
 for f in coat['faces']:
  assert len(set(f))==3 and all(0<=i<count for i in f)
  if max(f)<surface:
   for a,b in zip(f,f[1:]+f[:1]):
    edges[tuple(sorted((a,b)))]+=1;neighbors[a].add(b);neighbors[b].add(a)
 assert all(n==2 for n in edges.values()),'coat surface has an open seam'
 seen=set();pending=[0]
 while pending:
  i=pending.pop()
  if i in seen:continue
  seen.add(i);pending.extend(neighbors[i]-seen)
 assert len(seen)==surface,'coat consists of disconnected pieces'
 for point,normal,weights in zip(coat['vertices'],coat['normals'],coat['weights']):
  assert all(math.isfinite(v) for v in point+normal)
  assert abs(sum(v*v for v in normal)-1)<.001
  assert 1<=len(weights)<=4 and abs(sum(w for _,w in weights)-1)<.00001
  assert all(0<=j<len(coat['bones']) and w>=0 for j,w in weights)
 # Hair tips inherit exactly the root weights, so they cannot slide off in motion.
 for start in range(surface,count,4):
  assert all(coat['weights'][i]==coat['weights'][start] for i in range(start,start+4))

# ---- Rest-pose containment ----------------------------------------------------------------
# Sadaharu reads as cute only while his mouth is genuinely shut: no dark cavity, tongue or lid
# may be visible until an animation opens it. That is geometry, so it is checked as geometry.
def mul3(a,b): return [[sum(a[i][k]*b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]
def mul4(a,b):
 m=[[sum(a[i][k]*b[k][j] for k in range(4)) for j in range(4)] for i in range(4)];return m
def rigid(pivot,rot):
 rx,ry,rz=(math.radians(v) for v in rot)
 cx,sx=math.cos(rx),math.sin(rx);cy,sy=math.cos(ry),math.sin(ry);cz,sz=math.cos(rz),math.sin(rz)
 m=mul3(mul3([[cz,-sz,0],[sz,cz,0],[0,0,1]],[[cy,0,sy],[0,1,0],[-sy,0,cy]]),[[1,0,0],[0,cx,-sx],[0,sx,cx]])
 return [m[0]+[pivot[0]],m[1]+[pivot[1]],m[2]+[pivot[2]],[0,0,0,1]]
def apply(m,p): return [sum(m[i][j]*p[j] for j in range(3))+m[i][3] for i in range(3)]
def inverse(m):
 t=[[m[j][i] for j in range(3)] for i in range(3)]
 o=[-sum(t[i][j]*m[j][3] for j in range(3)) for i in range(3)]
 return [t[0]+[o[0]],t[1]+[o[1]],t[2]+[o[2]],[0,0,0,1]]
world={}
for b in rig['bones']:
 m=rigid(b['pivot'],b['rotation'])
 world[b['name']]=m if b['parent'] is None else mul4(world[b['parent']],m)
by={b['name']:b for b in rig['bones']}
def shell(prefixes):
 out=[]
 for b in rig['bones']:
  if any(b['name']==p or b['name'].startswith(p) for p in prefixes):
   inv=inverse(world[b['name']])
   out+=[(inv,c) for c in b['cubes']+b.get('meshes',[]) if c['material'] in (0,1)]
 return out
def samples(o,s):
 pts=[]
 for i in range(5):
  for j in range(5):
   u,v=i/4,j/4
   pts+=[[o[0]+s[0]*u,o[1]+s[1]*v,o[2]],[o[0]+s[0]*u,o[1]+s[1]*v,o[2]+s[2]],
         [o[0]+s[0]*u,o[1],o[2]+s[2]*v],[o[0]+s[0]*u,o[1]+s[1],o[2]+s[2]*v],
         [o[0],o[1]+s[1]*u,o[2]+s[2]*v],[o[0]+s[0],o[1]+s[1]*u,o[2]+s[2]*v]]
 return pts
def mesh_samples(c):
 if 'origin' in c:return samples(c['origin'],c['size'])
 assert 'center' in c
 pts=[]
 for i in range(c['rings']+1):
  a=-math.pi/2+math.pi*i/c['rings'];y=math.copysign(abs(math.sin(a))**(2/c['power']),math.sin(a));g=c['taper'][0]+(c['taper'][1]-c['taper'][0])*(y+1)/2
  for j in range(c['segments']):
   t=2*math.pi*j/c['segments'];k=abs(math.cos(a))**(2/c['power'])
   x=k*math.copysign(abs(math.cos(t))**(2/c['power']),math.cos(t));z=k*math.copysign(abs(math.sin(t))**(2/c['power']),math.sin(t))
   pts.append([c['center'][0]+x*c['size'][0]/2*g,c['center'][1]+y*c['size'][1]/2,c['center'][2]+z*c['size'][2]/2*g])
 return pts
def contains(c,p):
 if 'origin' in c:return all(c['origin'][i]-.04<=p[i]<=c['origin'][i]+c['size'][i]+.04 for i in range(3))
 if 'center' not in c:return False
 q=[(p[i]-c['center'][i])/(c['size'][i]/2) for i in range(3)]
 g=c['taper'][0]+(c['taper'][1]-c['taper'][0])*(q[1]+1)/2
 return g>0 and abs(q[0]/g)**c['power']+abs(q[1])**c['power']+abs(q[2]/g)**c['power']<=1.025

def buried(bone,shapes,cover,label):
 for c in shapes:
  for p in mesh_samples(c):
   q=apply(world[bone],p)
   assert any(contains(shape,apply(inv,q)) for inv,shape in cover),label+' is exposed at rest near '+str([round(v,2) for v in q])
mouth=shell(['head','cheek_','upper_jaw','lower_jaw'])
for name in ('upper_jaw','lower_jaw','tongue'):
 buried(name,[c for c in by[name]['cubes']+by[name].get('meshes',[]) if c['material'] in (6,7)],mouth,name+' mouth interior')
# Closed eyes are now separate smile marks, explicitly hidden until the blink completes.
for name in ('eyelid_left','eyelid_right'):
 assert by[name].get('meshes') and not by[name]['cubes']

# The render smoke test poses parts directly and never calls setupAnim, so a bone the
# animation controller names but the rig does not have would only fail in a live world.
model=(r/'src/main/java/com/hex/sadaharu/client/SadaharuModel.java').read_text()
for ref in sorted(set(re.findall(r'(?:part|rot|move)\("([a-z0-9_]+)"',model))):
 if ref.endswith('_'):continue                       # a concatenation prefix such as "tail_"+i
 assert ref in names,'SadaharuModel drives missing bone '+ref

src='\n'.join(p.read_text() for p in (r/'src/main/java').rglob('*.java'))
personality=(r/'src/main/java/com/hex/sadaharu/Personality.java').read_text()

# The screen and the menu are wired by raw integers, so nothing but a check keeps a
# button from calling a case that does not exist, or from landing off the panel.
screen=(r/'src/main/java/com/hex/sadaharu/client/SadaharuScreen.java').read_text()
menu=(r/'src/main/java/com/hex/sadaharu/SadaharuMenu.java').read_text()
handled={int(n) for n in re.findall(r'case (\d+)\s*->',menu.split('clickMenuButton')[1])}
clicked={int(n) for n in re.findall(r'send\((\d+)\)',screen)}
assert clicked<=handled,'screen sends unhandled button ids '+str(clicked-handled)
assert handled<=clicked,'menu handles button ids no screen button sends: '+str(handled-clicked)
height=int(re.search(r'imageHeight=(\d+)',screen).group(1))
boxes=[tuple(int(v) for v in m) for m in re.findall(r'bounds\(leftPos\+(\d+),topPos\+(\d+),(\d+),(\d+)\)',screen)]
assert len(boxes)==len(clicked),'every button should carry explicit bounds'
for x,y,w,h in boxes:
 assert y+h<=height-12,'a button at y='+str(y)+' runs past the '+str(height)+'px panel'
 assert x+w<=int(re.search(r'imageWidth=(\d+)',screen).group(1)),'a button at x='+str(x)+' runs past the panel width'
for i,a in enumerate(boxes):
 for b in boxes[i+1:]:
  assert a[0]+a[2]<=b[0] or b[0]+b[2]<=a[0] or a[1]+a[3]<=b[1] or b[1]+b[3]<=a[1],'two buttons overlap: '+str(a)+' '+str(b)
label=int(re.search(r'calls him\.",\d+,(\d+),',screen).group(1))
assert all(y+h<=label for x,y,w,h in boxes),'a button overlaps the footer hint'
slots=int(re.search(r'SimpleContainerData\((\d+)\)',menu).group(1))
assert slots==int(re.search(r'getCount\(\)\{return (\d+);\}',menu).group(1)),'client and server data slot counts differ'
filled={int(n) for n in re.findall(r'case (\d+)\s*->',menu.split('ContainerData()')[1].split('public void set')[0])}
assert filled==set(range(slots)),'data slots declared but never filled: '+str(set(range(slots))-filled)

# Following is a persisted instruction, not a transient flag.
dogsrc=(r/'src/main/java/com/hex/sadaharu/Sadaharu.java').read_text()
for field in ('Following','AutomaticHome'):
 assert 'putBoolean("'+field+'"' in dogsrc,field+' is never written to NBT'
 assert 'getBoolean("'+field+'")' in dogsrc,field+' is never read back from NBT'
# The download has to unpack to the jar itself. Multiple paths in one artifact make
# GitHub preserve the directory tree, which is what turns it into a folder.
wf=(r/'.github/workflows/build.yml').read_text()
assert 'Confirm one ready-to-install jar' in wf,'nothing checks the jar is actually installable'
assert wf.count('uses: actions/upload-artifact')==2,'the jar and the diagnostics must be separate artifacts'
jar_step=wf.split('name: Upload the mod jar')[1].split('      - name:')[0]
assert 'path: build/libs/*.jar' in jar_step,'the jar artifact must name exactly one path'
assert 'path: |' not in jar_step,'a multi-path jar artifact downloads as a folder tree'
assert 'if-no-files-found: error' in jar_step,'a missing jar must fail the run rather than upload nothing'
for entry in ('META-INF/mods.toml','assets/hexsadaharu/sounds/bark.ogg'):
 assert entry in wf,'the jar check no longer verifies '+entry

# He is mortal now, so the old blanket guards must stay gone and the recovery must exist.
assert 'MAX_HEALTH,60' in dogsrc,'thirty hearts is the agreed maximum health'
assert 'isInvulnerableTo(DamageSource d) {return true;}' not in dogsrc,'he is invulnerable to everything again'
assert 'setHealth(float h) {super.setHealth(Float.isFinite(h)&&h>0?Math.max(h,getMaxHealth())' not in dogsrc,'setHealth still forces him back to full'
assert 'getHealth()!=getMaxHealth()||' not in dogsrc,'the tick loop still heals him back to full every tick'
assert re.search(r'if\(!\(amount>=getHealth\(\)\)\)return super\.hurt',dogsrc),'damage no longer reaches vanilla, so he cannot be hurt'
for needed in ('void collapse()','void recover()','downed=RECOVERY','super.setHealth(getMaxHealth())'):
 assert needed in dogsrc,'the knock-down cycle is missing: '+needed
# Recovery has to try home first, then the owner, and never leave him at zero health.
body=dogsrc[dogsrc.index('void recover()'):]
for step in ('SafeTravel.landing(destination,home,this)','o.blockPosition()'):
 assert step in body,'recovery never tries: '+step
assert body.index('SafeTravel.landing(destination,home,this)')<body.index('o.blockPosition()'),'recovery must prefer his home over his owner'
assert 'getSharedSpawnPos()' in dogsrc[dogsrc.index('void recover()'):],'recovery has no fallback when he has neither home nor owner'
# Mortality is not only the entity class. Forge's event bus cancelled every attack,
# every point of damage and his death independently of it, which is what actually kept
# him immortal after the entity guards came out.
events=(r/'src/main/java/com/hex/sadaharu/WorldEvents.java').read_text()
def handler(signature):
 for line in events.splitlines():
  if signature in line:return line
 raise AssertionError('WorldEvents has no handler for '+signature)
assert 'setCanceled(true)' not in handler('LivingAttackEvent e)'),'the event bus still cancels every attack on him'
assert 'setCanceled(true)' not in handler('LivingDamageEvent e)'),'the event bus still cancels all damage to him'
assert 'setHealth(dog.getMaxHealth())' not in handler('LivingDeathEvent e)'),'death still heals him to full instead of putting him down'
assert 'collapse()' in handler('LivingDeathEvent e)'),'a death that slips past hurt() must put him down'
assert 'Enemy&&e.getAmount()>0?10F:Math.min(2,Math.max(0,e.getAmount()))' in events,'hostile hits must deal five hearts while non-hostile hits stay gentle'
assert 'setAct(Act.DOWNED)' in dogsrc,'nothing ever puts him down'
for guard in ('isVehicle()||downed>0','downed<=0&&e instanceof Player','downed<=0&&!isVehicle()','home==null||downed>0'):
 assert guard in dogsrc,'a downed Sadaharu is still reachable: '+guard
assert 'case DOWNED' in model,'being knocked down has no pose'
assert 'downed>0' in (r/'src/main/java/com/hex/sadaharu/WorldEvents.java').read_text(),'he can still be called away while he is down'
assert 'getJumpPower' in dogsrc,'he still uses the vanilla jump height'
assert re.search(r'BlockPathTypes\.WATER,\s*([0-9.]+)',dogsrc),'water pathfinding malus is unset'
assert float(re.search(r'BlockPathTypes\.WATER,\s*([0-9.]+)',dogsrc).group(1))<=4,'water is priced so high he walks around every pond'
assert 'afloat()' in model,'the client never draws a swimming pose'
assert 'setAct(Act.SLEEP_TWITCH)' in personality,'nothing ever starts a dream twitch'
assert 'case SLEEP_TWITCH' in model,'the dream twitch has no pose of its own'
assert 'setAct(Act.LEAP)' in personality,'nothing makes him leap of his own accord'
assert 'dog.following' in personality,'the follow instruction never reaches his behaviour'

assert 'extends Wolf' not in src
assert src.count('new KeyMapping(')==1
assert 'SpawnEggItem' not in src
assert 'threat instanceof Enemy?10F:2F' in src and 'nextIntervention=dog.now()+900' in src
assert 'addRegionTicket' in src and 'removeRegionTicket' in src
assert 'getDataStorage().computeIfAbsent' in src
assert (r/'src/main/resources/assets/hexsadaharu/textures/item/kibble.png').exists()

# Audio: his voice must be a real Ogg Vorbis file, and mono, or Minecraft plays it
# flat across the whole world instead of attenuating it with distance.
sounds=json.loads((r/'src/main/resources/assets/hexsadaharu/sounds.json').read_text())
lang=json.loads((r/'src/main/resources/assets/hexsadaharu/lang/en_us.json').read_text())
voices=re.search(r'VOICES\s*=\s*\{(.*?)\}',(r/'src/main/java/com/hex/sadaharu/HexSadaharu.java').read_text()).group(1)
registered=set(re.findall(r'"([a-z_]+)"',voices))
assert set(sounds)==registered,set(sounds)^registered
for event,body in sounds.items():
 assert lang.get(body['subtitle']),event+' has no subtitle translation'
 for entry in body['sounds']:
  name=entry['name']
  if not name.startswith('hexsadaharu:'):
   assert entry['type']=='event' and name.startswith('minecraft:'),name
   continue
  assert entry.get('type','file')=='file',name+' must be a file reference'
  ogg=r/'src/main/resources/assets/hexsadaharu/sounds'/(name.split(':',1)[1]+'.ogg')
  assert ogg.exists(),str(ogg)+' is missing'
  raw=ogg.read_bytes()
  assert raw[:4]==b'OggS',ogg.name+' is not an Ogg container'
  head=raw.index(b'\x01vorbis')
  assert raw[head+11]==1,ogg.name+' must be mono so it attenuates with distance'
assert [e['name'] for e in sounds['bark']['sounds']]==['hexsadaharu:bark'],'the bark event must be his own voice'
for event in ('bark','deep_bark','excited'):
 assert sounds[event]['sounds'][0]['name']=='hexsadaharu:bark',event+' must lead with his own bark'

# Casual interactions have to be both chosen on the server and drawn on the client.
played=set(re.findall(r'voice\("([a-z_]+)"',personality+(r/'src/main/java/com/hex/sadaharu/Sadaharu.java').read_text()))
assert played<=registered,played-registered
assert 'voice("bark"' in personality,'nothing ever plays the bark event'
for act in ('HEAD_BITE','LICK_PLAYER','POUT','BARK'):
 assert 'Act.'+act in personality,'Personality never reaches Act.'+act
 assert 'case '+act in model or re.search(r'case [A-Z_, ]*\b'+act+r'\b',model),'SadaharuModel does not animate Act.'+act

print('Resource, curved rig, closed-mouth, uniqueness, voice, interaction, control and damage contracts passed.')

