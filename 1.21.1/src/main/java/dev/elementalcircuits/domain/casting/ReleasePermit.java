package dev.elementalcircuits.domain.casting;

/** A carrier's shared release budget. Refusal leaves the complete output payment in the VM. */
@FunctionalInterface
public interface ReleasePermit {
    ReleasePermit UNLIMITED = () -> true;

    boolean tryAcquire();

    default boolean tryAcquire(int output, SpellSpec spell) {
        return tryAcquire();
    }

    default boolean tryAcquire(int slot, int output, SpellSpec spell) {
        return tryAcquire(output, spell);
    }
}
