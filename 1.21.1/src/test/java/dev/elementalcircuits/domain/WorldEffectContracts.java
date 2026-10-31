package dev.elementalcircuits.domain;

import dev.elementalcircuits.adapter.WorldInteractionResources;
import java.util.*;

public final class WorldEffectContracts {
    private static int count;
    private static void check(boolean condition,String message){count++;if(!condition)throw new AssertionError(message);}
    private static EffectDistribution.Target target(String key,int component,long minimum){
        List<Long> needs=new ArrayList<>(Collections.nCopies(17,0L));needs.set(component,minimum);return new EffectDistribution.Target(key,needs);
    }
    public static int run(){
        count=0;var rules=WorldInteractionResources.rules();check(rules.sourceCost()==Fixed.ONE,"生成火焰/水源每目标至少支付1点作用量");
        check(rules.required(Fixed.ONE,98304)==43691,"强度阈值向上舍入，不能免费越过动作成本");
        int fire=Element.FIRE.component(false),water=Element.WATER.component(false);long[] values=new long[17];values[fire]=4*Fixed.ONE;values[water]=Fixed.ONE;
        Mixture budget=new Mixture(values);List<EffectDistribution.Target> candidates=new ArrayList<>();
        for(int i=0;i<12;i++)candidates.add(target("B:"+i,fire,Fixed.ONE));
        var plan=EffectDistribution.allocate(budget,candidates);
        check(plan.shares().size()==4,"范围预算4点只生成最多4个单价1点目标");
        check(plan.dissipated().values()[water]==Fixed.ONE,"无响应目标的元素进入耗散");
        Mixture sum=plan.dissipated();for(var share:plan.shares())sum=sum.plus(share.mana());check(sum.equals(budget),"计划预算与耗散精确守恒");
        Collections.reverse(candidates);check(EffectDistribution.allocate(budget,candidates).equals(plan),"查询顺序不改变分配与余数");
        var poor=EffectDistribution.allocate(new Mixture(values).retain(Fixed.ONE/8),candidates);check(poor.shares().isEmpty(),"不足单个动作成本时不改变方块");
        Random random=new Random(20261029);
        for(int run=0;run<120;run++){
            List<EffectDistribution.Target> targets=new ArrayList<>();long[] raw=new long[17];
            for(int c=0;c<17;c++)raw[c]=random.nextInt(1000000);
            for(int i=0;i<20;i++){
                List<Long> minimums=new ArrayList<>();for(int c=0;c<17;c++)minimums.add(random.nextBoolean()?1L+random.nextInt(100000):0L);
                targets.add(new EffectDistribution.Target("target:"+i,minimums));
            }
            Mixture original=new Mixture(raw);var allocated=EffectDistribution.allocate(original,targets);Mixture all=allocated.dissipated();
            for(var share:allocated.shares()){
                all=all.plus(share.mana());var receiver=targets.stream().filter(t->t.key().equals(share.key())).findFirst().orElseThrow();
                for(int c=0;c<17;c++)if(share.mana().values()[c]>0)check(receiver.minimums().get(c)>0 && share.mana().values()[c]>=receiver.minimums().get(c),"每个接收者具有能力且份额足够动作");
            }
            check(all.equals(original),"混合正反相世界作用量不复制或丢失");Collections.shuffle(targets,random);
            check(allocated.equals(EffectDistribution.allocate(original,targets)),"混合能力分配稳定");
        }
        boolean rejected=false;try{EffectDistribution.allocate(budget,List.of(target("same",fire,1),target("same",fire,1)));}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"同一物理目标不能重复领作用量");return count;
    }
}
