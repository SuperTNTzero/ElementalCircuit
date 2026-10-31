package dev.elementalcircuits.client;

import dev.elementalcircuits.adapter.RuleResources;
import dev.elementalcircuits.client.effect.FeedbackClient;
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.server.*;
import net.minecraft.client.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Real owner-only hit confirmation; invalid damage must not flash the reticle. */
final class ImpactWorldProbe {
    private static volatile int state;
    private static int ticks;
    private static Cow target;
    private static long baseline;
    private static int confirmedFrames;
    static boolean done(){return state==3;}
    static void server(ServerLevel level){
        if(!PortWorldProbe.complete() || level.players().isEmpty() || state>=2)return;var player=level.players().get(0);
        if(state==0){player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);target=EntityType.COW.create(level);target.moveTo(player.position().add(0,1,6));target.setNoAi(true);target.setNoGravity(true);target.setInvulnerable(true);level.addFreshEntity(target);state=1;baseline=FeedbackClient.confirmations();}
        if(++ticks==8 || ticks==18){
            if(ticks==18){if(FeedbackClient.confirmations()!=baseline)throw new AssertionError("无敌目标产生了假命中确认");target.setInvulnerable(false);}
            Vec3 from=player.getEyePosition(),to=target.getBoundingBox().getCenter();float health=target.getHealth();
            SpellEffects.castOwned(level,player.getUUID(),from,to.subtract(from).normalize(),new UUID(31,16),new MagicCircuit.Cast(0,Mixture.pure(2*Fixed.ONE).convert(Element.ARCANE,Fixed.ONE),new SpellSpec(SpellSpec.Method.DIRECTED,SpellSpec.Shape.BEAM,0,12,0,0)),RuleResources.defaults(),Fixed.ONE);
            if(ticks==18){if(target.getHealth()>=health)throw new AssertionError("真实命中未产生服务器伤害");state=2;}
        }
    }
    static boolean ready(Minecraft mc){
        if(state==2 && FeedbackClient.confirmations()>baseline && ++confirmedFrames>=2){Screenshot.grab(mc.gameDirectory,"confirmed-impact.png",mc.getMainRenderTarget(),m->{});state=3;org.slf4j.LoggerFactory.getLogger("ImpactWorldProbe").info("IMPACT_FEEDBACK_WORLD_PASS: invulnerable target silent + accepted server damage + owner native payload + reticle confirmation");}
        return done();
    }
}
