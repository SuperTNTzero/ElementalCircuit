package dev.elementalcircuits.item;
import dev.elementalcircuits.server.BookService;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class BlueprintNotebookItem extends Item {
    public BlueprintNotebookItem(Properties p){super(p);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player p,InteractionHand hand){if(!level.isClientSide && p instanceof ServerPlayer server)BookService.openNotebook(server,p.getItemInHand(hand));return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),level.isClientSide);}
    @Override public void appendHoverText(ItemStack s,Level l,List<Component> text,TooltipFlag f){text.add(Component.literal("右键：保存、预览与分享蓝图；最多16页"));text.add(Component.literal("仅保存设计，不保存魔力或运行现场"));}
}
