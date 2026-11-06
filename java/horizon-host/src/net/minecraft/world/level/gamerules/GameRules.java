package net.minecraft.world.level.gamerules;

import java.util.HashMap;
import java.util.Map;

/**
 * Compatibility shim for Minecraft's GameRules.
 */
public class GameRules {
    private final Map<String, GameRule<?>> rules = new HashMap<>();

    public static GameRules create() {
        GameRules rules = new GameRules();
        rules.addRule("doDaylightCycle", new GameRule<>(true));
        rules.addRule("doMobSpawning", new GameRule<>(true));
        rules.addRule("doMobLoot", new GameRule<>(true));
        rules.addRule("doTileDrops", new GameRule<>(true));
        rules.addRule("commandBlockOutput", new GameRule<>(true));
        rules.addRule("doImmediateRespawn", new GameRule<>(false));
        rules.addRule("doFireTick", new GameRule<>(true));
        rules.addRule("doInsomnia", new GameRule<>(true));
        rules.addRule("doWeatherCycle", new GameRule<>(true));
        rules.addRule("naturalRegeneration", new GameRule<>(true));
        return rules;
    }

    private void addRule(String name, GameRule<?> rule) {
        rules.put(name, rule);
    }

    public <T> boolean hasRule(String name) {
        return rules.containsKey(name);
    }

    public <T> T get(GameRule<T> rule) {
        GameRule<?> r = rules.get(rule.name);
        return r != null ? (T) r.value : null;
    }

    public boolean getBoolean(String name) {
        GameRule<?> r = rules.get(name);
        return r != null && Boolean.TRUE.equals(r.value);
    }

    public int getInt(String name) {
        GameRule<?> r = rules.get(name);
        return r != null ? ((Number) r.value).intValue() : 0;
    }

    public static class GameRule<T> {
        public final String name;
        public final T value;
        public final GameRuleCodec<T> codec;

        public GameRule(T value) {
            this.name = null;
            this.value = value;
            this.codec = null;
        }

        public GameRule(String name, T value, GameRuleCodec<T> codec) {
            this.name = name;
            this.value = value;
            this.codec = codec;
        }
    }

    public interface GameRuleCodec<T> {
        T decode(T value);
    }
}
