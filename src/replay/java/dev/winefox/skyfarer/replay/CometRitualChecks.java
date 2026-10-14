package dev.winefox.skyfarer.replay;

import dev.winefox.skyfarer.WineFoxSkyfarer;
import dev.winefox.skyfarer.item.CometProgress;
import dev.winefox.skyfarer.runtime.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.*;
import java.util.UUID;

/** Actual production ritual against world/entity fixtures. No maid is created in the ritual cases. */
public final class CometRitualChecks {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static ItemEntity drop(ServerLevel world,Object item,int count,double x){var stack=new ItemStack(item);stack.setCount(count);var entity=new ItemEntity(world,stack);entity.pos=new Vec3(x,0,0);return entity;}
    static ItemEntity heart(ServerLevel world){return drop(world,WineFoxSkyfarer.COMET_HEART.get(),1,0);}
    static int fuel(ServerLevel world){return world.entities.stream().filter(e->e instanceof ItemEntity&&e.alive).map(e->((ItemEntity)e).getItem()).filter(s->s.is(Items.TNT)).mapToInt(ItemStack::getCount).sum();}
    public static void main(String[] args){
        CometRewards.INSTANCE.owners.clear();
        insufficientAndGates();transactionAndWard();attribution();
        CometRewards.INSTANCE.owners.clear();CometChecks.evolution();
        System.out.println("Dropped-item ritual and unchanged kill evolution checks passed");
    }
    static void insufficientAndGates(){
        var world=new ServerLevel();var core=heart(world);var a=drop(world,Items.TNT,32,1);var b=drop(world,Items.TNT,31,-1);
        var before=core.getItem();var bolt=new LightningBolt(world);
        require(!CometRitual.strike(bolt,false,2)&&fuel(world)==63&&core.getItem()==before,"insufficient total partially mutated inputs");
        b.getItem().setCount(32);require(!CometRitual.strike(bolt,false,2),"repeat event retried failed strike");
        bolt=new LightningBolt(world);
        require(!CometRitual.strike(bolt,true,2)&&!CometRitual.strike(bolt,false,1),"visual or later flash accepted");
        world.server.sameThread=false;require(!CometRitual.strike(bolt,false,2),"off-thread transaction accepted");world.server.sameThread=true;
        b.pos=new Vec3(3.01,0,0);require(!CometRitual.strike(bolt,false,2)&&fuel(world)==64,"out-of-radius material used");
        b.pos=new Vec3(1,0,0);b.alive=false;require(!CometRitual.strike(new LightningBolt(world),false,2)&&a.getItem().getCount()==32,"picked-up/removed material used");
        require(core.getItem()==before&&!CometRitual.protectedItem(core),"failed ritual altered heart/protection");
        var client=new ReplayWorld();require(!CometRitual.strike(new LightningBolt(client),false,2),"non-server world accepted");
        System.out.println("Ritual rejects insufficient, picked-up, out-of-radius, visual, later-flash and off-thread inputs without partial payment");
    }
    static void transactionAndWard(){
        var world=new ServerLevel();world.time=100;
        var core=heart(world);for(int i=0;i<19;i++)CometProgress.increment(core.getItem());
        var a=drop(world,Items.TNT,32,1);var b=drop(world,Items.TNT,40,-1);var distant=drop(world,Items.TNT,64,4);
        UUID originalEntity=core.id;int entities=world.entities.size();var bolt=new LightningBolt(world);
        require(CometRitual.strike(bolt,false,2),"valid dropped-item recipe failed without maid/weather");
        require(core.id.equals(originalEntity)&&world.entities.size()==entities+1&&core.getItem().is(WineFoxSkyfarer.EVERBURNING_COMET_HEART.get())&&core.getItem().getCount()==1,"result duplicated or entity identity lost");
        require(CometProgress.read(core.getItem())==19,"conversion lost components");
        require(fuel(world)==72&&a.getItem().getCount()+b.getItem().getCount()==8&&distant.getItem().getCount()==64,"did not consume exactly 64 local TNT");
        require(!CometRitual.strike(bolt,false,2)&&fuel(world)==72,"duplicate strike consumed again");
        for(var source:new Object[]{DamageTypes.LIGHTNING_BOLT,net.minecraft.tags.DamageTypeTags.IS_FIRE,net.minecraft.tags.DamageTypeTags.IS_EXPLOSION})
            require(CometRitual.protectedFrom(core,new DamageSource(null,source)),"result not protected against ritual aftermath");
        require(!CometRitual.protectedFrom(core,new DamageSource(null,new Object())),"temporary ward grants unrelated invulnerability");
        for(var item:new ItemEntity[]{a,b})if(!item.getItem().isEmpty())require(CometRitual.protectedItem(item),"surplus lost protection");
        require(!CometRitual.protectedItem(distant),"distant material affected");
        var persisted=core.getPersistentData().copy();var reloaded=new ItemEntity(world,core.getItem().copy());reloaded.getPersistentData().putLong("winefox_skyfarer:comet_ward_until",persisted.getLong("winefox_skyfarer:comet_ward_until"));
        world.time=219;require(CometRitual.protectedItem(reloaded),"ward lost before deadline");world.time=220;
        require(!CometRitual.protectedItem(core)&&!CometRitual.protectedItem(reloaded),"ward lifetime extended/permanent");
        System.out.println("Atomic conversion: one existing entity, 32+40 TNT -> 8 surplus, component preservation, duplicate rejection and finite persisted aftermath protection passed");
    }
    static void attribution(){
        var rewards=CometRewards.INSTANCE;rewards.owners.clear();
        var world=new ServerLevel();var core=heart(world);drop(world,Items.TNT,64,1);
        var thrower=UUID.randomUUID();core.thrower=thrower;world.server.profiles.known.add(thrower);
        var bolt=new LightningBolt(world);var cause=new ServerPlayer(world);bolt.cause=cause;
        require(CometRitual.strike(bolt,false,2)&&rewards.owners.contains(cause.id)&&!rewards.owners.contains(thrower),"known lightning cause lacks precedence");
        world=new ServerLevel();core=heart(world);core.thrower=thrower;world.server.profiles.known.add(thrower);drop(world,Items.TNT,64,1);
        require(CometRitual.strike(new LightningBolt(world),false,2)&&rewards.owners.contains(thrower),"offline known thrower lost reward entitlement");
        int owners=rewards.owners.size();world=new ServerLevel();core=heart(world);core.thrower=UUID.randomUUID();drop(world,Items.TNT,64,1);new ServerPlayer(world);
        require(CometRitual.strike(new LightningBolt(world),false,2)&&rewards.owners.size()==owners,"unknown thrower rewarded an unrelated nearby player");
        System.out.println("Reward attribution: lightning cause > positively identified offline thrower; unattributed ritual yields item without bystander reward");
    }
}
