package dev.elementalcircuits.server.ecology;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.config.ServerSettings;
import dev.elementalcircuits.domain.mana.ManaField;
import dev.elementalcircuits.entity.ManaSlime;
import dev.elementalcircuits.server.mana.WorldManaField;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.registries.*;

/** Vanilla spawn scheduling and a persistent, sampled exposure counter, without chunk scans. */
public final class ManaEcology {
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ElementalCircuits.ID);
    public static final DeferredHolder<EntityType<?>, EntityType<ManaSlime>> SLIME =
            TYPES.register(
                    "mana_slime",
                    () ->
                            EntityType.Builder.of(ManaSlime::new, MobCategory.MONSTER)
                                    .sized(.52f, .52f)
                                    .clientTrackingRange(10)
                                    .build("elementalcircuits:mana_slime"));
    public static final DeferredHolder<Item, Item> SPAWN_EGG =
            ElementalCircuits.ITEMS.register(
                    "mana_slime_spawn_egg",
                    () ->
                            new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                                    SLIME, 0x397d9b, 0xa5edf4, new Item.Properties()));
    private static final String EXPOSURE = "elementalcircuits_mana_exposure";

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(
                (EntityAttributeCreationEvent event) ->
                        event.put(
                                SLIME.get(),
                                Mob.createMobAttributes()
                                        .add(
                                                net.minecraft.world.entity.ai.attributes.Attributes
                                                        .ATTACK_DAMAGE,
                                                1)
                                        .build()));
        bus.addListener(
                (RegisterSpawnPlacementsEvent event) ->
                        event.register(
                                SLIME.get(),
                                SpawnPlacementTypes.ON_GROUND,
                                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                (type, level, reason, pos, random) ->
                                        level.getDifficulty() != Difficulty.PEACEFUL
                                                && canSpawn(level.getLevel(), pos)
                                                && Mob.checkMobSpawnRules(
                                                        type, level, reason, pos, random),
                                RegisterSpawnPlacementsEvent.Operation.REPLACE));
        NeoForge.EVENT_BUS.addListener(ManaEcology::tick);
    }

    public static boolean canSpawn(ServerLevel level, BlockPos pos) {
        return ServerSettings.count("ecology.enabled") != 0
                && ServerSettings.count("field.enabled") != 0
                && WorldManaField.get(level)
                        .naturalNodeNear(pos, ServerSettings.count("ecology.nodeRadius"));
    }

    public static void tick(EntityTickEvent.Post event) {
        if (event.getEntity().getType() != EntityType.SLIME
                || !(event.getEntity() instanceof Slime slime)
                || !(slime.level() instanceof ServerLevel level)
                || slime.tickCount % 20 != 0
                || !slime.isAlive()) return;
        expose(slime, level);
    }

    public static ManaSlime expose(Slime slime, ServerLevel level) {
        if (slime.getType() != EntityType.SLIME
                || !slime.isAlive()
                || ServerSettings.count("ecology.enabled") == 0
                || ServerSettings.count("field.enabled") == 0) return null;
        var field = WorldManaField.get(level);
        var cell =
                ManaField.Cell.at(
                        slime.blockPosition().getX(),
                        slime.blockPosition().getY(),
                        slime.blockPosition().getZ());
        boolean strong =
                field.valid()
                        && field.field().amount(cell)
                                >= ServerSettings.mana("ecology.conversionField");
        var data = slime.getPersistentData();
        int threshold = ServerSettings.count("ecology.conversionTicks");
        int exposure = strong ? Math.min(threshold, Math.max(0, data.getInt(EXPOSURE)) + 20) : 0;
        if (exposure == 0) data.remove(EXPOSURE);
        else data.putInt(EXPOSURE, exposure);
        if (exposure < threshold) return null;
        int size = slime.getSize();
        float health = slime.getHealth();
        var converted = slime.convertTo(SLIME.get(), true);
        if (converted != null) {
            converted.setSize(size, true);
            converted.setHealth(Math.min(health, converted.getMaxHealth()));
            converted.getPersistentData().remove(EXPOSURE);
            level.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.END_ROD,
                    converted.getX(),
                    converted.getY() + .4,
                    converted.getZ(),
                    12,
                    .35,
                    .35,
                    .35,
                    .02);
        }
        return converted;
    }

    private ManaEcology() {}
}
