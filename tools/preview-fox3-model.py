"""Offline orthographic QA of the exact packaged mesh/UV/PNG, not a Minecraft screenshot."""
import json, math, pathlib
from PIL import Image, ImageDraw
root=pathlib.Path(__file__).resolve().parent.parent
assets=root/'src/main/resources/assets/winefox_skyfarer'
model=json.loads((assets/'models/entity/fox_tail_missile.json').read_text())
texture=Image.open(assets/'textures/entity/fox_tail_missile.png').convert('RGBA')
size=600

def render(yaw,pitch):
    image=Image.new('RGB',(size,size),(26,29,36)); depth=[-float('inf')]*(size*size)
    cy,sy=math.cos(yaw),math.sin(yaw); cp,sp=math.cos(pitch),math.sin(pitch)
    def rotate(v):
        x,y,z=v[:3]; x,z=x*cy+z*sy,-x*sy+z*cy
        return x,y*cp-z*sp,y*sp+z*cp
    for face in model['faces']:
        points=[]
        for v in face['vertices']:
            x,y,z=rotate(v); points.append((size/2+x*430,size/2-y*430,z,v[3],v[4]))
        n=rotate(face['normal']); light=.6+.4*max(0,n[0]*-.3+n[1]*.7+n[2]*.65)
        for indices in ((0,1,2),(0,2,3)):
            a,b,c=[points[i] for i in indices]
            area=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1])
            if abs(area)<1e-9: continue
            for y in range(max(0,math.floor(min(a[1],b[1],c[1]))),min(size,math.ceil(max(a[1],b[1],c[1]))+1)):
                for x in range(max(0,math.floor(min(a[0],b[0],c[0]))),min(size,math.ceil(max(a[0],b[0],c[0]))+1)):
                    s=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/area
                    t=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/area
                    w=1-s-t
                    if min(s,t,w)<-1e-7: continue
                    z=s*a[2]+t*b[2]+w*c[2]; index=y*size+x
                    if z<=depth[index]: continue
                    u=s*a[3]+t*b[3]+w*c[3]; v=s*a[4]+t*b[4]+w*c[4]
                    rgba=texture.getpixel((min(127,max(0,int(u*128))),min(127,max(0,int(v*128)))))
                    if rgba[3]<128: continue
                    depth[index]=z; image.putpixel((x,y),tuple(int(q*min(1,light)) for q in rgba[:3]))
    return image

result=Image.new('RGB',(size*2,size),(26,29,36))
result.paste(render(-1.0,.45),(0,0)); result.paste(render(2.2,.45),(size,0))
draw=ImageDraw.Draw(result)
draw.text((20,20),'PACKAGED FOX3 MODEL - front / white tip',fill='white')
draw.text((size+20,20),'Rear / mechanical exhaust',fill='white')
draw.text((20,size-25),'Offline mesh + texture preview (not in-game rendering)',fill='white')
dest=root/'build/reports/fox3-model-preview.png'; dest.parent.mkdir(parents=True,exist_ok=True)
result.save(dest); print(dest)
