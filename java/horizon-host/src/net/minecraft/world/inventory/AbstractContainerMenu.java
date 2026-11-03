package net.minecraft.world.inventory;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Minimal compatibility shim for Minecraft's AbstractContainerMenu.
 * Phase 2 of fabric-runtime: Minecraft Java compatibility layer.
 */
public class AbstractContainerMenu {
    public List<Slot> slots = new ArrayList<>();
    protected Container container;
    public int containerId;
    protected ItemStack carried = ItemStack.EMPTY;

    public AbstractContainerMenu() {
        this(0);
    }

    public AbstractContainerMenu(int containerId) {
        this.containerId = containerId;
    }

    public AbstractContainerMenu(int containerId, Container container) {
        this.containerId = containerId;
        this.container = container;
    }

    public static class Slot {
        public int index;
        public int slot;
        public int x;
        public int y;
        public Container container;
        private ItemStack item;

        public Slot() {
            this(null, 0, 0, 0);
        }

        public Slot(Container container, int slot, int x, int y) {
            this.container = container;
            this.slot = slot;
            this.x = x;
            this.y = y;
            this.index = slot;
            this.item = ItemStack.EMPTY;
        }

        public ItemStack getItem() {
            if (container != null) {
                try {
                    return container.getItem(slot);
                } catch (Exception ignored) {
                    // fall through
                }
            }
            return item == null ? ItemStack.EMPTY : item;
        }

        public void set(ItemStack stack) {
            this.item = stack == null ? ItemStack.EMPTY : stack;
            if (container != null) {
                try {
                    container.setItem(slot, this.item);
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }

        public boolean hasItem() {
            return !getItem().isEmpty();
        }

        public boolean mayPickup(Object player) {
            return true;
        }

        public boolean mayPlace(ItemStack stack) {
            return true;
        }

        @Override
        public String toString() {
            return "Slot[" + slot + "]";
        }
    }

    public Slot getSlot(int index) {
        if (index < 0 || index >= slots.size()) {
            return null;
        }
        return slots.get(index);
    }

    public ItemStack getCarried() {
        return carried == null ? ItemStack.EMPTY : carried;
    }

    public void setCarried(ItemStack stack) {
        this.carried = stack == null ? ItemStack.EMPTY : stack;
    }

    public void clicked(int slotId, int button, Object clickType, Object player) {
        // Override in subclasses
    }

    public void clicked(int slotId, int button, int clickType, Object player) {
        clicked(slotId, button, (Object) Integer.valueOf(clickType), player);
    }

    public void broadcastChanges() {
        // Override in subclasses
    }

    public void setData(int index, int value) {
        // Override in subclasses
    }

    public boolean clickMenuButton(Object player, int button) {
        return false;
    }

    public boolean stillValid(Object player) {
        return true;
    }

    @Override
    public String toString() {
        return "AbstractContainerMenu";
    }
}
