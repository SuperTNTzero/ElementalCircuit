package dev.elementalcircuits.domain;

/** A carrier's shared release budget. Refusal leaves the complete output payment in the VM. */
@FunctionalInterface
public interface ReleasePermit {
    ReleasePermit UNLIMITED = () -> true;
    boolean tryAcquire();
}
