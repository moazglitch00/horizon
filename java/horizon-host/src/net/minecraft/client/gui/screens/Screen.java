package net.minecraft.client.gui.screens;

import net.minecraft.client.MinecraftClient;

/**
 * Minimal compatibility shim for Minecraft's Screen.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class Screen {
    protected final MinecraftClient minecraft;
    public int width;
    public int height;
    protected boolean isPauseScreen = false;
    private net.minecraft.network.chat.Component title;

    public Screen() {
        this(MinecraftClient.getInstance(), null);
    }

    public Screen(net.minecraft.network.chat.Component title) {
        this(MinecraftClient.getInstance(), title);
    }

    public Screen(MinecraftClient minecraft) {
        this(minecraft, null);
    }

    public Screen(MinecraftClient minecraft, net.minecraft.network.chat.Component title) {
        this.minecraft = minecraft;
        this.title = title;
    }

    public net.minecraft.network.chat.Component getTitle() {
        return title;
    }

    public MinecraftClient getMinecraft() {
        return minecraft;
    }

    public void init() {
        // Override in subclasses
    }

    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        init();
    }

    public void render() {
        // Override in subclasses
    }

    public void render(int mouseX, int mouseY, float partialTick) {
        render();
    }

    public void render(Object guiGraphics, int mouseX, int mouseY, float partialTick) {
        render(mouseX, mouseY, partialTick);
    }

    public void tick() {
        // Override in subclasses
    }

    public void onClose() {
        // Override in subclasses
    }

    public boolean isPauseScreen() {
        return isPauseScreen;
    }

    public void removed() {
        // Override in subclasses
    }

    @Override
    public String toString() {
        return "Screen";
    }
}
