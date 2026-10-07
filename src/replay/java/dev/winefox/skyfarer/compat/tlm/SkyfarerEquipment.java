package dev.winefox.skyfarer.compat.tlm;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
public class SkyfarerEquipment {
 public static boolean ensureWings(EntityMaid m){if(m.wingDurability<20&&m.spareWings>0){m.spareWings--;m.wingDurability=432;}return m.equipped&&m.wingDurability>=20;}
 public static void report(EntityMaid m,String event){m.reports.add(event);m.reportTimes.add(m.level().getGameTime());}
 public static void updateNotices(EntityMaid m,boolean ready,boolean ground){EquipmentNotices.tick(m,ready,ground);}
 public static WingInventory.Qualification wingQualification(EntityMaid m){return !m.equipped?WingInventory.Qualification.NOT_ELYTRA_EQUIVALENT:m.wingDurability<20?WingInventory.Qualification.LOW_DURABILITY:WingInventory.Qualification.READY;}
 public static boolean wing(net.minecraft.world.item.ItemStack stack){return WingInventory.recognized(stack,java.util.List.of());}
 public static void groundCombat(EntityMaid m){m.groundAttacks++;GroundSpearCombat.tick(m);}
 public static boolean elytra(EntityMaid m){return m.equipped;}public static boolean spear(EntityMaid m){return m.weapon.spear;}public static boolean fireworks(EntityMaid m){return m.inventory.count>0;}}
