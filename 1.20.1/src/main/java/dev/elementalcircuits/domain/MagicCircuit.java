package dev.elementalcircuits.domain;

import java.util.List;

public abstract class MagicCircuit {
    public record Cast(long sequence, Mixture mana, SpellSpec spell) { }
    public abstract Blueprint definition();
    public abstract boolean start();
    public abstract void stop();
    public abstract boolean powered();
    public abstract boolean editable();
    public abstract List<Cast> tick(ManaSource source);
    public abstract CircuitSnapshot snapshot();
    public abstract boolean receive(int rune,Packet packet);
    public abstract Packet offered(int rune);
    public abstract void export(int rune,Packet expected);
    public abstract void clearDrives();
    public void drive(int rune){drive(rune,true);}
    public abstract void drive(int rune,boolean value);
    public abstract boolean validSignal(int rune);
    public abstract boolean signal(int rune);
}
