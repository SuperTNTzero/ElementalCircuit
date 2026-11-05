
import dev.elementalcircuits.domain.*;
import dev.elementalcircuits.adapter.*;

import java.util.*;
import java.io.*;
import java.lang.management.ManagementFactory;
public class CoreAudit {
 static volatile int sink;
 static Blueprint dense(int n){List<Rune> nodes=new ArrayList<>();for(int q=-6;q<=6;q++)for(int r=-6;r<=6;r++)if(new Hex(q,r).inside(6) && nodes.size()<n)nodes.add(Rune.create(nodes.size()+1,Rune.Kind.SIGNAL,new Hex(q,r)));return new Blueprint(1,RuleResources.defaults().withMaterial(Rules.Material.BRASS),nodes,List.of(),true);}
 static CircuitAssembly assembly(Blueprint b,int slots){var a=new CircuitAssembly(UUID.randomUUID(),AssemblyResources.rules(),new TickCircuit(b));for(int i=1;i<slots;i++){int id=a.add(b.rules());a.replace(id,b);}return a;}
 static boolean utf(String s){try{new DataOutputStream(new ByteArrayOutputStream()).writeUTF(s);return true;}catch(IOException e){return false;}}
 static final com.sun.management.ThreadMXBean MEM=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
 static void bench(String label,Runnable action,int warm,int samples){for(int i=0;i<warm;i++)action.run();long before=MEM.getThreadAllocatedBytes(Thread.currentThread().getId());double[] t=new double[samples];for(int i=0;i<samples;i++){long start=System.nanoTime();action.run();t[i]=(System.nanoTime()-start)/1e6;}long alloc=MEM.getThreadAllocatedBytes(Thread.currentThread().getId())-before;Arrays.sort(t);System.out.printf(Locale.ROOT,"BENCH %s median_ms=%.4f p95_ms=%.4f alloc_KiB=%.1f%n",label,t[samples/2],t[(int)(samples*.95)],alloc/1024.0/samples);}
 public static void main(String[] args)throws Exception{
  for(int n:new int[]{0,16,32,64})for(int slots:new int[]{1,4}){var b=dense(n);String save=JsonCodecs.assembly(assembly(b,slots));System.out.println("ASSEMBLY nodes="+n+" slots="+slots+" bytes="+save.length()+" binary_utf_fits="+utf(save));}
  Blueprint example=LogicExamples.alternating(RuleResources.defaults(),LogicResources.defaults());System.out.println("LOGIC_EXAMPLE nodes="+example.runes().size());
  for(var b:List.of(example,dense(64))){var v=BlueprintVolume.empty(UUID.randomUUID());for(int i=1;i<=16;i++){v=v.add("page "+i,b);String s=JsonCodecs.volume(v);if(i==1 || i==8 || i==16 || !utf(s)){System.out.println("VOLUME nodes="+b.runes().size()+" pages="+i+" bytes="+s.length()+" binary_utf_fits="+utf(s));if(!utf(s))break;}}}
  for(int nodes:new int[]{0,16}){var a=assembly(dense(nodes),1);bench("typical_idle_1slot_nodes_"+nodes,()->sink^=JsonCodecs.assembly(a).length(),80,150);}
  for(int carriers:new int[]{1,4,16,36}){
   List<CircuitAssembly> list=new ArrayList<>();for(int i=0;i<carriers;i++)list.add(assembly(dense(64),4));
   bench("idle_serialize_"+carriers,()->{for(var a:list)sink^=JsonCodecs.assembly(a).length();},25,80);
   var source=new ManaPool(64*Fixed.ONE,0,0);list.forEach(a->a.slots().forEach(id->a.circuit(id).start()));
   bench("active_vm_"+carriers,()->{for(var a:list){a.clearDrives();a.deliver();sink^=a.advance(source,1).size();a.publish();}},25,80);
  }
  var v=BlueprintVolume.empty(UUID.randomUUID());for(int i=0;i<16;i++)v=v.add("page "+i,example);String book=JsonCodecs.volume(v);
  bench("read_volume_16_logic_pages",()->sink^=JsonCodecs.readVolume(book).pages().size(),30,80);
 }
}
