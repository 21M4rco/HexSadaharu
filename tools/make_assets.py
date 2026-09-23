"""Author the actual runtime cuboid rig and pixel art; no generated concept image."""
from pathlib import Path
import json, math, random
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]; A=ROOT/'src/main/resources/assets/hexsadaharu'
for p in ['models/entity','textures/entity','textures/item','lang','models/item']: (A/p).mkdir(parents=True,exist_ok=True)
# 0 fur  1 fur shade  2 ear pink  3 eye  4 brow/nose  5 collar  6 mouth  7 tongue  8 spare  9 blush  10 glint  11 spare
colors=['#fafbfc','#f1f3f6','#f3bfc2','#221e24','#5a4744','#b4303a','#7a3a44','#ef8f9d','#fff7e8','#e9bcc0','#ffffff','#d9b249']
rig=[]
def bone(name,parent,pivot,rot=(0,0,0)):
 b=dict(name=name,parent=parent,pivot=list(pivot),rotation=list(rot),cubes=[]);rig.append(b);return b
def box(b,pos,size,mat=0): b['cubes'].append(dict(origin=list(pos),size=list(size),material=mat))
def slab(b,x,y,z,w,h,d,mat):
 # One layer as two crossing plates, so the plan-view corners come off instead of staying square.
 cx=min(w*.17,2.4);cz=min(d*.17,2.4)
 box(b,[x-w/2+cx,y,z-d/2],[w-2*cx,h,d],mat)
 box(b,[x-w/2,y,z-d/2+cz],[w,h,d-2*cz],mat)
def k_of(layers,power,i):
 m=(i+.5)/layers
 return (1-min(abs(m-.5)*2,1)**power)**(1/power)
def k_at(h,layers,power,y):
 """Profile factor of the layer holding local height y, measured from the blob centre."""
 return k_of(layers,power,min(layers-1,max(0,int((y+h/2)/h*layers))))
def blob(b,c,s,mat=0,layers=6,power=2.6,taper=(1,1),flat=False):
 # Actual curved surfaces, baked once by RoundedMesh, not overlapping cuboid slabs.
 b.setdefault('meshes',[]).append(dict(center=list(c),size=list(s),material=mat,
     power=min(power,2.25),taper=list(taper),segments=24 if max(s)>18 else 12,rings=16 if max(s)>18 else 8))

def wedge(b,vertices,faces,mat=0):
 b.setdefault('meshes',[]).append(dict(vertices=vertices,faces=faces,material=mat))

root=bone('root',None,[0,24,0])
body=bone('body','root',[0,-25,3]);blob(body,[0,0,0],[24,26,30],layers=7,power=2.2)
chest=bone('chest','body',[0,-1,-10]);blob(chest,[0,0,0],[24,28,15],layers=7,power=2.3)
neck=bone('neck','chest',[0,-6,-3]);blob(neck,[0,-1,0],[22,18,15],layers=6,power=2.4)
# The collar is a band following the throat, laid out on an ellipse so it never becomes a flat plate.
collar=bone('collar','neck',[0,-1.7,-.1],(40,0,0));RX,RZ,CH,CT=12.2,8.7,1.8,.55
verts=[];faces=[]
for j in range(32):
 th=2*math.pi*j/32
 for rx,rz,y in [(RX,RZ,-CH/2),(RX,RZ,CH/2),(RX-CT,RZ-CT,CH/2),(RX-CT,RZ-CT,-CH/2)]:verts.append([math.sin(th)*rx,y,-math.cos(th)*rz])
for j in range(32):
 for k in range(4):faces.append([j*4+k,((j+1)%32)*4+k,((j+1)%32)*4+(k+1)%4,j*4+(k+1)%4])
wedge(collar,verts,faces,5)
HY,HH,HD,HZ,HP,HL=-6.,21.,21.,-2.5,2.15,7
head=bone('head','neck',[0,-5,-3]);blob(head,[0,HY,HZ],[27,HH,HD],layers=HL,power=HP)
def face(y,x=0): return HZ-HD/2*max(0,1-abs((y-HY)/(HH/2))**HP-abs(x/13.5)**HP)**(1/HP)   # head-local z of the face surface at height y
for side in [-1,1]:
 lr='left' if side==1 else 'right'
 c=bone('cheek_'+str(side),'head',[side*9.2,-1.0,-4.4],(0,0,-side*6));blob(c,[0,0,0],[8.5,8.5,11.5],layers=5,power=2.3)
 # Tiny tapered cheek wisps stay close to the silhouette.
 for i in range(2):
  f=bone('cheek_tuft_'+str(side)+'_'+str(i),c['name'],[side*2.5,1.3+i*1.8,1.8],(0,0,side*12))
  wedge(f,[[-1.2,-1.5,-1],[1.2,-1.5,-1],[side*1.2,1.3,0],[0,-1.5,1]],[[0,2,1],[0,3,2],[1,2,3],[0,1,3]])
 # Clean triangular ears with soft white rims and a continuous pink inset.
 ear=bone('ear_'+lr,'head',[side*9.2,-13.,-1.5],(-8,0,side*18))
 wedge(ear,[[-5,0,-2],[5,0,-2],[.3,-10,-.6],[-4.2,0,2.5],[4.2,0,2.5],[.3,-9,.9]],
       [[0,2,1],[3,4,5],[0,3,5,2],[1,2,5,4],[0,1,4,3]])
 wedge(ear,[[-3.25,-1.2,-2.05],[3.25,-1.2,-2.05],[.3,-8,-.99]],[[0,2,1]],2)
 # Shallow rounded eyes follow the cheek curvature rather than floating on a flat face.
 eye=bone('eye_'+lr,'head',[side*6.5,-4.,face(-4.,6.5)-.18],(0,-side*22,0))
 blob(eye,[0,0,0],[5.8,6.0,1.8],3,power=2)
 blob(eye,[-1.0,-1.3,-.84],[1.65,1.8,.45],10,power=2)
 blob(eye,[1.1,1.0,-.85],[.6,.65,.3],10,power=2)
 # Closed-eye smile is hidden at rest; eye compression handles intermediate blinks.
 lid=bone('eyelid_'+lr,'head',eye['pivot'],(0,-side*22,0))
 for j in range(5):
  x=(j-2)*.96
  blob(lid,[x,.2+abs(j-2)*.15,-.88],[1.2,.4,.3],4,power=2)
 brow=bone('brow_'+lr,'head',[side*6.3,-9.1,face(-9.1,6.3)-.1],(0,-side*20,0))
 verts=[];faces=[]
 for j in range(13):
  t=j/12;x=(t-.5)*4.7;y=-math.sin(t*math.pi)*1.0
  radius=.43+.2*(1-t if side==1 else t)
  for k in range(8):
   a=2*math.pi*k/8;verts.append([x,y+math.sin(a)*radius,math.cos(a)*.35])
 for j in range(12):
  for k in range(8):faces.append([j*8+k,j*8+(k+1)%8,(j+1)*8+(k+1)%8,(j+1)*8+k])
 wedge(brow,verts,faces,4)
 blob(brow,[-side*1.8,0,-.02],[1.4,1.5,.72],4,power=2)
# Short soft muzzle. While the jaw is shut nothing dark, and no tooth, is exposed.
MY,MH,MD,MZ,MPW,ML=1.2,5.2,8.,-2.6,2.4,5
upper=bone('upper_jaw','head',[0,0,-9]);blob(upper,[0,MY,MZ],[11.8,MH,MD],layers=ML,power=MPW)
def snout(y): return MZ-MD/2*max(0,1-abs((y-MY)/(MH/2))**2.25)**(1/2.25)  # upper-jaw-local z of the muzzle surface
mark=bone('closed_mouth_mark','upper_jaw',[0,0,0])
for j in range(9):
 x=(j-4)*.43;y=2.15+.32*math.sin(abs(x)/1.72*math.pi)
 blob(mark,[x,y,snout(y)-.09],[.62,.28,.3],4,power=2)
nose=bone('nose','upper_jaw',[0,.05,snout(.05)-.03])
wedge(nose,[[-1.9,-.55,0],[1.9,-.55,0],[0,1.35,-.1],[-1.4,-.25,-.8],[1.4,-.25,-.8],[0,1.0,-.6]],
 [[0,1,4,3],[3,4,5],[0,3,5,2],[1,2,5,4],[0,2,1]],4)
blob(nose,[-.55,-.25,-.81],[1.,.35,.18],9,power=2)
jaw=bone('lower_jaw','head',[0,2.4,-7]);blob(jaw,[0,.9,-2.6],[8.,3.,6.6],layers=4,power=2.4)
blob(jaw,[0,-.5,-3.2],[4.5,.18,3.6],6,power=2)                                   # mouth floor, sealed inside the muzzle at rest
tongue=bone('tongue','lower_jaw',[0,-.6,-3.1]);blob(tongue,[0,0,-.9],[4.4,.6,3.4],mat=7,layers=3,power=2.3)
for name,x,z in [('front_left',8,-10),('front_right',-8,-10),('rear_left',8,11),('rear_right',-8,11)]:
 # Furry shoulder tapering into a slim ankle, instead of a straight column.
 leg=bone(name+'_leg','body',[x,5,z]);blob(leg,[0,7.8,0],[8.8 if name.startswith('front') else 11,18,9.2],layers=5,power=2.4,taper=(1.16,.8))
 paw=bone(name+'_paw',leg['name'],[0,16,0]);blob(paw,[0,2.,-1.2],[9.4,4.2,10.4],layers=4,power=2.4)
 for i in [-1,0,1]: blob(paw,[i*2.3,2.,-5.25],[2.8,2.3,2.1],power=2)
for i,(p,piv,rot,size) in enumerate([('body',[0,-5,14],[48,0,0],[10.5,10.5,13]),('tail_0',[0,0,6.5],[34,0,0],[9.4,9.4,11.5]),('tail_1',[0,0,5.5],[36,0,0],[8.2,8.2,10.5]),('tail_2',[0,0,4.5],[26,0,0],[6.4,6.4,8.5])]):
 b=bone('tail_'+str(i),p,piv,rot);blob(b,[0,0,size[2]*.3],size,layers=5,power=2.3,flat=True)
# One continuous ruff along a smooth curve; the old ladder of tabs read as a staircase.
ruff=bone('chest_ruff','chest',[0,2,-5.6]);blob(ruff,[0,0,0],[18,19,5],power=2,taper=(1,.45))
for j in range(7):
 u=(j-3)/3.
 b=bone('chest_fluff_'+str(j),'chest',[u*6.3,7.-abs(u)*2.5,-6.6],(0,0,-u*10))
 wedge(b,[[-1.65,-2.4,-.3],[1.65,-2.4,-.3],[0,3.0-abs(u)*.7,-.1],[0,-2.4,1.7]],
       [[0,2,1],[0,3,2],[1,2,3],[0,1,3]])
# Enlarge the entire head hierarchy together, including the expression and jaw rig.
head_family={'head'}
for b in rig:
 if b['parent'] in head_family:head_family.add(b['name'])
 if b['name'] not in head_family:continue
 if b['name']=='head':b['pivot'][1]-=1.0;b['pivot'][2]-=.6
 else:b['pivot']=[round(v*1.12,5) for v in b['pivot']]
 for m in b.get('meshes',[]):
  if 'vertices' in m:m['vertices']=[[round(v*1.12,5) for v in p] for p in m['vertices']]
  else:
   for key in ('center','size'):m[key]=[round(v*1.12,5) for v in m[key]]
from sculpt_coat import sculpt
coats=sculpt(rig)
(A/'models/entity/sadaharu.json').write_text(json.dumps({'texture_size':[512,512],'palette':colors,'bones':rig,'coats':coats},separators=(',',':')))
im=Image.new('RGBA',(512,512)); d=ImageDraw.Draw(im)
for i,c in enumerate(colors):x=(i%4)*128;y=(i//4)*128;d.rectangle((x,y,x+127,y+127),fill=c)
im.save(A/'textures/entity/sadaharu.png')
# Hand-pixelled bowl of coarse meat, fish, grains and greens.
im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
d.polygon([(3,14),(28,14),(26,25),(22,28),(9,28),(5,24)],fill='#533a36');d.polygon([(4,15),(27,15),(24,24),(9,24),(6,21)],fill='#b46b47');d.rectangle((7,24,24,26),fill='#753f32')
d.ellipse((3,8,28,20),fill='#e2b17a');d.ellipse((5,9,26,17),fill='#684734')
r=random.Random(23)
for i in range(38):
 x=r.randint(7,24);y=r.randint(9,16);c=r.choice(['#c87762','#8e5140','#eecb87','#829daa','#d9c697','#76935d']);d.rectangle((x,y,x+2,y+1),fill=c)
im.save(A/'textures/item/kibble.png')
im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
for b in [(2,10,13,14),(4,7,12,11),(6,4,10,8),(7,2,9,6)]:d.ellipse(b,fill='#76503b',outline='#493528')
im.save(A/'textures/item/poop.png')
for item in ['kibble','poop']:(A/f'models/item/{item}.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':f'hexsadaharu:item/{item}'}}))
(A/'lang/en_us.json').write_text(json.dumps({'entity.hexsadaharu.sadaharu':'Sadaharu','item.hexsadaharu.kibble':'Kibble','item.hexsadaharu.poop':'Sadaharu’s calling card','key.hexsadaharu.call':'Call Dog','key.categories.hexsadaharu':'Sadaharu'},indent=2))
D=ROOT/'src/main/resources/data'
for tag,vals in [('meats',[{'id':'forge:raw_meats','required':False},{'id':'forge:cooked_meats','required':False},'minecraft:beef','minecraft:porkchop','minecraft:chicken','minecraft:mutton','minecraft:rabbit','minecraft:cooked_beef','minecraft:cooked_porkchop','minecraft:cooked_chicken','minecraft:cooked_mutton','minecraft:cooked_rabbit']),('fishes',[{'id':'forge:raw_fishes','required':False},{'id':'forge:cooked_fishes','required':False},{'id':'minecraft:fishes','required':False},'minecraft:cod','minecraft:salmon','minecraft:cooked_cod','minecraft:cooked_salmon'])]:
 p=D/f'hexsadaharu/tags/items/{tag}.json';p.parent.mkdir(parents=True,exist_ok=True)
 for v in vals:
  if isinstance(v,dict):v['id']='#'+v['id']
 p.write_text(json.dumps({'replace':False,'values':vals},indent=2))
p=D/'hexsadaharu/recipes/kibble.json';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps({'type':'minecraft:crafting_shapeless','ingredients':[{'tag':'hexsadaharu:meats'},{'tag':'hexsadaharu:fishes'},{'item':'minecraft:wheat'}],'result':{'item':'hexsadaharu:kibble','count':4}},indent=2))
lang=json.loads((A/'lang/en_us.json').read_text());lang.update({'subtitles.hexsadaharu.'+k:'Sadaharu '+k.replace('_',' ') for k in ['bark','excited','deep_bark','whine','growl','pant','sleep','yawn','eat','land','step']});(A/'lang/en_us.json').write_text(json.dumps(lang,indent=2))
print(len(rig),'bones;',sum(len(b.get('meshes',[])) for b in rig),'rounded meshes')

