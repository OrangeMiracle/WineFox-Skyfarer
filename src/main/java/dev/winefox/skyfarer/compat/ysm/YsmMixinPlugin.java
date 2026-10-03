package dev.winefox.skyfarer.compat.ysm;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import java.util.List;
import java.util.Set;

/** YSM 2.6.5 has obfuscated client APIs. Never load its classes when absent or incompatible. */
public final class YsmMixinPlugin implements IMixinConfigPlugin {
    private boolean compatible;
    @Override public void onLoad(String pkg) {
        try {
            // Do not infer compatibility with a different obfuscated YSM release.
            var loading=net.neoforged.fml.loading.LoadingModList.get();
            if(loading==null || loading.getMods().stream().noneMatch(m->m.getModId().equals("yes_steve_model")
                && m.getVersion().toString().equals("2.6.5-neoforge+mc1.21.1"))) return;
            var node=MixinService.getService().getBytecodeProvider().getClassNode("com.elfmcys.yesstevemodel.oOoO0oOo0OoooOOoO0oO0O00");
            compatible=node.methods.stream().anyMatch(m->m.desc.equals("(Lcom/elfmcys/yesstevemodel/Oo0Oo0O0OoOoO0oooO00O0o0;Lcom/elfmcys/yesstevemodel/O00o00OOOo00OOOo0O0oOooo;)Lcom/elfmcys/yesstevemodel/O0o0ooO0oOoOoo0O000O0000;"));
        } catch(Exception | LinkageError missing) { compatible=false; }
    }
    @Override public boolean shouldApplyMixin(String target,String mixin) { return compatible; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> own,Set<String> other) {}
    @Override public void preApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
    @Override public void postApply(String name,ClassNode node,String mixin,IMixinInfo info) {}
}
