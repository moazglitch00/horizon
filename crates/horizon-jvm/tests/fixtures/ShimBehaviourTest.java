import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * Exercises the shims that T-202 found to be silent fakes.
 *
 * <p>Every assertion below fails on the pre-T-214 code: {@code Block.setId}
 * discarded its argument, {@code FriendlyByteBuf.readUtf(maxLength)} ignored the
 * cap, {@code MinecraftServer.isRunning()} was hardcoded true and {@code tick()}
 * was a no-op, and {@code RecipeManager}/{@code AbstractContainerMenu} returned
 * empty values or did nothing instead of doing the work or failing loudly. The
 * Rust test {@code shim_behaviour_is_not_silent} compiles this against every
 * {@code net/minecraft} shim and requires {@code SHIM-OK} on stdout.
 */
public final class ShimBehaviourTest {
    static final class TestRecipe implements Recipe {
        private final ItemStack result;

        TestRecipe(ItemStack result) {
            this.result = result;
        }

        @Override
        public ItemStack getResultItem() {
            return result;
        }

        @Override
        public boolean matches(Container container, Level level) {
            return container != null
                    && container.getContainerSize() == 1
                    && !container.getItem(0).isEmpty();
        }

        @Override
        public boolean canCraftInDimensions(int width, int height) {
            return true;
        }
    }

    static final class TestContainer implements Container {
        private final ItemStack[] items;

        TestContainer(ItemStack... items) {
            this.items = items;
        }

        @Override
        public int getContainerSize() {
            return items.length;
        }

        @Override
        public ItemStack getItem(int slot) {
            return items[slot];
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items[slot] = stack;
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        // 1. Block.setId must persist its argument.
        Block block = new Block(Block.Properties.of());
        Identifier stone = Identifier.parse("minecraft:stone");
        block.setId(stone);
        expect(stone.equals(block.getId()), "Block.setId must persist its id");

        // 2. FriendlyByteBuf.readUtf(maxLength) must enforce the cap, and a fresh
        //    buffer must expose no readable bytes until something is written.
        FriendlyByteBuf empty = new FriendlyByteBuf(64);
        expect(empty.readableBytes() == 0, "a fresh buffer must have nothing to read");
        FriendlyByteBuf buf = new FriendlyByteBuf();
        buf.writeUtf("hello");
        expect("hello".equals(buf.readUtf(5)), "readUtf must accept a string within the cap");
        FriendlyByteBuf longBuf = new FriendlyByteBuf();
        longBuf.writeUtf("toolong");
        boolean threw = false;
        try {
            longBuf.readUtf(3);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        expect(threw, "readUtf must reject a string over the cap");

        // 3. MinecraftServer: state-backed isRunning, loud tick.
        MinecraftServer server = MinecraftServer.getInstance();
        expect(!server.isRunning(), "a shim server must not claim to be running");
        server.setRunning(true);
        expect(server.isRunning(), "setRunning(true) must be observable");
        server.setRunning(false);
        threw = false;
        try {
            server.tick();
        } catch (UnsupportedOperationException e) {
            threw = e.getMessage().startsWith("server-tick");
        }
        expect(threw, "MinecraftServer.tick must fail loudly with a phase name");

        // 4. RecipeManager is a real store.
        RecipeManager recipes = new RecipeManager();
        TestRecipe recipe = new TestRecipe(new ItemStack(new Item(null), 1));
        recipes.addRecipe(Identifier.parse("test:one"), recipe);
        TestContainer container = new TestContainer(new ItemStack(new Item(null), 1));
        expect(
                recipes.getRecipeFor(null, container, null).orElse(null) == recipe,
                "getRecipeFor must find a stored matching recipe");
        expect(recipes.byType(null).size() == 1, "byType(null) must return the store");
        threw = false;
        try {
            recipes.byType("smelting");
        } catch (UnsupportedOperationException e) {
            threw = e.getMessage().startsWith("recipe-by-type");
        }
        expect(threw, "byType(type) must fail loudly while it has no type registry");

        // 5. AbstractContainerMenu.clicked must fail loudly instead of silently
        //    doing nothing.
        AbstractContainerMenu menu = new AbstractContainerMenu();
        threw = false;
        try {
            menu.clicked(0, 0, null, null);
        } catch (UnsupportedOperationException e) {
            threw = e.getMessage().startsWith("container-click");
        }
        expect(threw, "AbstractContainerMenu.clicked must fail loudly");

        // 6. A BlockEntityType without a factory must not return null.
        BlockEntityType<BlockEntity> noFactory = new BlockEntityType<>(null);
        threw = false;
        try {
            noFactory.create();
        } catch (UnsupportedOperationException e) {
            threw = e.getMessage().startsWith("block-entity-type.factory");
        }
        expect(threw, "BlockEntityType.create must fail loudly without a factory");

        System.out.println("SHIM-OK");
    }
}
