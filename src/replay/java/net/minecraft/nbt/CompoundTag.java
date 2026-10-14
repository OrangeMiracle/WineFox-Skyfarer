package net.minecraft.nbt;
public class CompoundTag {
 public void remove(String key){values.remove(key);}
 public boolean getBoolean(String k){return (Boolean)values.getOrDefault(k,false);}
 public CompoundTag copy(){var result=new CompoundTag();result.values.putAll(values);return result;}
 public boolean contains(String k){return values.containsKey(k);}public int getInt(String k){return ((Number)values.getOrDefault(k,0)).intValue();}public long getLong(String k){return ((Number)values.getOrDefault(k,0)).longValue();}
 private final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public void putString(String k,String v){values.put(k,v);} public void putDouble(String k,double v){values.put(k,v);}public void putInt(String k,int v){values.put(k,v);}
 public void putLong(String k,long v){values.put(k,v);}public void putUUID(String k,java.util.UUID v){values.put(k,v);}public void putBoolean(String k,boolean v){values.put(k,v);}
 public void put(String k,Object v){values.put(k,v);}public String getString(String k){return (String)values.getOrDefault(k,"");}
 public double getDouble(String k){return ((Number)values.getOrDefault(k,0)).doubleValue();}public CompoundTag getCompound(String k){return (CompoundTag)values.getOrDefault(k,new CompoundTag());}
}
