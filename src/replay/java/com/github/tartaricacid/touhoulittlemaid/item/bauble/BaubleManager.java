package com.github.tartaricacid.touhoulittlemaid.item.bauble;
public class BaubleManager {
 public final java.util.Map<Object,com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble> bound=new java.util.HashMap<>();
 public void bind(Object item,com.github.tartaricacid.touhoulittlemaid.api.bauble.IMaidBauble bauble){bound.put(item,bauble);}
}
