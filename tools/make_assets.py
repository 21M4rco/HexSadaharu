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
 # A stack of superellipse layers: the silhouette curves away at top and bottom instead of stepping.
 x,y,z=c;w,h,d=s
 for i in range(layers):
  t0=i/layers;t1=(i+1)/layers
  k=k_of(layers,power,i);g=taper[0]+(taper[1]-taper[0])*((t0+t1)*.5)
  slab(b,x,y-h/2+h*t0,z,w*k*g,h*(t1-t0),d*g if flat else d*k*g,mat)

root=bone('root',None,[0,24,0])
body=bone('body','root',[0,-19,3]);blob(body,[0,0,0],[24,22,30],layers=7,power=2.2)
chest=bone('chest','body',[0,-1,-10]);blob(chest,[0,0,0],[25,23,15],layers=7,power=2.3)
neck=bone('neck','chest',[0,-6,-3]);blob(neck,[0,-1,0],[22,18,15],layers=6,power=2.4)
# The collar is a band following the throat, laid out on an ellipse so it never becomes a flat plate.
collar=bone('collar','neck',[0,1.4,0]);RX,RZ,CH,CT=12.9,8.2,2.9,1.7
for j in range(12):
 th=2*math.pi*j/12
 seg=bone('collar_'+str(j),'collar',[math.sin(th)*RX,0,-math.cos(th)*RZ],(0,math.degrees(math.atan2(-RZ*math.sin(th),RX*math.cos(th))),0))
 box(seg,[-3.6,-CH/2,-CT/2],[7.2,CH,CT],5)
HY,HH,HD,HZ,HP,HL=-6.,19.,21.,-2.5,2.35,7
head=bone('head','neck',[0,-5,-3]);blob(head,[0,HY,HZ],[27,HH,HD],layers=HL,power=HP)
def face(y): return HZ-HD/2*k_at(HH,HL,HP,y-HY)   # head-local z of the face surface at height y
for side in [-1,1]:
 lr='left' if side==1 else 'right'
 c=bone('cheek_'+str(side),'head',[side*10,-1,-3.5],(0,0,-side*6));blob(c,[0,0,0],[8.5,12,13],layers=5,power=2.3)
 for i in range(2):
  f=bone('cheek_tuft_'+str(side)+'_'+str(i),c['name'],[side*2.6,1.5+i*3.6,1.5],(0,0,side*(14+i*10)))
  box(f,[-1.4,-2.4,-2.4],[3.4,5.2,4.8])
 # Continuous pink inner panel; per-layer strips banded the ear like a candy cane.
 ear=bone('ear_'+lr,'head',[side*8.5,-13.,-1.5],(-8,0,side*23))
 for j in range(6):
  w=9.-j*1.3;d=4.8-j*.4
  box(ear,[-w/2,-j*1.4-1.5,-d/2],[w,1.45,d])
  if j<5: box(ear,[-max(1.2,w-3.)/2,-j*1.4-1.5,-d/2-.26],[max(1.2,w-3.),1.45,.3],2)
 # Round eye: stacked bands and one glint, no pale bar across the lower lid.
 eye=bone('eye_'+lr,'head',[side*6.8,-3.6,face(-3.6)+.25])
 for i,w in enumerate([3.6,5.4,6.2,6.2,5.4,3.6]): box(eye,[-w/2,-3.+i,-.55],[w,1.,.6],3)
 box(eye,[-2.,-2.1,-.77],[1.7,1.9,.3],10);box(eye,[1.,.5,-.77],[.75,.75,.28],10)
 # A real lid, parked folded back inside the skull and swept down to blink.
 lid=bone('eyelid_'+lr,'head',[side*6.8,-7.,face(-7.)+.45],(126,0,0))
 box(lid,[-3.5,0,-.95],[7.,6.9,1.])
 for x,y,w in [(-3.5,6.05,2.2),(-1.5,6.3,3.,),(1.3,6.05,2.2)]: box(lid,[x,y,-1.],[w,.55,1.05],4)
 # Brows sit low and close over the eye; high steep arches read as horns.
 brow=bone('brow_'+lr,'head',[side*6.8,-7.6,face(-7.6)+.1])
 for j in range(3):
  t=j/2;seg=bone('brow_'+str(side)+'_'+str(j),brow['name'],[(t-.5)*5.,-math.sin(t*math.pi)*1.25,0],(0,0,math.degrees(math.atan(-math.cos(t*math.pi)*.55))))
  box(seg,[-1.35,-.65,-.6],[2.7,1.3,1.])
  seg['cubes'][-1]['material']=4
 box(brow,[-side*1.7-1.05,-1.,-.65],[2.1,2.1,1.05],4)
# Short soft muzzle. While the jaw is shut nothing dark, and no tooth, is exposed.
MY,MH,MD,MZ,MPW,ML=1.2,5.2,8.,-2.6,2.4,5
upper=bone('upper_jaw','head',[0,0,-9]);blob(upper,[0,MY,MZ],[11.8,MH,MD],layers=ML,power=MPW)
def snout(y): return MZ-MD/2*k_at(MH,ML,MPW,y-MY)  # upper-jaw-local z of the muzzle surface
for x,y,w in [(-1.75,2.35,1.05),(-.75,2.15,1.5),(.7,2.35,1.05)]: box(upper,[x,y,snout(y)-.42],[w,.4,.44],4)
nose=bone('nose','upper_jaw',[0,.55,snout(.55)+.05])
box(nose,[-1.3,-.95,-.45],[2.6,.8,.9],4);box(nose,[-2.2,-.35,-.62],[4.4,1.,1.1],4);box(nose,[-1.6,.5,-.45],[3.2,.85,.9],4)
box(nose,[-1.05,-.72,-.72],[1.8,.35,.28],9)
jaw=bone('lower_jaw','head',[0,2.4,-7]);blob(jaw,[0,.9,-2.6],[8.,3.,6.6],layers=4,power=2.4)
box(jaw,[-2.6,-.85,-5.6],[5.2,.24,4.4],6)                                   # mouth floor, sealed inside the muzzle at rest
tongue=bone('tongue','lower_jaw',[0,-1.25,-3.5]);blob(tongue,[0,0,-.9],[5.6,.8,4.4],mat=7,layers=3,power=2.3)
for name,x,z in [('front_left',8,-10),('front_right',-8,-10),('rear_left',8,11),('rear_right',-8,11)]:
 # Furry shoulder tapering into a slim ankle, instead of a straight column.
 leg=bone(name+'_leg','body',[x,5,z]);blob(leg,[0,4.6,0],[8.8,12,8.8],layers=5,power=2.4,taper=(1.16,.8))
 paw=bone(name+'_paw',leg['name'],[0,10,0]);blob(paw,[0,2.,-1.2],[9.4,4.2,10.4],layers=4,power=2.4)
 for i in [-1,0,1]: box(paw,[i*2.5-1.,1.3,-6.4],[2.,1.9,1.2])
for i,(p,piv,rot,size) in enumerate([('body',[0,-5,14],[48,0,0],[10.5,10.5,10]),('tail_0',[0,0,6.5],[34,0,0],[9.4,9.4,8.5]),('tail_1',[0,0,5.5],[36,0,0],[8.2,8.2,7.5]),('tail_2',[0,0,4.5],[26,0,0],[6.4,6.4,6.5])]):
 b=bone('tail_'+str(i),p,piv,rot);blob(b,[0,0,size[2]*.3],size,layers=5,power=2.3,flat=True)
# One continuous ruff along a smooth curve; the old ladder of tabs read as a staircase.
for j in range(9):
 u=(j-4)/4.
 b=bone('chest_fluff_'+str(j),'chest',[u*8.2,3.+u*u*3.,-6.6-abs(u)*.4],(0,0,u*20))
 box(b,[-2.1,-3.,-1.5],[4.2,6.,3.])
for side in [-1,1]:
 for j in range(3):
  b=bone('belly_fluff_'+str(side)+'_'+str(j),'body',[side*9.4,7.6,j*6.5-5],(0,0,side*22))
  box(b,[-2.2,-1.4,-2.6],[4.4,5.2,5.2])
(A/'models/entity/sadaharu.json').write_text(json.dumps({'texture_size':[512,512],'palette':colors,'bones':rig},indent=2))
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
print(len(rig),'bones;',sum(len(b['cubes']) for b in rig),'cuboids')
