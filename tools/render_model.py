"""Offline inspection of exact runtime cuboids, with orthographic views and poses."""
import json, math
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from mpl_toolkits.mplot3d.art3d import Poly3DCollection
from make_assets import colors
ROOT=Path(__file__).resolve().parents[1]
rig=json.loads((ROOT/'src/main/resources/assets/hexsadaharu/models/entity/sadaharu.json').read_text())['bones']
def render(ax,pose,azim):
 matrices={};polys=[];cs=[]
 for b in rig:
  p=np.array(b['pivot'],float);r=np.radians(b['rotation'])
  n=b['name']
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
    q=(m@np.r_[o+s*[x,y,z],1])[:3];vs.append([q[0],-q[2],24-q[1]])
   for face,shade in [([0,1,2,3],.98),([4,7,6,5],.75),([0,4,5,1],1),([3,2,6,7],.7),([0,3,7,4],.88),([1,5,6,2],.93)]:
    polys.append([vs[i] for i in face]);rgb=matplotlib.colors.to_rgb(colors[c['material']]);cs.append(tuple(v*shade for v in rgb))
 ax.add_collection3d(Poly3DCollection(polys,facecolors=cs,edgecolors='none',zsort='average'))
 ax.set_xlim(-28,28);ax.set_ylim(-30,35);ax.set_zlim(0,61);ax.set_box_aspect((56,65,61));ax.view_init(elev=12,azim=azim);ax.set_proj_type('ortho');ax.set_axis_off();ax.set_facecolor('#dce2e9')
fig=plt.figure(figsize=(16,10),facecolor='#dce2e9')
for i,(pose,az) in enumerate([('Front',90),('Side',0),('Rear',-90),('Sitting',65),('Mouth open',75),('Bounding',30)]):
 ax=fig.add_subplot(2,3,i+1,projection='3d');render(ax,pose,az);ax.set_title(pose,color='#2d3748',fontsize=14,y=.96)
fig.suptitle('SADAHARU · runtime cuboid geometry review',color='#28384a',fontsize=21,y=.98)
fig.subplots_adjust(left=0,right=1,top=.94,bottom=0,wspace=-.05,hspace=-.06)
(ROOT/'docs').mkdir(exist_ok=True);fig.savefig(ROOT/'docs/model-review.png',dpi=140)
print(ROOT/'docs/model-review.png')
