package dev.elementalcircuits.domain;

import java.util.*;

/** One component budget shared by every responding world target; no Minecraft dependency. */
public final class EffectDistribution {
    public record Target(String key,List<Long> minimums) {
        public Target {
            if(key==null || key.isEmpty() || minimums==null || minimums.size()!=Mixture.COMPONENTS)throw new IllegalArgumentException("非法作用目标");
            minimums=List.copyOf(minimums);minimums.forEach(Fixed::checked);
        }
    }
    public record Share(String key,Mixture mana) { }
    public record Plan(List<Share> shares,Mixture dissipated) {
        public Plan { shares=List.copyOf(shares); }
    }
    private EffectDistribution(){ }
    public static Plan allocate(Mixture mana,List<Target> targets){
        if(targets.size()>128)throw new IllegalArgumentException("作用目标超限");
        List<Target> ordered=targets.stream().sorted(Comparator.comparing(Target::key)).toList();
        if(ordered.stream().map(Target::key).distinct().count()!=ordered.size())throw new IllegalArgumentException("重复作用目标");
        long[][] assigned=new long[ordered.size()][Mixture.COMPONENTS];long[] waste=mana.values();
        for(int component=0;component<Mixture.COMPONENTS;component++){
            long amount=waste[component];if(amount==0)continue;List<Integer> eligible=new ArrayList<>();
            for(int i=0;i<ordered.size();i++)if(ordered.get(i).minimums().get(component)>0)eligible.add(i);
            // Trim unaffordable recipients before equal sharing; a wide area can still affect a few blocks.
            boolean changed=true;
            while(changed && !eligible.isEmpty()){
                changed=false;long share=amount/eligible.size();
                for(int j=eligible.size()-1;j>=0;j--)if(ordered.get(eligible.get(j)).minimums().get(component)>share){eligible.remove(j);changed=true;break;}
            }
            if(eligible.isEmpty())continue;
            long each=amount/eligible.size(),remainder=amount%eligible.size();
            for(int i:eligible){assigned[i][component]=each+(remainder-->0?1:0);}
            waste[component]=0;
        }
        List<Share> shares=new ArrayList<>();
        for(int i=0;i<ordered.size();i++){Mixture m=new Mixture(assigned[i]);if(!m.empty())shares.add(new Share(ordered.get(i).key(),m));}
        return new Plan(shares,new Mixture(waste));
    }
}
