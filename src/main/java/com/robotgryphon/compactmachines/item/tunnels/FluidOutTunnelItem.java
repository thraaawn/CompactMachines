package com.robotgryphon.compactmachines.item.tunnels;

import com.robotgryphon.compactmachines.core.Registration;
import com.robotgryphon.compactmachines.tunnels.TunnelDefinition;

public class FluidOutTunnelItem extends TunnelItem {
    public FluidOutTunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public TunnelDefinition getDefinition() {
        return Registration.FLUID_OUT_TUNNEL.get();
    }
}
