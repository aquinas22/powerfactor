package com.powerfactor.api;

import com.powerfactor.scada.ScadaReading;

/** A block entity a SCADA terminal can monitor (and, if controllable, start/stop) over fiber. */
public interface ScadaDevice {
    ScadaReading scadaReading();

    /** Whether the operator has this device enabled. Only meaningful if the reading is controllable. */
    boolean remoteEnabled();

    void setRemoteEnabled(boolean enabled);
}
