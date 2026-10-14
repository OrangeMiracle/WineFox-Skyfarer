package dev.winefox.skyfarer.runtime;

import dev.winefox.skyfarer.item.CometProgress;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;

/** Real mapped ItemStack/NBT/SavedData and dependency bytecode; no Minecraft client/server launched. */
public final class CometDataChecks {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var lookup=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var heart=new ItemStack(Items.AMETHYST_SHARD);CustomData.update(DataComponents.CUSTOM_DATA,heart,t->t.putString("unrelated","preserved"));
        for(int i=0;i<57;i++)CometProgress.increment(heart);
        var loaded=ItemStack.parse(lookup,heart.save(lookup)).orElseThrow();
        require(CometProgress.read(loaded)==57&&loaded.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated").equals("preserved"),"actual stack serialization lost progress/components");
        var copied=loaded.copy();CometProgress.increment(copied);require(CometProgress.read(loaded)==57&&CometProgress.read(copied)==58,"copy mutated original component");
        for(int i=0;i<150;i++)CometProgress.increment(copied);require(CometProgress.read(copied)==100,"counter overflow");
        var owner=UUID.randomUUID();var rewards=new CometRewards();require(rewards.remember(owner)&&!rewards.remember(owner),"duplicate reward entitlement");
        var restored=CometRewards.load(rewards.save(new CompoundTag(),lookup),lookup);require(restored.entitled(owner)&&!restored.remember(owner),"offline entitlement lost on reload");
        verifyHooks();verifyResources();
        System.out.println("Actual ItemStack codec persistence/copy, namespaced components, bounded count, SavedData reload/deduplication, native dependency hook descriptors and loot/advancement resources passed");
    }
    static void verifyHooks()throws Exception {
        var calls=calls("net/minecraft/world/entity/LightningBolt.class","tick");
        require(calls.stream().filter(s->s.contains("Level.getEntities(")).count()==2,"lightning ordinal no longer matches dependency");
        int gate=index(calls,"EventHooks.onEntityStruckByLightning("),hit=index(calls,"Entity.thunderHit(");require(gate>=0&&hit>gate,"lightning hit bypasses event gate");
        require(index(calls,"LightningBolt.spawnFire(")>=0&&index(calls,"LightningBolt.spawnFire(")<gate,"native ignition order changed; re-audit HEAD ritual hook");
        require(calls("dev/winefox/skyfarer/mixin/CometLightningMixin.class",null).stream().anyMatch(s->s.contains("CometRitual.strike(")),"ritual hook disconnected");
        require(calls("net/minecraft/world/entity/item/ItemEntity.class","mergeWithNeighbours").stream().anyMatch(s->s.contains("ItemEntity.isMergable(")),"temporary merge-protection hook requires re-audit");
        require(calls("net/minecraft/world/entity/LivingEntity.class","die").stream().anyMatch(s->s.contains("CommonHooks.onLivingDeath(")),"death cancellation gate changed");
        var spear=calls("com/notunanancyowen/spears/mixin/LivingEntityMixin.class",null);
        require(spear.stream().anyMatch(s->s.contains("DamageSources.mobAttack(Lnet/minecraft/world/entity/LivingEntity;)")),"Spears no longer attributes native damage to actual mob");
        var tlm=calls("com/github/tartaricacid/touhoulittlemaid/inventory/handler/BaubleItemHandler.class","updateBaubles");
        require(tlm.stream().anyMatch(s->s.contains("BaubleManager.getBauble(")),"native slot no longer resolves bound bauble");
        for(String name:List.of("VirtualPropulsion","CometBaubles"))require(Files.exists(Path.of("build/classes/java/main/dev/winefox/skyfarer/compat/tlm/"+name+".class")),"missing actual production class");
        var virtual=calls("dev/winefox/skyfarer/compat/tlm/VirtualPropulsion.class",null);
        require(virtual.stream().noneMatch(s->s.contains("FireworkRocketEntity")||s.contains("teleport")||s.contains("setPos")||s.contains("hurtAndBreak")),"virtual provider escaped physics contract");
    }
    static int index(List<String> list,String text){for(int i=0;i<list.size();i++)if(list.get(i).contains(text))return i;return -1;}
    static List<String> calls(String resource,String method)throws Exception {
        var calls=new ArrayList<String>();try(var input=CometDataChecks.class.getClassLoader().getResourceAsStream(resource)){
            require(input!=null,"missing dependency "+resource);new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9){
                @Override public MethodVisitor visitMethod(int a,String name,String desc,String sig,String[] ex){if(method!=null&&!name.equals(method))return null;return new MethodVisitor(Opcodes.ASM9){
                    @Override public void visitMethodInsn(int op,String owner,String name,String desc,boolean itf){calls.add(owner+"."+name+desc);}
                };}
            },0);
        }return calls;
    }
    static void verifyResources()throws Exception {
        var root=Path.of("src/main/resources/data");
        var advancement=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("winefox_skyfarer/advancement/silver_wing_successor.json"))).getAsJsonObject();
        var display=advancement.getAsJsonObject("display");require(display.get("hidden").getAsBoolean()&&display.get("show_toast").getAsBoolean()&&display.getAsJsonObject("icon").get("id").getAsString().equals("minecraft:elytra"),"advancement presentation");
        require(advancement.getAsJsonObject("criteria").getAsJsonObject("awakened").get("trigger").getAsString().equals("minecraft:impossible"),"advancement can unlock without evolution");
        var expected=Map.of("woodland_mansion",.02,"ancient_city",.03,"end_city_treasure",.05);
        for(var entry:expected.entrySet()){
            var json=com.google.gson.JsonParser.parseString(Files.readString(root.resolve("winefox_skyfarer/loot_modifiers/comet_"+entry.getKey()+".json"))).getAsJsonObject();
            var conditions=json.getAsJsonArray("conditions");require(conditions.get(0).getAsJsonObject().get("loot_table_id").getAsString().equals("minecraft:chests/"+entry.getKey())&&conditions.get(1).getAsJsonObject().get("chance").getAsDouble()==entry.getValue(),"wrong loot table/chance");
        }
        require(!Files.exists(root.resolve("minecraft/loot_table/chests")),"vanilla table overridden");
    }
}
