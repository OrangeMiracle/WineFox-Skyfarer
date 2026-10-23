"""Render captured production vertices with the real vanilla spark sprite, without Minecraft."""
import io,json,pathlib,zipfile
from PIL import Image,ImageDraw
root=pathlib.Path(__file__).resolve().parent.parent
frames=json.loads((root/'build/reports/fox3-burst-vertices.json').read_text())
with zipfile.ZipFile(root/'build/moddev/artifacts/neoforge-21.1.250-client-extra-aka-minecraft-resources.jar') as z:
    sprite=Image.open(io.BytesIO(z.read('assets/minecraft/textures/particle/spark_5.png'))).convert('RGBA')
sheet=Image.new('RGB',(1500,1000),(12,15,24))
for i,frame in enumerate(frames):
    panel=Image.new('RGBA',(500,500),(12,15,24,255))
    for x,y,size,r,g,b,a in frame['sparks']:
        if not a: continue
        stamp=sprite.copy()
        stamp.putdata([(int(sr*r/255),int(sg*g/255),int(sb*b/255),int(sa*a/255)) for sr,sg,sb,sa in sprite.get_flattened_data()])
        width=max(1,round(size*65)); stamp=stamp.resize((width,width),Image.Resampling.NEAREST)
        panel.alpha_composite(stamp,(round(250+x*65-width/2),round(455-y*65-width/2)))
    draw=ImageDraw.Draw(panel); draw.text((12,12),f"{frame['tick']/20:.1f}s / tick {frame['tick']}",fill='white')
    sheet.paste(panel.convert('RGB'),((i%3)*500,(i//3)*500))
ImageDraw.Draw(sheet).text((12,978),'Offline production-vertex + vanilla-sprite preview; not an in-game screenshot.',fill='white')
dest=root/'build/reports/fox3-burst-preview.png'; sheet.save(dest); print(dest)
