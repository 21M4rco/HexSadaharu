"""Software depth render of the exact runtime rig (not an AI concept illustration)."""
import json,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont
ROOT=Path(__file__).resolve().parents[1]
DOC=json.loads((ROOT/'src/main/resources/assets/hexsadaharu/models/entity/sadaharu.json').read_text())
rig=DOC['bones'];colors=DOC['palette']
W,H=560,520

def posed(pose,name,p,r):
 """Mirror of the poses SadaharuModel drives at runtime, applied to pivot/rotation."""

 if pose=='Sitting':
  if name=='body':p+=[0,6,1.5];r[0]-=.3
  if name=='neck':r[0]+=.3
  if name=='head':p[1]-=1.5
  if name.startswith('front_') and name.endswith('_leg'):p[1]-=4;r[0]+=.3
  if name.startswith('rear_') and name.endswith('_leg'):r[0]-=1.1;r[2]+=-.18 if name=='rear_left_leg' else .18
  if name.startswith('rear_') and name.endswith('_paw'):r[0]+=1.3
 if pose=='Mouth open':
  if name=='lower_jaw':r[0]+=.95
  if name=='head':r[0]-=.2
  if name=='tongue':r[0]-=.25
 if pose=='Bounding':
  if name=='body':r[0]-=.18;p[1]-=3
  if name.startswith('front_') and name.endswith('_leg'):r[0]-=1.05
  if name.startswith('rear_') and name.endswith('_leg'):r[0]+=.9
 return p,r

def render(pose,azim):
 az=math.radians(azim);el=math.radians(10)
 right=np.array([-math.sin(az),math.cos(az),0]);view=np.array([math.cos(az)*math.cos(el),math.sin(az)*math.cos(el),math.sin(el)]);up=np.cross(view,right)
 bg=np.full((H,W,3),[218,226,236],np.uint8);depth=np.full((H,W),-1e9);matrices={}
 def triangle(v,col):
  xyz=np.array([[np.dot(q,right)*6.4+W/2,-np.dot(q-np.array([0,0,27]),up)*6.4+H/2,np.dot(q,view)]for q in v])
  xmin=max(0,int(xyz[:,0].min()));xmax=min(W-1,int(xyz[:,0].max()+1));ymin=max(0,int(xyz[:,1].min()));ymax=min(H-1,int(xyz[:,1].max()+1))
  if xmax<xmin or ymax<ymin:return
  a,b,c=xyz;den=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
  if abs(den)<1e-6:return
  yy,xx=np.mgrid[ymin:ymax+1,xmin:xmax+1];xx=xx+.5;yy=yy+.5
  wa=((b[1]-c[1])*(xx-c[0])+(c[0]-b[0])*(yy-c[1]))/den
  wb=((c[1]-a[1])*(xx-c[0])+(a[0]-c[0])*(yy-c[1]))/den;wc=1-wa-wb
  z=wa*a[2]+wb*b[2]+wc*c[2];d=depth[ymin:ymax+1,xmin:xmax+1];mask=(wa>=0)&(wb>=0)&(wc>=0)&(z>d)
  d[mask]=z[mask]
  if np.ndim(col)==2:
   shaded=wa[:,:,None]*col[0]+wb[:,:,None]*col[1]+wc[:,:,None]*col[2]
   bg[ymin:ymax+1,xmin:xmax+1][mask]=np.clip(shaded[mask],0,255).astype(np.uint8)
  else:bg[ymin:ymax+1,xmin:xmax+1][mask]=col
 for b in rig:
  p,r=posed(pose,b['name'],np.array(b['pivot'],float),np.radians(np.array(b['rotation'],float)))
  n=b['name']
  hidden=(n.startswith('eyelid_') and pose!='Blink') or (n.startswith('eye_') and pose=='Blink') or (n=='closed_mouth_mark' and pose=='Mouth open')
  sx,sy,sz=np.sin(r);cx,cy,cz=np.cos(r)
  rot=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
  m=np.eye(4);m[:3,:3]=rot;m[:3,3]=p
  if b['parent']:m=matrices[b['parent']]@m
  matrices[n]=m
  if hidden:continue
  for c in b['cubes']:
   o=np.array(c['origin']);s=np.array(c['size']);vs=[]
   for x,y,z in [(0,0,0),(1,0,0),(1,1,0),(0,1,0),(0,0,1),(1,0,1),(1,1,1),(0,1,1)]:
    q=(m@np.r_[o+s*[x,y,z],1])[:3];vs.append(np.array([q[0],-q[2],24-q[1]]))
   for face,shade in [([0,1,2,3],1),([4,7,6,5],.81),([0,4,5,1],.97),([3,2,6,7],.76),([0,3,7,4],.84),([1,5,6,2],.9)]:
    he=colors[c['material']].lstrip('#');col=[int(int(he[i:i+2],16)*shade)for i in (0,2,4)]
    triangle([vs[face[i]]for i in (0,1,2)],col);triangle([vs[face[i]]for i in (0,2,3)],col)
  for mesh in b.get('meshes',[]):
   if 'vertices' in mesh:
    points=np.array(mesh['vertices']);faces=mesh['faces']
   else:
    c=np.array(mesh['center']);radii=np.array(mesh['size'])/2;pwr=mesh['power'];rings=mesh['rings'];segments=mesh['segments'];points=[];faces=[]
    def sp(v,p):return math.copysign(abs(v)**p,v)
    for i in range(rings+1):
     lat=-math.pi/2+math.pi*i/rings;y=sp(math.sin(lat),2/pwr);g=mesh['taper'][0]+(mesh['taper'][1]-mesh['taper'][0])*(y+1)/2
     for j in range(segments+1):
      lon=2*math.pi*j/segments;points.append(c+radii*np.array([sp(math.cos(lat),2/pwr)*sp(math.cos(lon),2/pwr)*g,y,sp(math.cos(lat),2/pwr)*sp(math.sin(lon),2/pwr)*g]))
    for i in range(rings):
     for j in range(segments):
      a=i*(segments+1)+j;faces.append([a,a+segments+1,a+segments+2,a+1])
    points=np.array(points)
   transformed=[]
   for point in points:
    q=(m@np.r_[point,1])[:3];transformed.append(np.array([q[0],-q[2],24-q[1]]))
   he=colors[mesh['material']].lstrip('#');base=np.array([int(he[i:i+2],16) for i in (0,2,4)])
   light=np.array([-.3,.6,1.]);light/=np.linalg.norm(light)
   for face in faces:
    vs=[transformed[j] for j in face];normal=np.cross(vs[1]-vs[0],vs[2]-vs[0]);length=np.linalg.norm(normal)
    if length<1e-8 and len(vs)==4:normal=np.cross(vs[2]-vs[0],vs[3]-vs[0]);length=np.linalg.norm(normal)
    if length<1e-8:continue
    if 'vertices' not in mesh:
     center=(m@np.r_[mesh['center'],1])[:3];center=np.array([center[0],-center[2],24-center[1]])
     if np.dot(normal,np.mean(vs,axis=0)-center)<0:normal=-normal
    shade=.77+.23*max(0,np.dot(normal/length,light));col=(base*shade).astype(np.uint8)
    triangle(vs[:3],col)
    if len(vs)==4:triangle([vs[0],vs[2],vs[3]],col)
 # The same welded vertices and joint weights consumed by SkinnedCoat in Minecraft.
 from sculpt_coat import matrix
 bind={}
 for b in rig:bind[b['name']]=(bind[b['parent']] if b['parent'] else np.eye(4))@matrix(b)
 for coat in DOC.get('coats',[]):
  pts=np.array(coat['vertices']);ns=np.array(coat['normals']);out=np.zeros_like(pts);norm=np.zeros_like(ns)
  joints=np.array(coat['weights'])[:,:,0].astype(int);weights=np.array(coat['weights'])[:,:,1]
  for j,name in enumerate(coat['bones']):
   transform=matrices[name]@np.linalg.inv(bind[name]);w=np.sum(np.where(joints==j,weights,0),axis=1)
   out+=(pts@transform[:3,:3].T+transform[:3,3])*w[:,None]
   norm+=(ns@np.linalg.inv(transform[:3,:3]))*w[:,None]
  out=np.stack([out[:,0],-out[:,2],24-out[:,1]],axis=1)
  norm=np.stack([norm[:,0],-norm[:,2],-norm[:,1]],axis=1);norm/=np.linalg.norm(norm,axis=1)[:,None]
  light=np.array([-.3,.6,1.]);light/=np.linalg.norm(light)
  shade=(.73+.27*np.maximum(0,norm@light))*np.array(coat['shade'])
  base=np.array([250,251,252]);cols=shade[:,None]*base
  for f in coat['faces']:triangle(out[f],cols[f])
 return Image.fromarray(bg)
VIEWS=[('Front',90),('Three-quarter',62),('Side',0),('Rear',-90),('Sitting',65),('Mouth open',75),('Blink',82),('Bounding',30)]
canvas=Image.new('RGB',(W*4,80+H*2),(218,226,236));draw=ImageDraw.Draw(canvas)
fontpath='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
if not Path(fontpath).exists():fontpath='C:/Windows/Fonts/arial.ttf'
font=ImageFont.truetype(fontpath,24);title=ImageFont.truetype(fontpath,32)
draw.text((45,23),'SADAHARU  /  continuous skinned coat',fill='#27384a',font=title)
for i,(pose,az) in enumerate(VIEWS):
 x=(i%4)*W;y=72+(i//4)*H;canvas.paste(render(pose,az),(x,y));draw.text((x+22,y+6),pose,fill='#344356',font=font)
(ROOT/'docs').mkdir(exist_ok=True);canvas.save(ROOT/'docs/model-review.png')
print('docs/model-review.png',canvas.size)

