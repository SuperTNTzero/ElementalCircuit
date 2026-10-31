package dev.elementalcircuits.domain;

/** Frozen into each blueprint and runtime. Changing the resource affects new carriers only. */
public record Rules(int version, Material material, long conversion, long inversion, long coupling,
                    long nodeCapacity, long injection, int interval, int window, long signalCost,
                    long playerCapacity, long playerRegen, int playerDelay, long range, long radius,
                    long routingRetention, long oppositionRetention) {
    public enum Material { WOOD, BRASS, CRYSTAL }
    public Rules {
        if (version != 1 || material == null) throw new IllegalArgumentException("未知规则版本");
        for (long rate : new long[]{conversion, inversion, coupling, routingRetention, oppositionRetention})
            if (rate <= 0 || rate > Fixed.ONE) throw new IllegalArgumentException("非法保留率");
        for (long amount : new long[]{nodeCapacity, injection, playerCapacity, playerRegen}) Fixed.checked(amount);
        if (nodeCapacity == 0 || injection == 0 || injection > nodeCapacity || playerCapacity == 0 || interval < 1 || interval > 1200
            || window < 1 || window > 1200 || playerDelay < 0 || playerDelay > 12000 || signalCost < 0 || signalCost > Fixed.ONE
            || range < 1 || range > 64 || radius < 1 || radius > 8) throw new IllegalArgumentException("非法规则数值");
    }
    public int boardRadius() { return switch (material) { case WOOD -> 4; case BRASS -> 6; case CRYSTAL -> 8; }; }
    public long retention() { return switch (material) { case WOOD -> 62259; case BRASS -> 64880; case CRYSTAL -> 65405; }; }
    public Rules withMaterial(Material m) { return new Rules(version,m,conversion,inversion,coupling,nodeCapacity,injection,interval,window,signalCost,playerCapacity,playerRegen,playerDelay,range,radius,routingRetention,oppositionRetention); }
}
