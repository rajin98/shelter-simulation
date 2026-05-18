package com.sheltersim.model;

public enum CellLabel {
    EMPTY('0'),
    ROOM_A('a'), ROOM_B('b'), ROOM_C('c'), ROOM_D('d'), ROOM_E('e'),
    PORCH('2'), COURTYARD('3'),
    KITCHEN_ROOM('4'), GARDEN('5'),
    BATHROOM('B');

    public final char symbol;

    CellLabel(char symbol) {
        this.symbol = symbol;
    }

    public boolean isSolid() {
        return this != EMPTY;
    }

    public static CellLabel fromChar(char c) {
        for (CellLabel label : values()) {
            if (label.symbol == c) return label;
        }
        throw new IllegalArgumentException("Unknown cell symbol: " + c);
    }
}
