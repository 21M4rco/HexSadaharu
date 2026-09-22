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
for b in ['root','body','chest','neck','head','collar','upper_jaw','lower_jaw','tongue','nose','ear_left','ear_right','tail_0','tail_1','tail_2','tail_3','brow_left','brow_right','eye_left','eye_right','eyelid_left','eyelid_right']:
 assert b in names,b
for s in ['front_left','front_right','rear_left','rear_right']:
 assert s+'_leg' in names and s+'_paw' in names

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
   out+=[(inv,c['origin'],c['size']) for c in b['cubes'] if c['material'] in (0,1)]
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
def buried(bone,cubes,cover,label,eps=.04):
 m=world[bone]
 for c in cubes:
  for p in samples(c['origin'],c['size']):
   q=apply(m,p)
   if not any(all(o[i]-eps<=(l:=apply(inv,q))[i]<=o[i]+s[i]+eps for i in range(3)) for inv,o,s in cover):
    raise AssertionError(label+' is exposed at rest near '+str([round(v,2) for v in q]))
mouth=shell(['head','cheek_','upper_jaw','lower_jaw'])
skull=shell(['head','cheek_'])
for name in ('upper_jaw','lower_jaw','tongue'):
 buried(name,[c for c in by[name]['cubes'] if c['material'] in (6,7)],mouth,name+' mouth interior')
for name in ('eyelid_left','eyelid_right'):
 buried(name,by[name]['cubes'],skull,name)

src='\n'.join(p.read_text() for p in (r/'src/main/java').rglob('*.java'))
assert 'extends Wolf' not in src
assert src.count('new KeyMapping(')==1
assert 'SpawnEggItem' not in src
assert '2F)' in src and 'nextIntervention=dog.now()+900' in src
assert 'addRegionTicket' in src and 'removeRegionTicket' in src
assert 'getDataStorage().computeIfAbsent' in src
assert (r/'src/main/resources/assets/hexsadaharu/textures/item/kibble.png').exists()
print('Resource, rig, closed-mouth, uniqueness, control and damage contracts passed.')
