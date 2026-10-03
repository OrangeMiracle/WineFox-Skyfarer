package dev.winefox.skyfarer.client.gui;

import dev.winefox.skyfarer.network.OpenRemoteTaskPayload;
import dev.winefox.skyfarer.network.SetRemoteMaidTaskPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RemoteMaidTaskScreen extends Screen {
    private OpenRemoteTaskPayload data;
    private int page;
    private int pageSize;
    public RemoteMaidTaskScreen(OpenRemoteTaskPayload data) { super(Component.translatable("screen.winefox_skyfarer.remote")); this.data = data; }
    public java.util.UUID session() { return data.session(); }
    public void update(OpenRemoteTaskPayload data) { this.data = data; rebuildWidgets(); }
    private static Component taskName(ResourceLocation id) { return Component.translatable("task." + id.getNamespace() + "." + id.getPath()); }
    @Override protected void init() {
        pageSize = Math.max(1, (height - 135) / 24);
        page = Math.min(page, Math.max(0, (data.tasks().size() - 1) / pageSize));
        int x = width / 2 - 145;
        for (int i = page * pageSize; i < Math.min(data.tasks().size(), (page + 1) * pageSize); i++) {
            var option = data.tasks().get(i);
            var label = Component.literal(option.id().equals(data.current()) ? "✓ " : "").append(taskName(option.id()));
            Button button = Button.builder(label, pressed -> {
                pressed.active = false;
                PacketDistributor.sendToServer(new SetRemoteMaidTaskPayload(data.session(), option.id()));
            }).bounds(x, 78 + (i % pageSize) * 24, 290, 20).build();
            button.active = option.enabled(); addRenderableWidget(button);
        }
        Button previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); }).bounds(x, height - 42, 45, 20).build());
        previous.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); }).bounds(x + 245, height - 42, 45, 20).build());
        next.active = (page + 1) * pageSize < data.tasks().size();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width / 2 - 65, height - 42, 130, 20).build());
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.winefox_skyfarer.bound", data.name()), width / 2, 34, 0xAADDFF);
        graphics.drawCenteredString(font, Component.translatable("screen.winefox_skyfarer.current", taskName(data.current())), width / 2, 49, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.winefox_skyfarer.disabled"), width / 2, 63, 0x999999);
    }
    @Override public boolean isPauseScreen() { return false; }
}
