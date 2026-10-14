package com.powerfactor.logistics;

import com.powerfactor.registry.ModComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

/** Cached endpoint filter. Resource identity and components remain untouched during transport. */
final class PipeFilter {
    private final PipeSettings.Face settings;
    private final Resource[] samples;

    PipeFilter(PipeSettings.Face settings, PipeKind kind) {
        this.settings = settings;
        samples = settings.samples().stream().map(stack -> sample(stack, kind)).filter(resource -> !resource.isEmpty()).toArray(Resource[]::new);
    }

    static Resource sample(ItemStack stack, PipeKind kind) {
        if (kind == PipeKind.ITEM) return ItemResource.of(stack);
        var content = stack.get(ModComponents.FLUID.get());
        if (content != null && !content.isEmpty()) return FluidResource.of(content.copy());
        return FluidResource.of(FluidUtil.getFirstStackContained(stack));
    }

    boolean accepts(Resource resource) {
        if (settings.mode() == 0) return true;
        boolean found = false;
        for (Resource sample : samples) {
            if (settings.exact() ? resource.equals(sample)
                    : resource instanceof ItemResource item && sample instanceof ItemResource reference && item.is(reference.getItem())
                    || resource instanceof FluidResource fluid && sample instanceof FluidResource fluidReference && fluid.getFluid().isSame(fluidReference.getFluid())) {
                found = true; break;
            }
        }
        return settings.mode() == 1 ? found : !found;
    }
}
