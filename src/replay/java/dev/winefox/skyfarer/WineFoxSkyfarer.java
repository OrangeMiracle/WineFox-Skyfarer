package dev.winefox.skyfarer;
public class WineFoxSkyfarer {
 public static class Item {public Object builtInRegistryHolder(){return this;}}
 public record Deferred(Item get) {}
 public static final Deferred COMET_HEART=new Deferred(new Item()),EVERBURNING_COMET_HEART=new Deferred(new Item());
 public static final Log LOGGER=new Log();
 public static class Log {
  public void info(String format,Object... args){for(Object arg:args)format=format.replaceFirst("\\{\\}",java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));dev.winefox.skyfarer.replay.ReplayWorld.events.add(format);}
 }
}
