package dev.elementalcircuits.domain;

/** Independent modifier dimensions; zero/UNSET means not yet specified. */
public record SpellSpec(Method method, Shape shape, long amount, int range, int radius, int aim, ProjectileEffects effects) {
    public SpellSpec(Method method,Shape shape,long amount,int range,int radius,int aim){this(method,shape,amount,range,radius,aim,ProjectileEffects.NONE);}
    public enum Method { UNSET, AREA, DIRECTED }
    public enum Shape { UNSET, SPHERE, BEAM, SHIELD, FAN, WALL, RING, COLUMN, CROSS, DOME }
    public static final SpellSpec EMPTY = new SpellSpec(Method.UNSET, Shape.UNSET, 0, 0, 0, -1);
    public SpellSpec {
        if (effects==null || method == null || shape == null || amount < 0 || amount > 64 * Fixed.ONE || range < 0 || range > 64
            || radius < 0 || radius > 8 || aim < -1 || aim > Fixed.ONE) throw new IllegalArgumentException("非法法术参数");
    }
    private static long combine(long a, long b, long unset) {
        if (a == unset) return b;
        if (b == unset || a == b) return a;
        throw new IllegalArgumentException("修饰维度冲突");
    }
    public SpellSpec merge(SpellSpec b) {
        return new SpellSpec(Method.values()[(int)combine(method.ordinal(),b.method.ordinal(),0)],
            Shape.values()[(int)combine(shape.ordinal(),b.shape.ordinal(),0)], combine(amount,b.amount,0),
            (int)combine(range,b.range,0),(int)combine(radius,b.radius,0),(int)combine(aim,b.aim,-1),effects.merge(b.effects));
    }
    public SpellSpec modify(Rune rune) {
        SpellSpec m = switch (rune.kind()) {
            case AREA -> new SpellSpec(Method.AREA,Shape.UNSET,0,0,0,-1);
            case DIRECTED -> new SpellSpec(Method.DIRECTED,Shape.UNSET,0,0,0,-1);
            case SPHERE -> new SpellSpec(Method.UNSET,Shape.SPHERE,0,0,0,-1);
            case BEAM -> new SpellSpec(Method.UNSET,Shape.BEAM,0,0,0,-1);
            case SHIELD -> new SpellSpec(Method.UNSET,Shape.SHIELD,0,0,0,-1);
            case FAN -> new SpellSpec(Method.UNSET,Shape.FAN,0,0,0,-1);
            case WALL -> new SpellSpec(Method.UNSET,Shape.WALL,0,0,0,-1);
            case RING -> new SpellSpec(Method.UNSET,Shape.RING,0,0,0,-1);
            case COLUMN -> new SpellSpec(Method.UNSET,Shape.COLUMN,0,0,0,-1);
            case CROSS -> new SpellSpec(Method.UNSET,Shape.CROSS,0,0,0,-1);
            case DOME -> new SpellSpec(Method.UNSET,Shape.DOME,0,0,0,-1);
            case AMOUNT -> new SpellSpec(Method.UNSET,Shape.UNSET,rune.value(),0,0,-1);
            case RANGE -> new SpellSpec(Method.UNSET,Shape.UNSET,0,(int)(rune.value()/Fixed.ONE),0,-1);
            case RADIUS -> new SpellSpec(Method.UNSET,Shape.UNSET,0,0,(int)(rune.value()/Fixed.ONE),-1);
            case AIM -> new SpellSpec(Method.UNSET,Shape.UNSET,0,0,0,(int)rune.value());
            case BURST,BOUNCE,HOMING,FRAGMENT,CHAIN,IGNITE,SLOW,KNOCKBACK -> new SpellSpec(Method.UNSET,Shape.UNSET,0,0,0,-1,ProjectileEffects.from(rune));
            default -> EMPTY;
        };
        return merge(m);
    }
    public boolean compatibleOutput() {
        return (!ShapeVolume.shaped(shape) || effects.empty())
            && (!(shape==Shape.FAN || shape==Shape.WALL) || method==Method.DIRECTED)
            && !(shape==Shape.FAN && range!=0)
            && !(method != Method.DIRECTED && (shape == Shape.BEAM || shape == Shape.SHIELD))
            && !(shape == Shape.BEAM && radius != 0) && !(shape == Shape.SHIELD && range != 0)
            && (effects.empty() || method==Method.DIRECTED && shape!=Shape.SHIELD)
            && (effects.burst()==0 || radius==0 || radius==effects.burst())
            && (effects.bounces()==0 && effects.homing()==0 || shape==Shape.SPHERE || shape==Shape.UNSET);
    }
}
