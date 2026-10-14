package net.minecraft.server.level;
public class ServerLevel extends dev.winefox.skyfarer.replay.ReplayWorld {
 public boolean thunder;
 public boolean isThundering(){return thunder;}
 public final Server server=new Server();public Server getServer(){return server;}
 public static class Server {
  public boolean sameThread=true;public boolean isSameThread(){return sameThread;}
  public final Profiles profiles=new Profiles();public Profiles getProfileCache(){return profiles;}
 }
 public static class Profiles {public final java.util.Set<java.util.UUID> known=new java.util.HashSet<>();public java.util.Optional<java.util.UUID> get(java.util.UUID id){return known.contains(id)?java.util.Optional.of(id):java.util.Optional.empty();}}
}
