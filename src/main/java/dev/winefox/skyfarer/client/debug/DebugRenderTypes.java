package dev.winefox.skyfarer.client.debug;

import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import java.util.OptionalDouble;

/** A separate batch keeps the selected maid visible through terrain without changing other lines. */
public abstract class DebugRenderTypes extends RenderType {
    private DebugRenderTypes(){super("unused",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,false,false,()->{},()->{});}
    public static final RenderType SELECTED=create("skyfarer_selected_debug_maid",DefaultVertexFormat.POSITION_COLOR_NORMAL,VertexFormat.Mode.LINES,1536,
        CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER)
            .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()){
                @Override public void setupRenderState(){RenderSystem.lineWidth(3*Math.max(2.5F,Minecraft.getInstance().getWindow().getWidth()/1920F*2.5F));}
            }).setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
            .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).createCompositeState(false));
}
