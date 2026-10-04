package dev.winefox.skyfarer.runtime;

import java.util.*;

/** Ordered bindings; incoming snapshots must never steal the user's selected panel. */
public final class DebugSelection {
    private final List<UUID> ids=new ArrayList<>();
    private UUID selected;
    public void add(UUID id,boolean select){if(!ids.contains(id))ids.add(id);if(selected==null||select)selected=id;}
    public void remove(UUID id){int index=ids.indexOf(id);if(index<0)return;ids.remove(index);if(id.equals(selected))selected=ids.isEmpty()?null:ids.get(Math.min(index,ids.size()-1));}
    public void cycle(int step){if(!ids.isEmpty())selected=ids.get(Math.floorMod(ids.indexOf(selected)+step,ids.size()));}
    public UUID selected(){return selected;}
    public List<UUID> ids(){return List.copyOf(ids);}
    public void clear(){ids.clear();selected=null;}
}
