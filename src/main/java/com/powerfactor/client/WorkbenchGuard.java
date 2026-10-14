package com.powerfactor.client;

/** Pure protocol checks, kept separate so automated tests need no graphics device. */
public final class WorkbenchGuard {
    public static boolean authorized(boolean enabled,boolean integrated,String directory,String world,String expected,String supplied) {
        return enabled && integrated && "agent-workbench".equals(directory) && "PowerFactorAgentTest".equals(world)
                && expected != null && expected.matches("[a-f0-9]{32}") && expected.equals(supplied);
    }
    public static String id(String id) {
        if(id==null || !id.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid request ID");return id;
    }
    public static String block(String state) {
        if(state==null || state.length()>256 || !state.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+(?:\\[[a-z0-9_=,.-]+\\])?"))
            throw new IllegalArgumentException("Invalid block state");return state;
    }
    public static double finite(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Non-finite coordinate or angle");return value;
    }
    private WorkbenchGuard() {}
}
