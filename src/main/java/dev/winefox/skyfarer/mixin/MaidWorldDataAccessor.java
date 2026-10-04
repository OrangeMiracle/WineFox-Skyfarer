package dev.winefox.skyfarer.mixin;

import com.github.tartaricacid.touhoulittlemaid.world.data.MaidWorldData;
import com.github.tartaricacid.touhoulittlemaid.world.data.MaidInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.*;

@Mixin(value=MaidWorldData.class,remap=false)
public interface MaidWorldDataAccessor {
    @Accessor("infos") Map<UUID,List<MaidInfo>> skyfarer$infos();
}
