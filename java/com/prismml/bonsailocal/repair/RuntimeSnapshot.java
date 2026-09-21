package com.prismml.bonsailocal.repair;

import java.util.Map;

/** Native work remains protected until both pending work and the child have ended. */
public final class RuntimeSnapshot {
    public final int state, pending;
    public final boolean alive;
    private RuntimeSnapshot(int state, int pending, boolean alive) {
        this.state = state; this.pending = pending; this.alive = alive;
    }
    public static RuntimeSnapshot parse(String json) {
        Map<String, Object> value = MiniJson.object(MiniJson.parse(json));
        return new RuntimeSnapshot(((Number)value.get("state")).intValue(),
                ((Number)value.get("pending")).intValue(), Boolean.TRUE.equals(value.get("alive")));
    }
    public boolean active() { return alive || pending != 0 || state == 2 || state == 3 || state == 6; }
    public String description() {
        if (pending == 2) return "Остановка модели…";
        if (state == 2) return "Модель загружается в память";
        if (state == 6) return "Проверка движка";
        return "Модель работает · чат доступен";
    }
}
