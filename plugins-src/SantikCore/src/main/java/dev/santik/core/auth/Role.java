package dev.santik.core.auth;

public enum Role {
    PLAYER, ADMIN, OWNER;
    public boolean atLeast(Role r) { return ordinal() >= r.ordinal(); }
    public String title() { return this == OWNER ? "Владелец" : this == ADMIN ? "Админ" : "Игрок"; }
}
