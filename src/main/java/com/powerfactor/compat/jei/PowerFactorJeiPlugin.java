package com.powerfactor.compat.jei;

import com.powerfactor.PowerFactor;
import com.powerfactor.client.MachineScreen;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.Identifier;

/** Loaded by JEI only when JEI is installed. Keeps JEI's item list off our side tabs. */
@JeiPlugin
public class PowerFactorJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return PowerFactor.id("jei");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler((Class) MachineScreen.class, new IGuiContainerHandler<MachineScreen<?>>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(MachineScreen<?> screen) {
                return screen.extraAreas();
            }
        });
    }
}
