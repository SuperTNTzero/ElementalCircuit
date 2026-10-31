package dev.elementalcircuits.domain;

import java.util.UUID;

public record CircuitAddress(UUID carrier,int slot,int rune) {
    public CircuitAddress {if(carrier==null || slot<1 || slot>65535 || rune<1 || rune>1024)throw new IllegalArgumentException("接口地址非法");}
}
