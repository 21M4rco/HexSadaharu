"""Software depth render of the exact runtime rig (not an AI concept illustration)."""
import json,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
rig=json.loads((ROOT/'src/main/resources/assets/hexsadaharu/models/entity/sadaharu.json').read_text())['bones']
colors=['#fafbfc','#eef0f3','#f1b6b9','#28232a','#69504b','#e34047','#62323f','#ef8295','#fff7e8','#bfcddd','#ffffff','#d9b249']
W,H=600,550

def render(pose,azim):
 az=math.radians(azim);el=math.radians(10)
 right=np.array([-math.sin(az),math.cos(az),0]);view=np.array([math.cos(az)*math.cos(el),math.sin(az)*math.cos(el),math.sin(el)]);up=np.cross(view,right)
 bg=np.full((H,W,3),[218,226,236],np.uint8);depth=np.full((H,W),-1e9);matrices={}
 def triangle(v,col):
  xyz=np.array([[np.dot(q,right)*7.7+W/2,-np.dot(q-np.array([0,0,24]),up)*7.7+H/2,np.dot(q,view)]for q in v])
  xmin=max(0,int(xyz[:,0].min()));xmax=min(W-1,int(xyz[:,0].max()+1));ymin=max(0,int(xyz[:,1].min()));ymax=min(H-1,int(xyz[:,1].max()+1))
  if xmax<xmin or ymax<ymin:return
  a,b,c=xyz;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
  if abs(den)<1e-6:return
  yy,xx=np.mgrid[ymin:ymax+1,xmin:xmax+1];xx=xx+.5;yy=yy+.5
  wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
  wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;wc=1-wa-wb
  z=wa*a[2]+wb*b[2]+wc*c[2];d=depth[ymin:ymax+1,xmin:xmax+1];mask=(wa>=0)&(wb>=0)&(wc>=0)&(z>d)
  d[mask]=z[mask];bg[ymin:ymax+1,xmin:xmax+1][mask]=col
 for b in rig:
  p=np.array(b['pivot'],float);r=np.radians(b['rotation']);n=b['name']
  if pose=='Sitting':
   if n=='body':p+=[0,6,1.5];r[0]-=.3
   if n=='neck':r[0]+=.3
   if n.startswith('front_') and n.endswith('_leg'):p[1]-=4;r[0]+=.3
   if n.startswith('rear_') and n.endswith('_leg'):r[0]-=1.1
   if n.startswith('rear_') and n.endswith('_paw'):r[0]+=1.3
  if pose=='Mouth open':
   if n=='lower_jaw':r[0]+=.95
   if n=='head':r[0]-=.2
  if pose=='Bounding':
   if n=='body':r[0]-=.18;p[1]-=3
   if n.startswith('front_') and n.endswith('_leg'):r[0]-=1.05
   if n.startswith('rear_') and n.endswith('_leg'):r[0]+=.9
  sx,sy,sz=np.sin(r);cx,cy,cz=np.cos(r)
  rot=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
  m=np.eye(4);m[:3,:3]=rot;m[:3,3]=p
  if b['parent']:m=matrices[b['parent']]@m
  matrices[n]=m
  for c in b['cubes']:
   o=np.array(c['origin']);s=np.array(c['size']);vs=[]
   for x,y,z in [(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)]:
    q=(m@np.r_[o+s*[x,y,z],1])[:3];vs.append(np.array([q[0],-q[2],24-q[1]]))
   for face,shade in [([0,1,2,3],1),([4,7,6,5],.81),([0,4,5,1],.97),([3,2,6,7],.76),([0,3,7,4],.84),([1,5,6,2],.9)]:
    he=colors[c['material']].lstrip('#');col=[int(int(he[i:i+2],16)*shade)for i in (0,2,4)]
    triangle([vs[face[i]]for i in (0,1,2)],col);triangle([vs[face[i]]for i in (0,2,3)],col)
 return Image.fromarray(bg)
canvas=Image.new('RGB',(1800,1180),(218,226,236));draw=ImageDraw.Draw(canvas)
fontpath='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
font=ImageFont.truetype(fontpath,26);title=ImageFont.truetype(fontpath,32)
draw.text((45,23),'SADAHARU  /  actual cuboid rig',fill='#27384a',font=title)
for i,(pose,az) in enumerate([('Front',90),('Side',0),('Rear',-90),('Sitting',65),('Mouth open',75),('Bounding',30)]):
 x=(i%3)*W;y=80+(i//3)*H;canvas.paste(render(pose,az),(x,y));draw.text((x+25,y+8),pose,fill='#344356',font=font)
(ROOT/'docs').mkdir(exist_ok=True);canvas.save(ROOT/'docs/model-review.png')
