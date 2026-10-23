"""Deterministic static Blockbench cube/UV bake; source artwork is never modified."""
import json, math, pathlib, hashlib
root = pathlib.Path(__file__).resolve().parent.parent
source = root / 'tools/素材参考/尾巴导弹/fox_tail_missile.bbmodel'
m = json.loads(source.read_text(encoding='utf-8'))
elements = {e['uuid']: e for e in m['elements']}
def sub(a,b): return [x-y for x,y in zip(a,b)]
def dot(a,b): return sum(x*y for x,y in zip(a,b))
def cross(a,b): return [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]
def unit(a): return [v/math.sqrt(dot(a,a)) for v in a]
def transform(p,node):
    o=node.get('origin',[0,0,0]); x,y,z=sub(p,o)
    rx,ry,rz=[math.radians(v) for v in node.get('rotation',[0,0,0])]
    # Blockbench 4.10 Cube.preview_controller sets THREE Euler order ZYX:
    # apply local X, then Y, then Z (not Three.js's default XYZ order).
    # https://github.com/JannisX11/blockbench/blob/v4.10.4/js/outliner/cube.js
    y,z=y*math.cos(rx)-z*math.sin(rx),y*math.sin(rx)+z*math.cos(rx)
    x,z=x*math.cos(ry)+z*math.sin(ry),-x*math.sin(ry)+z*math.cos(ry)
    x,y=x*math.cos(rz)-y*math.sin(rz),x*math.sin(rz)+y*math.cos(rz)
    return [x+o[0],y+o[1],z+o[2]]
nodes=[]
def visit(items, parents):
    for node in items:
        if isinstance(node,str): nodes.append((elements[node],parents))
        else: visit(node['children'],parents+[node])
visit(m['outliner'],[])
def world(p,e,parents):
    for node in [e]+list(reversed(parents)): p=transform(p,node)
    return p
centers={e['name']:world([(a+b)/2 for a,b in zip(e['from'],e['to'])],e,p) for e,p in nodes}
tip=centers['Tail7']; nozzle=centers['Nozzle_Warm_Core']
forward=unit(sub(tip,nozzle)); right=unit(cross([0,1,0],forward)); up=cross(forward,right)
center=[(a+b)/2 for a,b in zip(tip,nozzle)]
def canonical(p): return [dot(sub(p,center),axis)/32 for axis in (right,up,forward)]
faces=[]
for e,parents in nodes:
    assert e.get('type','cube')=='cube' and e.get('inflate',0)==0
    x,y,z=e['from']; X,Y,Z=e['to']
    corners={'north':[(X,Y,z),(x,Y,z),(x,y,z),(X,y,z)],'south':[(x,Y,Z),(X,Y,Z),(X,y,Z),(x,y,Z)],
      'east':[(X,Y,Z),(X,Y,z),(X,y,z),(X,y,Z)],'west':[(x,Y,z),(x,Y,Z),(x,y,Z),(x,y,z)],
      'up':[(x,Y,z),(X,Y,z),(X,Y,Z),(x,Y,Z)],'down':[(x,y,Z),(X,y,Z),(X,y,z),(x,y,z)]}
    for name,f in e['faces'].items():
        if f.get('texture') is None: continue
        assert f['texture']==0
        u,v,U,V=f['uv']; uv=[(u,v),(U,v),(U,V),(u,V)]
        n=int(f.get('rotation',0)//90)%4; uv=uv[-n:]+uv[:-n] if n else uv
        # Preserve UV corner correspondence, but emit counter-clockwise outward faces.
        points=[canonical(world(p,e,parents)) for p in reversed(corners[name])]
        uv=list(reversed(uv))
        normal=unit(cross(sub(points[1],points[0]),sub(points[2],points[0])))
        face_center=[sum(p[i] for p in points)/4 for i in range(3)]
        cube_center=canonical(world([(a+b)/2 for a,b in zip(e['from'],e['to'])],e,parents))
        assert dot(normal,sub(face_center,cube_center)) > 0, e['name']
        faces.append({'vertices':[[round(t,7) for t in p]+[a/128,b/128] for p,(a,b) in zip(points,uv)],'normal':normal})
result={'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'texture_size':[128,128],
 'front_axis':'+Z','tip':canonical(tip),'nozzle':canonical(nozzle),'cubes':len(nodes),'faces':faces}
dest=root/'src/main/resources/assets/winefox_skyfarer/models/entity/fox_tail_missile.json'
dest.write_text(json.dumps(result,separators=(',',':')),encoding='utf-8')
print(json.dumps({k:v for k,v in result.items() if k!='faces'})); print('faces',len(faces))
