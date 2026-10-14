package dev.winefox.skyfarer.compat.tlm;

import com.github.tartaricacid.touhoulittlemaid.api.ILittleMaid;
import com.github.tartaricacid.touhoulittlemaid.api.LittleMaidExtension;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import dev.winefox.skyfarer.WineFoxSkyfarer;

@LittleMaidExtension
public final class SkyfarerExtension implements ILittleMaid {
    @Override public void bindMaidBauble(com.github.tartaricacid.touhoulittlemaid.item.bauble.BaubleManager manager){CometBaubles.bind(manager);}
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    @Override public void addHardcodeAnimation(com.github.tartaricacid.touhoulittlemaid.client.animation.HardcodedAnimationManger manager) {
        manager.addMaidAnimation(new dev.winefox.skyfarer.client.SkyfarerAnimation());
    }
    @Override public void addMaidTask(TaskManager manager) {
        manager.add(new SkyfarerTask());
        WineFoxSkyfarer.LOGGER.info("Registered Skyfarer through TLM ILittleMaid extension API");
    }
}
