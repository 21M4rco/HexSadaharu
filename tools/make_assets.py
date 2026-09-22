"""Author the actual runtime cuboid rig and pixel art; no generated concept image."""
from pathlib import Path
import json, math, random
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[1]; A=ROOT/'src/main/resources/assets/hexsadaharu'
for p in ['models/entity','textures/entity','textures/item','lang','models/item']: (A/p).mkdir(parents=True,exist_ok=True)
colors=['#fafbfc','#eef0f3','#f1b6b9','#28232a','#69504b','#e34047','#62323f','#ef8295','#fff7e8','#bfcddd','#ffffff','#d9b249']
rig=[]
def bone(name,parent,pivot,rot=(0,0,0)):
 b=dict(name=name,parent=parent,pivot=pivot,rotation=rot,cubes=[]);rig.append(b);return b
def box(b,pos,size,mat=0): b['cubes'].append(dict(origin=pos,size=size,material=mat))
def roundbox(b,c,s,mat=0):
 x,y,z=c;w,h,d=s
 # Three overlapping slabs form a clean chamfered cuboid, not an oversized cube.
 bx=min(w*.17,3);by=min(h*.2,3);bz=min(d*.16,2.5)
 box(b,[x-w/2+bx,y-h/2,z-d/2+bz],[w-2*bx,h,d-2*bz],mat)
 box(b,[x-w/2,y-h/2+by,z-d/2+bz],[w,h-2*by,d-2*bz],mat)
 box(b,[x-w/2+bx,y-h/2+by,z-d/2],[w-2*bx,h-2*by,d],mat)
root=bone('root',None,[0,24,0]); body=bone('body','root',[0,-19,3]);roundbox(body,[0,0,0],[24,22,30])
chest=bone('chest','body',[0,-1,-10]);roundbox(chest,[0,0,0],[25,23,14]);
neck=bone('neck','chest',[0,-6,-3]);roundbox(neck,[0,-1,0],[23,18,14]);
collar=bone('collar','neck',[0,6,-1]);roundbox(collar,[0,0,0],[26.8,3.4,20],5)
box(collar,[-2,-1.2,-10.2],[4,3,1],11)
head=bone('head','neck',[0,-5,-3]);
for y,h,w,d in [(-15,3,16,15),(-12,3,23,19),(-9,4,27,21),(-5,5,29,21),(0,3,27,20)]:
 box(head,[-w/2+2,y,-2-d/2],[w-4,h,d])
 box(head,[-w/2,y,-2-d/2+2],[w,h,d-4])
# Recessed mouth cavity below the cranium; the jaw is never hidden inside a solid skull.
box(head,[-7,3,-8],[14,6,2],6)
# Soft wide cheek lobes flank a short, broad articulated muzzle.
for side in [-1,1]:
 c=bone('cheek_'+str(side),'head',[side*10,3,-5],(0,0,side*-5)); roundbox(c,[0,0,0],[9,12,12]);
 for i in range(3):
  f=bone('cheek_tuft_'+str(side)+'_'+str(i),c['name'],[side*3,1+i*2,1+i],(0,0,side*(20+i*6)));box(f,[-1,-2,-2],[3,5,4])
 ear=bone('ear_'+('left' if side==1 else 'right'),'head',[side*10,-11,-1],(0,0,side*25))
 for j in range(5):
  w=11-j*2.15;box(ear,[-w/2,-j*1.8-2,-2],[w,2.2,4.5]);
  if j<4:box(ear,[-(w-2.6)/2,-j*1.8-1.7,-2.15],[max(1,w-2.6),1.8,.35],2)
 eye=bone('eye_'+('left' if side==1 else 'right'),'head',[side*7,-3.5,-12.6])
 box(eye,[-2.7,-3,-.25],[5.4,6,.6],3);box(eye,[-3.35,-2,-.2],[6.7,4,.5],3)
 box(eye,[-2.2,1.15,-.57],[4.4,1.5,.32],9);box(eye,[-1.9,-2.2,-.65],[1.7,2,.45],10);box(eye,[1.1,.4,-.65],[.65,.65,.4],10)
 brow=bone('brow_'+('left' if side==1 else 'right'),'head',[side*7,-9.1,-12])
 for j in range(5):
  t=j/4;seg=bone('brow_'+str(side)+'_'+str(j),brow['name'],[(t-.5)*6.6,-math.sin(t*math.pi)*1.7,0],(0,0,math.degrees(math.atan(-math.cos(t*math.pi)*.65))))
  box(seg,[-1,-.7,-.4],[2,1.45,.9],4)
upper=bone('upper_jaw','head',[0,3,-9]);roundbox(upper,[0,0,-2],[14,7,9]);
box(upper,[-5,2.4,-5],[10,.8,7],6)
nose=bone('nose','upper_jaw',[0,-2,-6.65]);box(nose,[-2.2,-.8,-.4],[4.4,1.8,1.1],4);box(nose,[-1.2,.7,-.35],[2.4,1.2,.9],4);box(nose,[-1.2,-.7,-.55],[1.7,.4,.25],9)
jaw=bone('lower_jaw','head',[0,6,-3]);roundbox(jaw,[0,1.3,-7],[14,4,12]);box(jaw,[-5.7,-.85,-12],[11.4,.6,9],6)
tongue=bone('tongue','lower_jaw',[0,-.6,-7]);roundbox(tongue,[0,0,-2],[7,1.2,6],7)
for side in [-1,1]:
 for j in range(5):
  box(upper,[side*(4.4 if j>0 else 3.5)-.6,2.7,-5.6+j*1.25],[1.2,2 if j in [0,4] else 1,1.1],8)
  box(jaw,[side*5-.5,-1.7,-11+j*1.7],[1,1.4,1],8)
for j in range(5):box(upper,[-3.5+j*1.4,2.7,-5.8],[1,1.05,1],8)
for name,x,z in [('front_left',8,-10),('front_right',-8,-10),('rear_left',8,11),('rear_right',-8,11)]:
 leg=bone(name+'_leg','body',[x,5,z]);roundbox(leg,[0,4.5,0],[8.5,12,9]);
 paw=bone(name+'_paw',leg['name'],[0,10,0]);roundbox(paw,[0,2,-1.3],[9.5,4,11])
 for i in [-1,0,1]:box(paw,[i*2.6-.8,1,-6.8],[1.7,2.2,1.3])
 for side in [-1,1]:
  f=bone(name+'_fluff_'+str(side),leg['name'],[side*3,0,1],(0,0,side*15));box(f,[-1,-2,-2],[3,6,5])
for i,(p,piv,rot,size) in enumerate([('body',[0,-5,14],[45,0,0],[10,10,11]),('tail_0',[0,0,8],[35,0,0],[10,10,10]),('tail_1',[0,0,7],[38,0,0],[9,9,9]),('tail_2',[0,0,6],[25,0,0],[7,7,8])]):
 b=bone('tail_'+str(i),p,piv,rot);roundbox(b,[0,0,3],size)
for j in range(5):
 b=bone('chest_fluff_'+str(j),'chest',[(j-2)*4,7-abs(j-2),-6],(0,0,(j-2)*9));box(b,[-2,-2,-2],[4.5,7,4])
for side in [-1,1]:
 for j in range(3):
  b=bone('belly_fluff_'+str(side)+'_'+str(j),'body',[side*9,8,j*6-4],(0,0,side*18));box(b,[-2,-1,-2],[4,5,5])
(A/'models/entity/sadaharu.json').write_text(json.dumps({'texture_size':[512,512],'bones':rig},indent=2))
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
print(len(rig),'bones;',sum(len(b['cubes']) for b in rig),'cuboids')
