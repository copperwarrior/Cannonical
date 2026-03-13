package org.shipwrights.cannonical.integration.krakk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.shipwrights.cannonical.Cannonical;
import org.shipwrights.krakk.engine.damage.KrakkDamageCurves;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CannonicalDamageBlockConversions {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String RULE_DIRECTORY = "krakk_damage_block_conversions";
    private static final ResourceLocation RELOAD_LISTENER_ID = new ResourceLocation(Cannonical.MOD_ID, RULE_DIRECTORY);
    private static final RuleReloadListener RELOAD_LISTENER = new RuleReloadListener();
    private static volatile Map<Block, List<DamageConversionRule>> rulesBySource = Map.of();
    private static boolean initialized;

    private CannonicalDamageBlockConversions() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        ReloadListenerRegistry.register(PackType.SERVER_DATA, RELOAD_LISTENER, RELOAD_LISTENER_ID);
    }

    public static boolean applyConversionForDamageState(ServerLevel level, BlockPos blockPos, BlockState blockState, int damageState) {
        if (damageState <= 0) {
            return false;
        }

        List<DamageConversionRule> rules = rulesBySource.get(blockState.getBlock());
        if (rules == null || rules.isEmpty()) {
            return false;
        }

        for (DamageConversionRule rule : rules) {
            if (damageState < rule.minDamageState()) {
                continue;
            }

            BlockState targetState = copyCompatibleProperties(blockState, rule.target().defaultBlockState());
            if (targetState.getBlock() == blockState.getBlock()) {
                return false;
            }
            return level.setBlock(blockPos, targetState, 3);
        }

        return false;
    }

    private static BlockState copyCompatibleProperties(BlockState sourceState, BlockState targetState) {
        BlockState convertedState = targetState;
        for (Property<?> sourceProperty : sourceState.getProperties()) {
            Property<?> targetProperty = targetState.getBlock().getStateDefinition().getProperty(sourceProperty.getName());
            if (targetProperty == null) {
                continue;
            }

            String serializedValue = getSerializedPropertyValue(sourceState, sourceProperty);
            convertedState = applySerializedPropertyValue(convertedState, targetProperty, serializedValue);
        }
        return convertedState;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String getSerializedPropertyValue(BlockState sourceState, Property<?> sourceProperty) {
        Property rawSourceProperty = sourceProperty;
        Comparable rawValue = (Comparable) sourceState.getValue(rawSourceProperty);
        return rawSourceProperty.getName(rawValue);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applySerializedPropertyValue(BlockState state, Property<?> targetProperty, String serializedValue) {
        Property rawTargetProperty = targetProperty;
        Optional<?> parsedValue = rawTargetProperty.getValue(serializedValue);
        if (parsedValue.isEmpty()) {
            return state;
        }
        return state.setValue(rawTargetProperty, (Comparable) parsedValue.get());
    }

    private static int clampDamageState(int value) {
        return Math.max(1, Math.min(KrakkDamageCurves.MAX_DAMAGE_STATE, value));
    }

    private static ResourceLocation parseBlockId(JsonObject json, String primaryKey, String fallbackKey) {
        String key = json.has(primaryKey) ? primaryKey : fallbackKey;
        if (key == null || !json.has(key)) {
            throw new IllegalArgumentException("missing '" + primaryKey + "' field");
        }

        String rawId = GsonHelper.getAsString(json, key);
        ResourceLocation parsed = ResourceLocation.tryParse(rawId);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid block id '" + rawId + "'");
        }
        return parsed;
    }

    private static int parseThreshold(JsonObject json) {
        if (json.has("damage_state")) {
            return clampDamageState(GsonHelper.getAsInt(json, "damage_state"));
        }

        if (json.has("damage_fraction")) {
            double fraction = GsonHelper.getAsDouble(json, "damage_fraction");
            if (!Double.isFinite(fraction) || fraction <= 0.0D) {
                throw new IllegalArgumentException("'damage_fraction' must be a finite value > 0");
            }
            int threshold = (int) Math.ceil(fraction * KrakkDamageCurves.MAX_DAMAGE_STATE);
            return clampDamageState(threshold);
        }

        throw new IllegalArgumentException("missing 'damage_state' or 'damage_fraction' field");
    }

    private record DamageConversionRule(int minDamageState, Block target) {
    }

    private static final class RuleReloadListener extends SimpleJsonResourceReloadListener {
        private RuleReloadListener() {
            super(GSON, RULE_DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
            Map<Block, List<DamageConversionRule>> loadedRules = new HashMap<>();

            for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
                ResourceLocation fileId = entry.getKey();
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "damage conversion");
                    ResourceLocation sourceId = parseBlockId(json, "source", "from");
                    ResourceLocation targetId = parseBlockId(json, "target", "to");

                    Block sourceBlock = BuiltInRegistries.BLOCK.getOptional(sourceId)
                            .orElseThrow(() -> new IllegalArgumentException("unknown source block '" + sourceId + "'"));
                    Block targetBlock = BuiltInRegistries.BLOCK.getOptional(targetId)
                            .orElseThrow(() -> new IllegalArgumentException("unknown target block '" + targetId + "'"));
                    if (sourceBlock == targetBlock) {
                        continue;
                    }

                    int threshold = parseThreshold(json);
                    loadedRules.computeIfAbsent(sourceBlock, key -> new ArrayList<>())
                            .add(new DamageConversionRule(threshold, targetBlock));
                } catch (Exception ex) {
                    LOGGER.warn("Skipped damage conversion rule {}: {}", fileId, ex.getMessage());
                }
            }

            Map<Block, List<DamageConversionRule>> immutableRules = new HashMap<>();
            for (Map.Entry<Block, List<DamageConversionRule>> entry : loadedRules.entrySet()) {
                List<DamageConversionRule> sortedRules = new ArrayList<>(entry.getValue());
                sortedRules.sort(Comparator.comparingInt(DamageConversionRule::minDamageState).reversed());
                immutableRules.put(entry.getKey(), List.copyOf(sortedRules));
            }
            rulesBySource = Map.copyOf(immutableRules);
        }
    }
}
