"""Bake a welded, smoothly skinned coat. Requires numpy to author.

The game only loads the baked triangles; no voxel work happens during gameplay.
"""
import math
import numpy as np

def marching_cubes(field,level,spacing):
    """Conforming marching tetrahedra with shared edge vertices (no external mesher)."""
    dims=field.shape; corners=np.array([[0,0,0],[1,0,0],[1,1,0],[0,1,0],[0,0,1],[1,0,1],[1,1,1],[0,1,1]])
    cells=np.stack(np.meshgrid(*(np.arange(n-1) for n in dims),indexing='ij'),axis=-1).reshape(-1,3)
    values=np.stack([field[tuple((cells+c).T)] for c in corners],axis=1)
    cells=cells[(values.min(axis=1)<level)&(values.max(axis=1)>=level)]
    verts=[];faces=[];edges={}
    for cell in cells:
        positions=cell+corners;vals=field[tuple(positions.T)]-level
        ids=np.ravel_multi_index(positions.T,dims)
        def edge(a,b):
            key=tuple(sorted((int(ids[a]),int(ids[b]))))
            if key not in edges:
                edges[key]=len(verts);t=vals[a]/(vals[a]-vals[b]);verts.append((positions[a]+t*(positions[b]-positions[a]))*spacing)
            return edges[key]
        for tet in ((0,1,2,6),(0,2,3,6),(0,3,7,6),(0,7,4,6),(0,4,5,6),(0,5,1,6)):
            inside=[i for i in tet if vals[i]<0];outside=[i for i in tet if vals[i]>=0]
            if len(inside)==1:faces.append([edge(inside[0],i) for i in outside])
            elif len(inside)==3:faces.append([edge(outside[0],i) for i in inside])
            elif len(inside)==2:
                a,b=inside;c,d=outside;q=[edge(a,c),edge(a,d),edge(b,d),edge(b,c)]
                faces.extend([q[:3],[q[0],q[2],q[3]]])
    return np.array(verts),np.array(faces),None,None


def matrix(b):
    x,y,z=np.radians(b['rotation']);cx,cy,cz=np.cos([x,y,z]);sx,sy,sz=np.sin([x,y,z])
    m=np.eye(4)
    m[:3,:3]=np.array([[cz,-sz,0],[sz,cz,0],[0,0,1]])@np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
    m[:3,3]=b['pivot']
    return m


def sculpt(rig):
    by={b['name']:b for b in rig};world={}
    for b in rig:
        world[b['name']]=(world[b['parent']] if b['parent'] else np.eye(4))@matrix(b)
    # Fold small cheek pads into the skull; broad cheeks should not read as spheres.
    for n in ('cheek_-1','cheek_1'):
        by[n]['meshes'][0]['size']=[8.064,9.52,11.76]
    groups=[['body','chest','neck','chest_ruff']+
            [n for n in by if n.endswith('_leg') or n.endswith('_paw') or n.startswith('tail_')],
            ['head','cheek_-1','cheek_1']]
    coats=[]
    for group_index,names in enumerate(groups):
        shapes=[]
        for name in names:
            for mesh in by[name].get('meshes',[]):
                if 'center' in mesh and mesh['material']==0:
                    shapes.append((name,mesh,np.linalg.inv(world[name])))
            by[name]['meshes']=[]
        # Remove detached decorative tabs, replaced by coat-rooted tapered strands.
        for n,b in by.items():
            if n.startswith('cheek_tuft_') or n.startswith('chest_fluff_'): b['meshes']=[]
        def distances(points):
            result=[]
            for name,s,inv in shapes:
                q=points@inv[:3,:3].T+inv[:3,3]-s['center'];r=np.array(s['size'])/2
                q=q/r;g=s['taper'][0]+(s['taper'][1]-s['taper'][0])*(q[:,1]+1)/2
                q[:,0]/=np.maximum(g,.2);q[:,2]/=np.maximum(g,.2)
                result.append((np.sum(abs(q)**s['power'],axis=1)**(1/s['power'])-1)*min(r))
            return np.array(result).T
        step=1.3
        lo=np.array([-22,-40,-34]) if group_index==0 else np.array([-21,-38,-35])
        hi=np.array([22,27,36]) if group_index==0 else np.array([21,-1,2])
        axes=[np.arange(a,b+step,step) for a,b in zip(lo,hi)]
        grid=np.stack(np.meshgrid(*axes,indexing='ij'),axis=-1)
        flat=grid.reshape(-1,3);field=[]
        smooth=.95 if group_index==0 else .55
        for start in range(0,len(flat),24000):
            d=distances(flat[start:start+24000]);minimum=d.min(axis=1)
            field.extend(minimum-smooth*np.log(np.exp(-(d-minimum[:,None])/smooth).sum(axis=1)))
        points,faces,_,_=marching_cubes(np.array(field).reshape(grid.shape[:3]),0,spacing=(step,)*3)
        points+=lo
        # Outward normals are the field gradient, shared across faces and fur roots.
        def sdf(p):
            d=distances(p);v=d.min(axis=1)
            return v-smooth*np.log(np.exp(-(d-v[:,None])/smooth).sum(axis=1))
        normals=np.stack([(sdf(points+np.eye(3)[i]*.025)-sdf(points-np.eye(3)[i]*.025))/.05 for i in range(3)],axis=1)
        normals/=np.linalg.norm(normals,axis=1)[:,None]
        if group_index==0:
            # Fit both edges of the sloping collar to the *fused* neck surface.
            # An ellipse fitted to the old neck primitive disappears inside the new ruff.
            band=by['collar']['meshes'][0];cm=world['collar'];fitted=[]
            for j in range(32):
                a=2*math.pi*j/32;ray=np.array([math.sin(a)*12.2,0,-math.cos(a)*8.7])
                edge=[]
                for y in (-.9,.9):
                    origin=np.array([0,y,0]);low,high=0.,2.5
                    for _ in range(20):
                        t=(low+high)/2;q=cm[:3,:3]@(origin+ray*t)+cm[:3,3]
                        if sdf(q[None,:])[0]<.24:low=t
                        else:high=t
                    edge.append(origin+ray*high)
                fitted.extend([edge[0].tolist(),edge[1].tolist(),(edge[1]-ray/np.linalg.norm(ray)*.5).tolist(),(edge[0]-ray/np.linalg.norm(ray)*.5).tolist()])
            band['vertices']=np.round(fitted,5).tolist()
        bone_names=list(dict.fromkeys(s[0] for s in shapes))
        ds=distances(points);weights=np.zeros((len(points),len(bone_names)))
        # Smooth weights follow the same union as the surface, with a wider joint falloff.
        raw=np.exp(-(ds-ds.min(axis=1)[:,None])/1.8)
        for j,(name,_,_) in enumerate(shapes):weights[:,bone_names.index(name)]+=raw[:,j]
        indices=np.argsort(weights,axis=1)[:,-4:]
        values=np.take_along_axis(weights,indices,axis=1);values/=values.sum(axis=1)[:,None]
        bindings=[[[int(i),round(float(w),6)] for i,w in zip(ix,ws)] for ix,ws in zip(indices,values)]
        verts=points.tolist();norms=normals.tolist();shades=[1.]*len(verts);triangles=[]
        for f in faces:
            if np.dot(np.cross(points[f[1]]-points[f[0]],points[f[2]]-points[f[0]]),normals[f].mean(axis=0))<0:f=f[::-1]
            triangles.append([int(i) for i in f])
        rng=np.random.default_rng(240923+group_index)
        # Flowing small ribbons: broad embedded roots, curved ridges, fine pointed ends.
        # Dense face-center hair would obscure the expression; keep it to the ruff/crown.
        chosen=rng.choice(len(points),min(len(points),1800 if group_index==0 else 620),replace=False)
        for index in chosen:
            p=points[index];normal=normals[index]
            if group_index==0:
                collar_local=np.linalg.inv(world['collar'])@np.r_[p,1]
                if abs(collar_local[1])<1.5 and np.linalg.norm(collar_local[[0,2]]/[12.2,8.7])<1.6:continue
            if group_index==1 and p[2]<-23 and abs(p[0])<10 and p[1]>-25:continue
            if group_index==0 and p[1]>19:continue
            flow=np.array([normal[0]*.7,1.,.24])
            if p[2]>13:flow=np.array([normal[0]*.25,-.25,1.])
            flow-=normal*np.dot(flow,normal)
            if np.linalg.norm(flow)<.1:continue
            flow/=np.linalg.norm(flow);across=np.cross(flow,normal)
            length=rng.uniform(1.3,2.7) if group_index==0 else rng.uniform(1.0,2.1)
            width=rng.uniform(.22,.46);lift=rng.uniform(.3,.6)
            # A longer bib and cheek fringe give the silhouette the reference's shaggy edge.
            if (group_index==0 and p[2]<-15 and p[1]<9) or (group_index==1 and abs(p[0])>10):length*=1.3
            base=p-normal*.07
            tuft=[base-across*width,base+across*width,p+flow*length*.48+normal*lift,
                  p+flow*length+normal*lift*.35]
            first=len(verts);verts.extend(q.tolist() for q in tuft)
            ridge=normal+flow*.2;ridge/=np.linalg.norm(ridge)
            norms.extend([normal.tolist(),normal.tolist(),ridge.tolist(),normal.tolist()]);bindings.extend([bindings[index]]*4)
            shades.extend([.94,.97,1.,.98])
            for f in ([0,1,2],[0,2,3],[2,1,3]):
                a,b,c=[tuft[i] for i in f]
                if np.dot(np.cross(b-a,c-a),normal)<0:f=f[::-1]
                triangles.append([first+i for i in f])
        coats.append(dict(bones=bone_names,vertices=np.round(verts,4).tolist(),normals=np.round(norms,5).tolist(),
                          weights=bindings,shade=shades,faces=triangles,material=0,
                          surface_vertices=len(points)))
    return coats

