package com.lionclient.combat.lag;

import java.util.Set;

public final class LagRequest {
    private final Set<LagDirection> directions;
    private final AbstractTimeout timeout;

    public LagRequest(Set<LagDirection> directions, AbstractTimeout timeout) {
        this.directions = directions;
        this.timeout = timeout;
    }

    public Set<LagDirection> getDirections() {
        return directions;
    }

    public AbstractTimeout getTimeout() {
        return timeout;
    }
}
