package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import dev.winefox.skyfarer.flight.RiptidePlanner;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Server-thread resource. Weak keys permit controller/Transit handoff without retaining entities.
 * Never owns LivingEntity's item-use slot. A missed tick resets rather than granting offline charge. */
public final class RiptideReadiness {
    private static final java.util.Map<EntityMaid,RiptideReadiness> STATES=new java.util.WeakHashMap<>();
    private ItemStack trident=ItemStack.EMPTY,main=ItemStack.EMPTY,chest=ItemStack.EMPTY;
    private java.lang.ref.WeakReference<Object> level=new java.lang.ref.WeakReference<>(null);
    private float strength;
    private int ticks;
    private long last=Long.MIN_VALUE;
    private RiptideReadiness(){}
    public static RiptideReadiness of(EntityMaid maid){return STATES.computeIfAbsent(maid,k->new RiptideReadiness());}
    public void tick(EntityMaid m,boolean eligible,boolean active){
        long now=m.level().getGameTime();int before=ticks;
        ItemStack selected=eligible?RiptidePropulsion.prechargeItem(m):ItemStack.EMPTY;
        var hand=m.getMainHandItem();var armor=m.getItemBySlot(EquipmentSlot.CHEST);
        float power=RiptidePropulsion.strength(m,selected);
        boolean changed=level.get()!=m.level()||selected!=trident||hand!=main||armor!=chest||power!=strength;
        if(changed||!eligible||selected.isEmpty()||now<last||now>last+1){ticks=0;}
        if(level.get()!=m.level())level=new java.lang.ref.WeakReference<>(m.level());
        trident=selected;main=hand;chest=armor;strength=power;
        if(eligible&&!selected.isEmpty()&&now!=last)ticks=Math.min(RiptidePlanner.WINDUP,ticks+1);
        last=now;
        if((before<10&&ticks==10||before>0&&ticks==0)&&dev.winefox.skyfarer.config.SkyfarerSettings.debugLogging())
            dev.winefox.skyfarer.WineFoxSkyfarer.LOGGER.info("[WFS debug] RIPTIDE_RESOURCE maid={} state={} prepared={} reason={} using={} hand={}",
                m.getUUID(),ticks==10?"RIPTIDE_READY":"PRECHARGE",ticks,changed?"EQUIPMENT_CHANGED":!eligible?"INELIGIBLE":selected.isEmpty()?"ENVIRONMENT_OR_ITEM_LOST":active?"BURST_ACTIVE":"PREPARED",m.isUsingItem(),m.getUsedItemHand());
    }
    public boolean ready(EntityMaid m,float wanted){return ticks>=RiptidePlanner.WINDUP&&level.get()==m.level()&&m.isInWaterOrRain()&&strength==wanted
        &&RiptidePropulsion.contains(m,trident)&&RiptidePropulsion.strength(m,trident)==wanted
        &&m.getMainHandItem()==main&&m.getItemBySlot(EquipmentSlot.CHEST)==chest;}
    public int ticks(){return ticks;}
    public float strength(){return strength;}
    public boolean consume(EntityMaid m,float wanted){if(!ready(m,wanted))return false;ticks=0;return true;}
    public static void clear(EntityMaid m){STATES.remove(m);}
}
