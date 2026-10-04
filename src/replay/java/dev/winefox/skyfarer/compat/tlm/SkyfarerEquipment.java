package dev.winefox.skyfarer.compat.tlm;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
public class SkyfarerEquipment {public static boolean elytra(EntityMaid m){return m.equipped;}public static boolean spear(EntityMaid m){return m.weapon.spear;}public static boolean fireworks(EntityMaid m){return m.inventory.count>0;}}
