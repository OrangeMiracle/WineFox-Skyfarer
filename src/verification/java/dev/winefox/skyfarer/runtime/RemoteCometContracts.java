package dev.winefox.skyfarer.runtime;

import com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble;
import com.github.tartaricacid.touhoulittlemaid.inventory.handler.BaubleItemHandler;
import com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager;
import dev.winefox.skyfarer.item.CometProgress;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.util.*;

/** Actual TLM handler/NBT plus production cleanup call paths, without starting a server. */
public final class RemoteCometContracts {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        // Bootstrap only the bauble bindings required by this test; full TLM startup
        // needs an actual mod loader. Vanilla registered items stand in for mod IDs.
        var constructor=BaubleManager.class.getDeclaredConstructor();constructor.setAccessible(true);
        var manager=constructor.newInstance();var ordinary=new IMaidBauble(){};var eternal=new IMaidBauble(){};
        manager.bind(Items.AMETHYST_SHARD,ordinary);manager.bind(Items.NETHER_STAR,eternal);
        var lookup=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var original=new BaubleItemHandler(30);
        var stack=new ItemStack(Items.AMETHYST_SHARD);for(int i=0;i<57;i++)CometProgress.increment(stack);
        original.setStackInSlot(0,new ItemStack(Items.NETHER_STAR));original.setStackInSlot(17,stack);
        var restored=new BaubleItemHandler(30);restored.deserializeNBT(lookup,original.serializeNBT(lookup));
        check(restored.getStackInSlot(0).is(Items.NETHER_STAR)&&restored.getBaubleInSlot(0)==eternal,"eternal slot/binding lost on TLM NBT reload");
        check(restored.getBaubleInSlot(17)==ordinary&&CometProgress.read(restored.getStackInSlot(17))==57,"ordinary slot/progress lost on TLM NBT reload");
        CometProgress.increment(restored.getStackInSlot(17));check(CometProgress.read(stack)==57,"reloaded stack aliases old entity");
        var maid=read("com/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid");
        check(method(maid,"<init>").instructions.iterator().hasNext(),"missing TLM constructor");
        check(hasField(method(maid,"<init>"),Opcodes.PUTFIELD,"maidBauble"),"native bauble handler no longer initialized in constructor");
        check(hasField(method(maid,"getMaidBauble"),Opcodes.GETFIELD,"maidBauble"),"native accessor changed");
        check(calls(method(maid,"readAdditionalSaveData"),"BaubleItemHandler","deserializeNBT"),"native maid load no longer restores baubles");
        var command=read("dev/winefox/skyfarer/command/CommandManager");
        DispatchOwnershipChecks.verify(command);
        var tick=method(command,"beforeMaidTick");boolean failureCloses=false;
        for(var insn:tick.instructions)if(insn instanceof MethodInsnNode call&&call.owner.endsWith("/TransitFlight")&&call.name.equals("tick")){
            var branch=next(call);check(branch instanceof JumpInsnNode&&branch.getOpcode()==Opcodes.IFNE,"transit failure branch changed; re-audit cleanup");
            for(var at=branch.getNext();at!=null&&at!=((JumpInsnNode)branch).label;at=at.getNext())
                if(at instanceof MethodInsnNode c&&c.owner.equals(command.name)&&c.name.equals("cancel"))failureCloses=true;
        }
        check(failureCloses,"failed transit retains an order/tickets instead of cancel");
        var cancel=method(command,"cancel");
        check(calls(cancel,"Map","remove")&&calls(cancel,"TransitFlight","close")&&calls(cancel,"CommandTickets","release")&&calls(cancel,"CommandTickets","recall"),"cancel must remove order, close provider/session and release or transfer tickets");
        check(calls(method(command,"tickRecalls"),"CommandManager$Recall","expires")&&calls(method(command,"tickRecalls"),"CommandTickets","release"),"Home grace must expire and release tickets");
        check(calls(method(command,"tickRecalls"),"CommandManager","observe"),"completed Home return must publish position before ticket release");
        check(calls(method(command,"releaseHomeControl"),"AirCombatController","dispose"),"Home ground handoff retains controller");
        check(calls(method(command,"clear"),"CommandTickets","clear")&&calls(method(command,"logout"),"CommandManager","cancel")&&calls(method(command,"leave"),"CommandManager","cancel"),"global/owner/entity cleanup disconnected");
        System.out.println("Actual TLM BaubleItemHandler NBT, slot/binding restoration, progress and independent stacks passed (registered vanilla item stand-ins)");
        System.out.println("Native constructor/load and production failed-transit, cancel, finite Home grace, logout/leave/stop cleanup bytecode contracts passed; no real chunk IO claimed");
    }
    static AbstractInsnNode next(AbstractInsnNode node){do{node=node.getNext();}while(node!=null&&node.getOpcode()<0);return node;}
    static boolean hasField(MethodNode method,int opcode,String name){for(var i:method.instructions)if(i instanceof FieldInsnNode f&&f.getOpcode()==opcode&&f.name.equals(name))return true;return false;}
    static boolean calls(MethodNode method,String owner,String name){for(var i:method.instructions)if(i instanceof MethodInsnNode m&&m.owner.endsWith("/"+owner)&&m.name.equals(name))return true;return false;}
    static MethodNode method(ClassNode node,String name){return node.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    static ClassNode read(String name)throws Exception {
        try(var input=RemoteCometContracts.class.getClassLoader().getResourceAsStream(name+".class")){
            check(input!=null,"missing actual dependency "+name);var node=new ClassNode();new ClassReader(input).accept(node,0);return node;
        }
    }
}
