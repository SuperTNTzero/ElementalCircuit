package dev.elementalcircuits.domain.combat;

public enum Element {
    PURE(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.001"),
            0xbcc9de),
    EARTH(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.002"),
            0xb39258),
    WATER(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.003"),
            0x5bafff),
    FIRE(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.004"),
            0xff7846),
    WIND(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.005"),
            0x9be7c5),
    LIGHTNING(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.006"),
            0xe4bbff),
    LIGHT(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.007"),
            0xffec9d),
    SHADOW(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.008"),
            0x9574ce),
    ARCANE(
            dev.elementalcircuits.text.Texts.get(
                    "text.elementalcircuits.domain.combat._element.009"),
            0xff83c2);
    public final String title;
    public final int color;

    Element(String title, int color) {
        this.title = title;
        this.color = color;
    }

    public int component(boolean inverse) {
        return this == PURE ? 0 : 1 + (ordinal() - 1) * 2 + (inverse ? 1 : 0);
    }
}
