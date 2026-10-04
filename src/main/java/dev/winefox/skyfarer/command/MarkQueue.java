package dev.winefox.skyfarer.command;

import java.util.*;

/** Per-player ordered toggle list. Order supplies labels, never assignment priority. */
public final class MarkQueue {
    private final LinkedHashMap<UUID,Boolean> entries=new LinkedHashMap<>();
    public boolean toggle(UUID id,boolean creative) {
        if(entries.remove(id)!=null)return false;
        if(entries.size()==5)entries.remove(entries.keySet().iterator().next());
        entries.put(id,creative);return true;
    }
    public List<UUID> ids(){return List.copyOf(entries.keySet());}
    public boolean contains(UUID id){return entries.containsKey(id);}
    public boolean creative(UUID id){return Boolean.TRUE.equals(entries.get(id));}
    public void remove(UUID id){entries.remove(id);}
    public boolean isEmpty(){return entries.isEmpty();}
}
