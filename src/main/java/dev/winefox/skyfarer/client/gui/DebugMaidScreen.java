package dev.winefox.skyfarer.client.gui;

import dev.winefox.skyfarer.network.*;
import dev.winefox.skyfarer.runtime.DebugSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class DebugMaidScreen extends Screen {
    private CompoundTag data;private EditBox search;private String query="";private boolean dropdown;private int ownerPage;
    public DebugMaidScreen(CompoundTag data){super(Component.literal("女仆调试管理"));this.data=data;}
    public UUID session(){return data.getUUID("session");}
    public void update(CompoundTag next){if(search!=null)query=search.getValue();data=next;rebuildWidgets();}
    private int left(){return Math.max(8,(width-740)/2);}
    private int span(){return width-2*left();}
    private int rows(){return Math.clamp((height-132)/65,1,20);}
    private void request(String action,UUID maid,boolean enabled,int page,UUID owner){
        var tag=new CompoundTag();tag.putUUID("session",session());tag.putString("action",action);
        if(maid!=null)tag.putUUID("maid",maid);tag.putBoolean("enabled",enabled);
        tag.putUUID("owner",owner);tag.putInt("page",page);tag.putInt("size",rows());
        query=search==null?query:search.getValue();tag.putString("query",query);
        PacketDistributor.sendToServer(new DebugPanelActionPayload(tag));
    }
    private void list(int page,UUID owner){request("list",null,false,page,owner);}
    @Override protected void init(){
        int x=left(),w=span();
        search=addRenderableWidget(new EditBox(font,x,29,Math.max(80,w-94),20,Component.literal("按玩家名搜索")));
        search.setMaxLength(64);search.setValue(query);search.setHint(Component.literal("输入玩家名或 UUID"));
        addRenderableWidget(Button.builder(Component.literal("搜索玩家"),b->{dropdown=true;ownerPage=0;list(0,data.getUUID("owner"));}).bounds(x+w-90,29,90,20).build());
        addRenderableWidget(Button.builder(Component.literal(data.getString("owner_name")+(dropdown?" ▲":" ▼")),b->{dropdown=!dropdown;rebuildWidgets();}).bounds(x,53,w-74,20).build());
        addRenderableWidget(Button.builder(Component.literal("刷新"),b->list(data.getInt("page"),data.getUUID("owner"))).bounds(x+w-70,53,70,20).build());
        if(dropdown){
            var owners=data.getList("owners",Tag.TAG_COMPOUND);int count=Math.max(1,Math.min(6,(height-124)/22));
            ownerPage=Math.min(ownerPage,Math.max(0,(owners.size()-1)/count));
            for(int i=ownerPage*count;i<Math.min(owners.size(),(ownerPage+1)*count);i++){
                var owner=owners.getCompound(i);addRenderableWidget(Button.builder(Component.literal(owner.getString("name")),b->{dropdown=false;list(0,owner.getUUID("id"));}).bounds(x,78+(i%count)*22,w,20).build());
            }
            var prev=addRenderableWidget(Button.builder(Component.literal("< 玩家"),b->{ownerPage--;rebuildWidgets();}).bounds(x,height-30,80,20).build());prev.active=ownerPage>0;
            var next=addRenderableWidget(Button.builder(Component.literal("玩家 >"),b->{ownerPage++;rebuildWidgets();}).bounds(x+w-80,height-30,80,20).build());next.active=(ownerPage+1)*count<owners.size();
        }else{
            var maids=data.getList("maids",Tag.TAG_COMPOUND);
            for(int i=0;i<Math.min(rows(),maids.size());i++){
                var maid=maids.getCompound(i);int y=80+i*65;
                addRenderableWidget(Checkbox.builder(Component.literal("绑定"),font).pos(x,y+38).selected(maid.getBoolean("bound")).onValueChange((box,value)->request("bind",maid.getUUID("id"),value,data.getInt("page"),data.getUUID("owner"))).build());
                var visit=addRenderableWidget(Button.builder(Component.literal("玩家 → 女仆"),b->request("visit",maid.getUUID("id"),false,data.getInt("page"),data.getUUID("owner"))).bounds(x+w-200,y+37,98,20).build());
                var bring=addRenderableWidget(Button.builder(Component.literal("女仆 → 玩家"),b->request("bring",maid.getUUID("id"),false,data.getInt("page"),data.getUUID("owner"))).bounds(x+w-99,y+37,99,20).build());
                visit.active=bring.active=!data.getBoolean("busy");
            }
            int page=data.getInt("page"),size=data.getInt("size");
            var prev=addRenderableWidget(Button.builder(Component.literal("<"),b->list(page-1,data.getUUID("owner"))).bounds(x,height-30,45,20).build());prev.active=page>0;
            var next=addRenderableWidget(Button.builder(Component.literal(">"),b->list(page+1,data.getUUID("owner"))).bounds(x+w-45,height-30,45,20).build());next.active=(page+1)*size<data.getInt("total");
            // Request a page sized for the current GUI scale; the server caps it independently.
            if(size!=rows())list(0,data.getUUID("owner"));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-44,height-30,88,20).build());
    }
    private void fit(GuiGraphics g,String text,int x,int y,int max,int color){
        float scale=Math.min(1,(float)max/Math.max(1,font.width(text)));g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);g.drawString(font,text,0,0,color);g.pose().popPose();
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float tick){
        super.render(g,mouseX,mouseY,tick);g.drawCenteredString(font,title,width/2,12,0xFFFFFF);
        if(dropdown){if(data.getList("owners",Tag.TAG_COMPOUND).isEmpty())g.drawString(font,"没有匹配的玩家",left(),82,0xAAAAAA);return;}
        var maids=data.getList("maids",Tag.TAG_COMPOUND);int x=left(),w=span();
        for(int i=0;i<Math.min(rows(),maids.size());i++){
            var maid=maids.getCompound(i);var pos=DebugSnapshot.vector(maid,"position");int y=80+i*65;
            String distance=maid.getDouble("distance")<0?"(None)":String.format(Locale.ROOT,"(%.0fm)",maid.getDouble("distance"));
            fit(g,maid.getString("name")+"  "+distance+(maid.getBoolean("loaded")?"":"  （已卸载／最后记录）"),x,y,w,0x9DFFAD);
            fit(g,maid.getUUID("id").toString(),x,y+12,w,0xDDDDDD);
            fit(g,String.format(Locale.ROOT,"%.1f, %.1f, %.1f  %s",pos.x,pos.y,pos.z,maid.getString("dimension")),x,y+24,w,0xAACCFF);
        }
        if(maids.isEmpty())g.drawString(font,"该玩家暂无女仆记录",x,85,0xAAAAAA);
        g.drawCenteredString(font,String.format(Locale.ROOT,"第 %d / %d 页 · %d 位女仆",data.getInt("page")+1,Math.max(1,(data.getInt("total")+Math.max(1,data.getInt("size"))-1)/Math.max(1,data.getInt("size"))),data.getInt("total")),width/2,height-43,0xBBBBBB);
    }
    @Override public boolean isPauseScreen(){return false;}
}
