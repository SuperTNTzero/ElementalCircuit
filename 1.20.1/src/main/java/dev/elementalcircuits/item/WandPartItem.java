package dev.elementalcircuits.item;

import dev.elementalcircuits.domain.WandParts;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import java.util.List;

public final class WandPartItem extends Item {
    private final WandParts.Part part;
    public WandPartItem(WandParts.Part part){super(new Properties());this.part=part;}
    public WandParts.Part part(){return part;}
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> lines,TooltipFlag flag){
        if(part.slot()==WandParts.Slot.HEAD)lines.add(Component.literal(String.format(java.util.Locale.ROOT,"法术强度 %.2f× · 施法节奏 %.2f×",part.strength()/65536.0,part.speed()/65536.0)));
        if(part.durability()>0)lines.add(Component.literal("耐久贡献 +"+part.durability()));
        if(part.slot()==WandParts.Slot.TAIL)lines.add(Component.literal("近战伤害 "+part.melee()));
        lines.add(Component.literal("持法杖打开刻印界面，在部件页安装"));
    }
}
