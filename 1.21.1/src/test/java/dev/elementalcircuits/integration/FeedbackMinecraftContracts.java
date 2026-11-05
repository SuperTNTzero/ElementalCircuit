package dev.elementalcircuits.integration;

import dev.elementalcircuits.ElementalCircuits;
import dev.elementalcircuits.adapter.resources.RuleResources;
import dev.elementalcircuits.application.observation.*;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.domain.casting.SpellSpec;
import dev.elementalcircuits.domain.circuit.MagicCircuit;
import dev.elementalcircuits.domain.combat.Element;
import dev.elementalcircuits.domain.mana.Mixture;
import dev.elementalcircuits.network.FeedbackNetwork;
import dev.elementalcircuits.server.*;
import dev.elementalcircuits.server.circuit.CarrierService;
import dev.elementalcircuits.server.spell.SpellEffects;
import dev.elementalcircuits.server.spell.SpellFeedback;
import dev.elementalcircuits.server.spell.SpellVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

@GameTestHolder(ElementalCircuits.ID)
@PrefixGameTestTemplate(false)
public final class FeedbackMinecraftContracts {
    @GameTest(template = "empty")
    public static void feedbackOnlyConfirmsSuccessfulDamage(GameTestHelper h) {
        var level = h.getLevel();
        var player =
                net.neoforged.neoforge.common.util.FakePlayerFactory.get(
                        level,
                        new com.mojang.authlib.GameProfile(UUID.randomUUID(), "feedback-test"));
        level.addNewPlayer(player);
        try {
            var wand = new net.minecraft.world.item.ItemStack(ElementalCircuits.WAND.get());
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, wand);
            var carrier = CarrierService.identity(wand);
            var data = wand.save(level.registryAccess());
            h.assertTrue(
                    SpellFeedback.handCast(level, player.getUUID(), carrier)
                            && !SpellFeedback.handCast(level, player.getUUID(), UUID.randomUUID())
                            && data.equals(wand.save(level.registryAccess())),
                    "只有当前手持载体产生后坐，远程设备不晃动镜头，观察不改物品");
            var target = h.spawn(EntityType.COW, new BlockPos(3, 2, 5));
            target.setNoAi(true);
            target.setNoGravity(true);
            var origin = h.absoluteVec(new Vec3(3.5, 2.7, 1.5));
            var mana = Mixture.pure(Fixed.ONE).convert(Element.ARCANE, Fixed.ONE);
            var cast =
                    new MagicCircuit.Cast(
                            0,
                            mana,
                            new SpellSpec(
                                    SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 0, 8, 0, 0));
            SpellFeedback.unload(level);
            target.setInvulnerable(true);
            SpellEffects.castOwned(
                    level,
                    player.getUUID(),
                    origin,
                    new Vec3(0, 0, 1),
                    carrier,
                    cast,
                    RuleResources.defaults(),
                    Fixed.ONE);
            h.assertTrue(
                    SpellFeedback.pending(level, player.getUUID()).cast()
                            && !SpellFeedback.pending(level, player.getUUID()).hit(),
                    "无敌目标不产生成功命中确认");
            SpellFeedback.unload(level);
            target.setInvulnerable(false);
            float health = target.getHealth();
            SpellEffects.castOwned(
                    level,
                    player.getUUID(),
                    origin,
                    new Vec3(0, 0, 1),
                    carrier,
                    cast,
                    RuleResources.defaults(),
                    Fixed.ONE);
            var pulse = SpellFeedback.pending(level, player.getUUID());
            h.assertTrue(
                    target.getHealth() < health && pulse.cast() && pulse.hit(), "原生伤害成功后合并释放和命中反馈");
            for (int i = 0; i < 100; i++)
                SpellFeedback.observe(level, player.getUUID(), false, true, Element.FIRE, 1);
            h.assertTrue(
                    SpellFeedback.pending(level, player.getUUID()).hit()
                            && SpellFeedback.pending(level, UUID.randomUUID()) == null,
                    "同一施法者多次命中合并且不误发给其他玩家");
            SpellFeedback.unload(level);
            h.assertTrue(SpellFeedback.pending(level, player.getUUID()) == null, "世界卸载释放全部反馈");
        } finally {
            SpellFeedback.unload(level);
            level.removePlayerImmediately(
                    player, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void castAndBlockImpactHaveSeparateShapes(GameTestHelper h) {
        var level = h.getLevel();
        SpellVisuals.unload(level);
        h.setBlock(new BlockPos(2, 3, 5), net.minecraft.world.level.block.Blocks.STONE);
        Vec3 origin = h.absoluteVec(new Vec3(2.5, 3.5, 1.5));
        var cast =
                new MagicCircuit.Cast(
                        0,
                        Mixture.pure(Fixed.ONE).convert(Element.FIRE, Fixed.ONE),
                        new SpellSpec(SpellSpec.Method.DIRECTED, SpellSpec.Shape.BEAM, 0, 8, 0, 0));
        SpellEffects.castOwned(
                level,
                null,
                origin,
                new Vec3(0, 0, 1),
                UUID.randomUUID(),
                cast,
                RuleResources.defaults(),
                Fixed.ONE);
        var q = SpellVisuals.pending(level);
        h.assertTrue(
                q.stream().anyMatch(e -> e.kind() == SpellVisual.Kind.CAST)
                        && q.stream().anyMatch(e -> e.kind() == SpellVisual.Kind.BEAM)
                        && q.stream().anyMatch(e -> e.kind() == SpellVisual.Kind.IMPACT),
                "无生物的墙面也有实际发射、裁切光束与接触爆发三阶段");
        SpellVisuals.unload(level);
        boolean rejected = false;
        try {
            new FeedbackNetwork.Pulse(false, false, Element.FIRE, 1);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        h.assertTrue(rejected, "无阶段的空反馈拒绝");
        h.succeed();
    }
}
