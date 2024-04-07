package net.rollouter.main;

public record Brush(String item, String command, String name, int slot) {

    public Brush withSlot(int slot) {
        return new Brush(item, command, name, slot);
    }
}
