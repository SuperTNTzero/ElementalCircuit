package dev.elementalcircuits.domain;

public enum Element {
    PURE("纯净", 0xbcc9de), EARTH("土", 0xb39258), WATER("水", 0x5bafff),
    FIRE("火", 0xff7846), WIND("风", 0x9be7c5), LIGHTNING("雷", 0xe4bbff),
    LIGHT("光", 0xffec9d), SHADOW("暗", 0x9574ce), ARCANE("奥术", 0xff83c2);
    public final String title;
    public final int color;
    Element(String title, int color) { this.title = title; this.color = color; }
    public int component(boolean inverse) { return this == PURE ? 0 : 1 + (ordinal() - 1) * 2 + (inverse ? 1 : 0); }
}
