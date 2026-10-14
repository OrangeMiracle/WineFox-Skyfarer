package dev.winefox.skyfarer.runtime;
/** External reward delivery adapter; the actual SavedData ledger is separately tested on mapped APIs. */
public class CometRewards {
 public static final CometRewards INSTANCE=new CometRewards();public final java.util.Set<java.util.UUID> owners=new java.util.HashSet<>();
 public static CometRewards get(Object server){return INSTANCE;}
 public void earned(Object server,java.util.UUID owner){if(owner!=null)owners.add(owner);}
}
