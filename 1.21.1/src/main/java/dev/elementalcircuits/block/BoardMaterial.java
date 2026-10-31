package dev.elementalcircuits.block;
import dev.elementalcircuits.domain.Rules;
import net.minecraft.util.StringRepresentable;
public enum BoardMaterial implements StringRepresentable {
    EMPTY,WOOD,BRASS,CRYSTAL;
    @Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}
    public static BoardMaterial of(Rules.Material material){return valueOf(material.name());}
}
