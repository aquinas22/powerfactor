package com.powerfactor.client;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorkbenchGuardTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    @Test void requiresOptInIntegratedServerOwnedDirectoryWorldAndToken() {
        assertTrue(WorkbenchGuard.authorized(true,true,"agent-workbench","PowerFactorAgentTest",TOKEN,TOKEN));
        assertFalse(WorkbenchGuard.authorized(false,true,"agent-workbench","PowerFactorAgentTest",TOKEN,TOKEN));
        assertFalse(WorkbenchGuard.authorized(true,false,"agent-workbench","PowerFactorAgentTest",TOKEN,TOKEN));
        assertFalse(WorkbenchGuard.authorized(true,true,"client","PowerFactorAgentTest",TOKEN,TOKEN));
        assertFalse(WorkbenchGuard.authorized(true,true,"agent-workbench","MyWorld",TOKEN,TOKEN));
        assertFalse(WorkbenchGuard.authorized(true,true,"agent-workbench","PowerFactorAgentTest",TOKEN,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse(WorkbenchGuard.authorized(true,true,"agent-workbench","PowerFactorAgentTest",null,null));
    }
    @Test void rejectsPathTraversalAndCommandLikeBlockStates() {
        assertEquals(TOKEN,WorkbenchGuard.id(TOKEN));
        assertThrows(IllegalArgumentException.class,()->WorkbenchGuard.id("../saves"));
        assertEquals("powerfactor:fluid_tank[north=true]",WorkbenchGuard.block("powerfactor:fluid_tank[north=true]"));
        for(String state:new String[]{"minecraft:stone;op Player","../../world","minecraft:stone\nstop","minecraft:stone{Commands:1}"})
            assertThrows(IllegalArgumentException.class,()->WorkbenchGuard.block(state));
    }
    @Test void rejectsNonFiniteCoordinates() {
        assertEquals(-25.5,WorkbenchGuard.finite(-25.5));
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})
            assertThrows(IllegalArgumentException.class,()->WorkbenchGuard.finite(value));
    }
}
