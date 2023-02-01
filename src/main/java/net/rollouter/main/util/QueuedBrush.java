package net.rollouter.main.util;

public class QueuedBrush {

    public Brush brush;
    public int slot;

    public QueuedBrush(Brush brush, int slot) {
        this.brush = brush;
        this.slot = slot;
    }
    public Brush getBrush() {
        return brush;
    }
    public int getSlot() {
        return slot;
    }
}
