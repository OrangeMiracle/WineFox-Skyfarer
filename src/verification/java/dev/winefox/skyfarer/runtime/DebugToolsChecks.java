package dev.winefox.skyfarer.runtime;

import dev.winefox.skyfarer.client.debug.ClientDebugState;
import dev.winefox.skyfarer.network.AirCombatDebugPayload;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

public final class DebugToolsChecks {
    public static void main(String[] args)throws Exception {
        UUID a=new UUID(0,1),b=new UUID(0,2),c=new UUID(0,3);
        var selection=new DebugSelection();selection.cycle(-1);require(selection.selected()==null,"empty cycle");
        selection.add(a,true);selection.add(b,true);selection.add(c,true);selection.cycle(1);require(selection.selected().equals(a),"forward wraps");
        selection.cycle(-1);require(selection.selected().equals(c),"backward wraps");
        selection.add(a,false);require(selection.selected().equals(c)&&selection.ids().size()==3,"updates cannot steal selection or duplicate bindings");
        selection.remove(c);require(selection.selected().equals(b),"remove selected tail picks adjacent");selection.remove(a);require(selection.selected().equals(b),"remove other preserves selection");
        selection.remove(b);require(selection.selected()==null&&selection.ids().isEmpty(),"last removal clears panel");

        ClientDebugState.clear();accept(a,false,true,"A",1);accept(b,false,true,"B",2);accept(c,false,false,"C",3);
        require(ClientDebugState.all().size()==3&&ClientDebugState.selection.selected().equals(b),"three independent snapshots, one selected");
        accept(b,true,false,"B",2);accept(a,false,false,"A live update",4);
        require(ClientDebugState.current().getBoolean("unloaded")&&ClientDebugState.current().getDouble("x")==2,"unload retains last position and selected HUD while others update");
        ClientDebugState.selection.cycle(1);require(ClientDebugState.current().getString("maid_name").equals("C"),"switching finds corresponding snapshot");
        accept(b,false,false,"B reloaded",5);ClientDebugState.selection.cycle(-1);
        require(!ClientDebugState.current().getBoolean("unloaded")&&ClientDebugState.current().getDouble("x")==5,"reload replaces stale data under same UUID");
        var remove=new CompoundTag();remove.putUUID("remove",b);ClientDebugState.accept(new AirCombatDebugPayload(remove));
        require(ClientDebugState.all().size()==2&&ClientDebugState.selection.selected().equals(c),"unbind removes only one snapshot");
        ClientDebugState.accept(new AirCombatDebugPayload(new CompoundTag()));require(ClientDebugState.all().isEmpty()&&!ClientDebugState.current().hasUUID("maid"),"logout/clear removes every binding");

        try(var jar=new JarFile("libs/touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar")){
            var world=read(jar,"com/github/tartaricacid/touhoulittlemaid/world/data/MaidWorldData");
            require(world.fields.stream().anyMatch(f->f.name.equals("infos")&&f.desc.equals("Ljava/util/Map;")),"exact registry accessor field");
            require(world.methods.stream().anyMatch(m->m.name.equals("getInfos")&&m.desc.equals("(Ljava/util/UUID;)Ljava/util/List;")),"actual owner index method");
            var maid=read(jar,"com/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid");
            require(maid.methods.stream().noneMatch(m->m.name.equals("teleportTo")&&m.desc.startsWith("(Lnet/minecraft/server/level/ServerLevel;")),"TLM must inherit command teleport rather than portal override");
        }
        try(var jar=new JarFile("build/moddev/artifacts/neoforge-21.1.250-merged.jar")){
            var entity=read(jar,"net/minecraft/world/entity/Entity");
            var teleport=entity.methods.stream().filter(m->m.name.equals("teleportTo")&&m.desc.startsWith("(Lnet/minecraft/server/level/ServerLevel;")).findFirst().orElseThrow();
            require(calls(teleport,"restoreFrom")&&calls(teleport,"addDuringTeleport")&&!calls(teleport,"changeDimension"),"cross-dimension command teleport copies data and does not invoke TLM portal behavior");
        }
        // Common-side code must be loadable by dedicated servers without GUI/render classes.
        for(String name:List.of("DebugWatchManager","DebugPanelManager")){
            var node=new ClassNode();new ClassReader(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("build/classes/java/main/dev/winefox/skyfarer/runtime/"+name+".class"))).accept(node,0);
            for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call)require(!call.owner.startsWith("net/minecraft/client/")&&!call.owner.startsWith("dev/winefox/skyfarer/client/"),"common code links a client class");
        }
        System.out.println("Debug tools: multi-binding, wraparound, unloaded snapshot retention, reload, unbind, logout, exact TLM/teleport APIs and common-side isolation passed.");
    }
    private static void accept(UUID id,boolean unloaded,boolean select,String name,double x){var tag=new CompoundTag();tag.putUUID("maid",id);tag.putString("maid_name",name);tag.putBoolean("unloaded",unloaded);tag.putBoolean("select",select);tag.putDouble("x",x);ClientDebugState.accept(new AirCombatDebugPayload(tag));}
    private static ClassNode read(JarFile jar,String name)throws Exception{var node=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class"))).accept(node,ClassReader.SKIP_DEBUG);return node;}
    private static boolean calls(MethodNode method,String name){for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call&&call.name.equals(name))return true;return false;}
    private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
}
