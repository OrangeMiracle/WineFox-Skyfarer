package dev.winefox.skyfarer.command;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.ai.*;
import dev.winefox.skyfarer.compat.tlm.*;
import dev.winefox.skyfarer.config.SkyfarerSettings;
import dev.winefox.skyfarer.flight.HeadingController;
import dev.winefox.skyfarer.item.CommandSpyglassItem;
import dev.winefox.skyfarer.network.CommandMarksPayload;
import dev.winefox.skyfarer.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server authority for marks, ownership, bounded waking and individual assignments. */
public final class CommandManager {
    private static final class Session {
        final ServerLevel level;final MarkQueue marks=new MarkQueue();final Map<UUID,CompoundTag> snapshots=new HashMap<>();
        final Set<UUID> attempted=new HashSet<>();long lastClick=-100;
        Session(ServerLevel level){this.level=level;}
    }
    private static final class Order {
        final UUID owner;final ServerLevel level;final boolean creative;final TransitFlight flight=new TransitFlight();
        UUID target;Vec3 destination;boolean transit,finishing;int stalled,landedTicks,loadingTicks;
        Order(UUID owner,ServerLevel level,boolean creative,UUID target,Vec3 destination,boolean transit){this.owner=owner;this.level=level;this.creative=creative;this.target=target;this.destination=destination;this.transit=transit;}
    }
    private record Wake(UUID owner,ServerLevel level,UUID maid,long expires){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final Map<UUID,Order> ORDERS=new HashMap<>();
    private static final Map<UUID,Wake> WAKES=new HashMap<>();
    private static final Map<UUID,Long> RETRY=new HashMap<>();
    public static void install(){CommandAccess.targets=CommandManager::targets;CommandAccess.anchor=maid->{var o=ORDERS.get(maid.getUUID());return o==null?null:o.destination;};}
    public static boolean inTransit(EntityMaid maid){var o=ORDERS.get(maid.getUUID());return o!=null&&o.transit;}
    public static boolean inWaterEscape(EntityMaid maid){var o=ORDERS.get(maid.getUUID());return o!=null&&o.transit&&o.flight.waterEscape();}
    public static CompoundTag debug(EntityMaid maid) {
        var o=ORDERS.get(maid.getUUID());if(o==null||!o.transit)return null;
        var data=DebugSnapshot.make(maid,AirCombatState.APPROACH,null,null,null,0,o.destination.subtract(maid.position()).normalize(),0,
            new AirRoute(List.of(new AirRoute.Waypoint("COMMAND_TRANSIT",o.destination))));
        data.putString("state","COMMAND_TRANSIT");data.putString("reason","指挥镜赶路：48 格内交接战斗");
        data.putString("target_name",o.level.getEntity(o.target) instanceof LivingEntity t?t.getName().getString():"远程标记");
        DebugSnapshot.vector(data,"target",o.destination);data.putDouble("target_distance",maid.position().distanceTo(o.destination));
        o.flight.debug(data,maid);return data;
    }
    public static void toggle(ServerPlayer player) {
        if(!player.isAlive()||player.isSpectator())return;
        var stack=player.isUsingItem()?player.getUseItem():player.getMainHandItem();
        if(!(stack.getItem() instanceof CommandSpyglassItem))stack=player.getOffhandItem();
        if(!(stack.getItem() instanceof CommandSpyglassItem scope))return;
        if(scope.unlimited()&&!player.isCreative())return;
        var session=SESSIONS.get(player.getUUID());
        if(session==null||session.level!=player.serverLevel()){logout(player.getUUID());session=new Session(player.serverLevel());SESSIONS.put(player.getUUID(),session);}
        long now=player.level().getGameTime();if(now-session.lastClick<4)return;session.lastClick=now;
        LivingEntity target=pick(player);if(target==null)return;
        boolean added=session.marks.toggle(target.getUUID(),scope.unlimited());
        session.attempted.clear();remember(session,target);sync(player,session);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(added?"message.winefox_skyfarer.marked":"message.winefox_skyfarer.unmarked",target.getDisplayName(),session.marks.ids().size()),true);
        SkyfarerMaidRegistry.get(player.server).seed(player);
    }
    private static LivingEntity pick(ServerPlayer player) {
        Vec3 from=player.getEyePosition(),direction=player.getLookAngle();double range=SkyfarerSettings.get("command_mark_range");
        // Never generate chunks to raycast a mark.
        for(double d=0;d<=range;d+=2)if(!player.level().hasChunkAt(BlockPos.containing(from.add(direction.scale(d))))){range=Math.max(0,d-2);break;}
        Vec3 end=from.add(direction.scale(range));
        var block=player.level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
        LivingEntity best=null;double distance=from.distanceToSqr(end);
        for(var entity:player.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(1),
            e->e.isAlive()&&!(e instanceof Player)&&!(e instanceof EntityMaid)&&!e.isSpectator()&&!e.isInvulnerable()&&!player.isAlliedTo(e))) {
            var hit=entity.getBoundingBox().inflate(.1).clip(from,end);
            if(hit.isPresent()&&from.distanceToSqr(hit.get())<distance){best=entity;distance=from.distanceToSqr(hit.get());}
        }return best;
    }
    private static void remember(Session s,LivingEntity target) {
        var data=new CompoundTag();data.putUUID("id",target.getUUID());data.putInt("entity",target.getId());data.putString("name",target.getName().getString());
        DebugSnapshot.vector(data,"position",target.position());data.putDouble("width",target.getBbWidth());data.putDouble("height",target.getBbHeight());data.putLong("seen",s.level.getGameTime());s.snapshots.put(target.getUUID(),data);
    }
    private static void sync(ServerPlayer player,Session s) {
        var data=new CompoundTag();data.putString("dimension",s.level.dimension().location().toString());var list=new ListTag();
        for(UUID id:s.marks.ids())if(s.snapshots.containsKey(id))list.add(s.snapshots.get(id).copy());
        data.put("marks",list);PacketDistributor.sendToPlayer(player,new CommandMarksPayload(data));
    }
    public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();long now=server.overworld().getGameTime();
        RETRY.entrySet().removeIf(e->e.getValue()<=now);
        for(var pair:new ArrayList<>(SESSIONS.entrySet())) {
            var player=server.getPlayerList().getPlayer(pair.getKey());var s=pair.getValue();
            if(player==null||!player.isAlive()||player.serverLevel()!=s.level){logout(pair.getKey());continue;}
            for(UUID id:s.marks.ids()) {
                var target=s.level.getEntity(id);
                if(target instanceof LivingEntity living&&living.isAlive())remember(s,living);
                else if(target!=null || !s.snapshots.containsKey(id))s.marks.remove(id);
                else {
                    var snapshot=s.snapshots.get(id);
                    // Unloaded is not dead: arbitrarily distant dispatches have no elapsed-time cap.
                    // An absent entity in a fully ticking last-known chunk is stale after a grace period.
                    if(s.level.getGameTime()-snapshot.getLong("seen")>100&&s.level.isPositionEntityTicking(BlockPos.containing(DebugSnapshot.vector(snapshot,"position"))))s.marks.remove(id);
                }
            }
            s.snapshots.keySet().retainAll(s.marks.ids());
            if(now%5==0)sync(player,s);
            if(now%10!=0 || s.marks.isEmpty())continue;
            int budget=2;
            for(var entry:SkyfarerMaidRegistry.get(server).owned(player.getUUID())) {
                if(!entry.dimension().equals(s.level.dimension().location().toString())||ORDERS.containsKey(entry.id())||RETRY.getOrDefault(entry.id(),0L)>now)continue;
                var entity=s.level.getEntity(entry.id());
                if(entity instanceof EntityMaid maid){assign(player,s,maid);continue;}
                boolean unlimited=s.marks.ids().stream().anyMatch(s.marks::creative);
                boolean nearby=player.position().distanceTo(Vec3.atCenterOf(entry.pos()))<=SkyfarerSettings.get("command_home_range");
                boolean discover=unlimited||nearby||entry.known()&&!entry.home();
                if(!discover || entry.known()&&!entry.skyfarer() || s.attempted.contains(entry.id()) || WAKES.containsKey(entry.id()) || WAKES.size()>=8 || budget<=0)continue;
                // Unknown legacy entries are only discovery candidates, never permission to command.
                budget--;s.attempted.add(entry.id());CommandTickets.wake(s.level,entry.id(),entry.pos());
                WAKES.put(entry.id(),new Wake(player.getUUID(),s.level,entry.id(),now+100));
            }
        }
        for(var wake:new ArrayList<>(WAKES.values())) {
            var player=server.getPlayerList().getPlayer(wake.owner);var s=SESSIONS.get(wake.owner);var entity=wake.level.getEntity(wake.maid);
            if(entity instanceof EntityMaid maid) {
                SkyfarerMaidRegistry.get(server).observe(maid);
                if(player!=null&&s!=null)assign(player,s,maid);
            }
            if(entity!=null||now>wake.expires||s==null||s.marks.isEmpty()) {
                WAKES.remove(wake.maid);if(!ORDERS.containsKey(wake.maid))CommandTickets.release(wake.maid);
            }
        }
        for(var pair:new ArrayList<>(ORDERS.entrySet())) {
            var o=pair.getValue();var entity=o.level.getEntity(pair.getKey());
            if(!(entity instanceof EntityMaid)){if(++o.stalled>100)cancel(pair.getKey());continue;}
            o.stalled=0;
        }
    }
    private static void assign(ServerPlayer player,Session session,EntityMaid maid) {
        assign(player,session,maid,false);
    }
    private static void assign(ServerPlayer player,Session session,EntityMaid maid,boolean continuing) {
        if(ORDERS.containsKey(maid.getUUID()) || !eligible(player,maid) || !SkyfarerEquipment.elytra(maid)||!SkyfarerEquipment.spear(maid)||!SkyfarerEquipment.fireworks(maid))return;
        var controller=SkyfarerRuntime.get(maid);if(controller!=null&&controller.state().locksRunTarget())return;
        UUID selected=null;Vec3 destination=null;double best=-Double.MAX_VALUE;
        for(var id:session.marks.ids()) {
            var entity=session.level.getEntity(id);
            if(entity!=null&&(!(entity instanceof LivingEntity target)||!TargetEvaluator.attackable(maid,target)))continue;
            var snapshot=session.snapshots.get(id);if(snapshot==null)continue;
            if(!continuing&&!DispatchPolicy.admitted(maid.isHomeModeEnable(),session.marks.creative(id),maid.distanceTo(player),SkyfarerSettings.get("command_home_range")))continue;
            Vec3 pos=entity==null?DebugSnapshot.vector(snapshot,"position"):entity.position();
            double distance=maid.position().distanceTo(pos);
            double route=entity instanceof LivingEntity target&&distance<=64?TargetEvaluator.score(maid,target,null,pos)*.1:0;
            double score=DispatchPolicy.score(SkyfarerSettings.get("command_mark_bonus"),distance,
                HeadingController.horizontalAngle(maid.getDeltaMovement().lengthSqr()>.04?maid.getDeltaMovement():maid.getLookAngle(),pos.subtract(maid.position())),
                controller!=null&&id.equals(controller.previousTarget()),route);
            if(score>best){best=score;selected=id;destination=pos;}
        }
        if(selected==null)return;
        var order=new Order(player.getUUID(),session.level,session.marks.creative(selected),selected,destination,
            DispatchPolicy.transit(session.level.getEntity(selected)!=null,maid.position().distanceTo(destination)));
        ORDERS.put(maid.getUUID(),order);
        if(order.creative)CommandTickets.update(order.level,maid.getUUID(),maid.position(),order.destination);
        else CommandTickets.release(maid.getUUID()); // A discovery ticket must not become an unlimited survival ticket.
        if(SkyfarerSettings.debugLogging())dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS debug] COMMAND_ASSIGN maid={} target={} creative={} transit={} distance={}",maid.getUUID(),selected,order.creative,order.transit,maid.position().distanceTo(destination));
        if(order.transit&&controller!=null)controller.release();
        else if(session.level.getEntity(selected) instanceof LivingEntity target)SkyfarerRuntime.ensure(maid).acceptCommand(target);
    }
    private static boolean eligible(ServerPlayer owner,EntityMaid maid){return !owner.isSpectator()&&owner.getUUID().equals(maid.getOwnerUUID())&&SkyfarerRuntime.isSkyfarer(maid)&&maid.isAlive()&&!maid.isNoAi()&&!maid.isMaidInSittingPose()&&!maid.isSleeping()&&!maid.isPassenger()&&!maid.isLeashed()&&!maid.guiOpening;}
    public static boolean beforeMaidTick(EntityMaid maid) {
        var o=ORDERS.get(maid.getUUID());if(o==null)return false;
        var owner=o.level.getServer().getPlayerList().getPlayer(o.owner);var s=SESSIONS.get(o.owner);
        if(owner==null||owner.serverLevel()!=maid.level()||!eligible(owner,maid)||!SkyfarerEquipment.elytra(maid)||!SkyfarerEquipment.spear(maid)) {cancel(maid.getUUID());return false;}
        if(!o.transit&&maid.onGround()&&!SkyfarerEquipment.fireworks(maid)){cancel(maid.getUUID());return false;}
        var entity=o.level.getEntity(o.target);
        if(entity instanceof LivingEntity target&&target.isAlive())o.destination=target.position();
        boolean marked=s!=null&&s.marks.contains(o.target)&&(!(entity instanceof LivingEntity t)||t.isAlive());
        if(!marked&&!o.finishing) {
            var c=SkyfarerRuntime.get(maid);
            // Preserve an already committed pass; reassign at its end without forcing a landing.
            if(c==null||!AttackPolicy.finishCycle(c.state())) {
                o.flight.close();o.transit=false;
                if(s!=null&&!s.marks.isEmpty()) {
                    ORDERS.remove(maid.getUUID());assign(owner,s,maid,true);
                    var replacement=ORDERS.get(maid.getUUID());
                    if(replacement!=null){if(!replacement.creative)CommandTickets.release(maid.getUUID());return beforeMaidTick(maid);}
                    ORDERS.put(maid.getUUID(),o);
                }
                o.finishing=true;o.destination=maid.position();
                if(c==null||!c.controlsAir())SkyfarerRuntime.ensure(maid).commandReturn();
                // A running controller may still attack nearby unmarked hostiles, then land here.
            }
        }
        if(o.creative)CommandTickets.update(o.level,maid.getUUID(),maid.position(),o.transit?o.destination:maid.position().add(maid.getDeltaMovement().scale(24)));
        if(o.finishing && maid.onGround()) {
            // TLM SchedulePos checks recall every 40 ticks. Allow that native ground tick before
            // releasing the remote current-chunk ticket; never keep air control active here.
            if(!maid.isHomeModeEnable()||maid.isWithinRestriction()||++o.landedTicks>45){cancel(maid.getUUID());return false;}
        } else o.landedTicks=0;
        if(!o.transit)return false;
        // Adding a ticket does not promise that every entity-ticking future completed this tick.
        // Wait on the ground for initial corridor activation; never freeze an airborne entity.
        if(o.creative&&maid.onGround()&&!CommandTickets.ready(maid.getUUID())&&++o.loadingTicks<=100)return true;
        if(SkyfarerSettings.debugLogging()&&maid.tickCount%SkyfarerSettings.ticks("log_interval_ticks")==0)
            dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS debug] COMMAND_TRANSIT maid={} target={} distance={} pos={} velocity={} water={}",maid.getUUID(),o.target,maid.position().distanceTo(o.destination),maid.position(),maid.getDeltaMovement(),maid.isInWaterOrBubble());
        // The route's assigned target remains fixed until this distance, including other new marks.
        if(entity instanceof LivingEntity target&&maid.distanceTo(target)<=DispatchPolicy.HANDOFF_RANGE) {
            o.flight.close();o.transit=false;var c=SkyfarerRuntime.ensure(maid);c.acceptCommand(target);return false;
        }
        if(!o.flight.tick(maid,o.destination)) {
            o.flight.close();o.transit=false;o.finishing=true;
            SkyfarerRuntime.ensure(maid).commandReturn();
            RETRY.put(maid.getUUID(),o.level.getGameTime()+200);
            return false;
        }
        return true;
    }
    public static List<LivingEntity> targets(EntityMaid maid) {
        var o=ORDERS.get(maid.getUUID());if(o==null||o.transit||o.finishing)return List.of();
        var s=SESSIONS.get(o.owner);if(s==null)return List.of();var list=new ArrayList<LivingEntity>();
        for(var id:s.marks.ids())if(s.level.getEntity(id) instanceof LivingEntity target&&TargetEvaluator.attackable(maid,target)&&maid.distanceTo(target)<=64)list.add(target);
        return list;
    }
    public static void observe(EntityMaid maid){if(maid.level() instanceof ServerLevel level)SkyfarerMaidRegistry.get(level.getServer()).observe(maid);}
    public static void died(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if(event.getEntity().level().isClientSide())return;
        for(var s:SESSIONS.values())s.marks.remove(event.getEntity().getUUID());
    }
    public static void leave(EntityMaid maid) {
        if(maid.level() instanceof ServerLevel level){if(!maid.isAlive()||maid.getRemovalReason()==Entity.RemovalReason.DISCARDED)SkyfarerMaidRegistry.get(level.getServer()).remove(maid.getUUID());else observe(maid);}
        cancel(maid.getUUID());
    }
    private static void cancel(UUID id){var order=ORDERS.remove(id);if(order!=null){order.flight.close();if(SkyfarerSettings.debugLogging())dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS debug] COMMAND_RELEASE maid={} target={}",id,order.target);}CommandTickets.release(id);}
    public static void logout(UUID owner){SESSIONS.remove(owner);for(var entry:new ArrayList<>(ORDERS.entrySet()))if(entry.getValue().owner.equals(owner))cancel(entry.getKey());for(var w:new ArrayList<>(WAKES.values()))if(w.owner.equals(owner)){WAKES.remove(w.maid);CommandTickets.release(w.maid);}}
    public static void clear(){for(var o:ORDERS.values())o.flight.close();ORDERS.clear();WAKES.clear();SESSIONS.clear();RETRY.clear();CommandTickets.clear();}
}
