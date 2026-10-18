package com.powerfactor.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.powerfactor.item.PowerArmorItem;
import com.powerfactor.registry.ModComponents;
import com.powerfactor.registry.ModItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Opt-in file bridge, active only in the workbench-owned local test world. No listening socket. */
public final class AgentWorkbenchClient {
    private static final boolean ENABLED=Boolean.getBoolean("powerfactor.agentWorkbench");
    private static final String TOKEN=System.getProperty("powerfactor.agentToken","");
    private static final Set<String> BUSY=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private record Pending(String id,long at,Runnable operation) {}
    private static final ArrayList<Pending> PENDING=new ArrayList<>();
    private static long ticks;
    private static Path root() { return Minecraft.getInstance().gameDirectory.toPath().resolve("workbench"); }
    private static boolean ready() {
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();
        return WorkbenchGuard.authorized(ENABLED,server!=null,mc.gameDirectory.getName(),server==null?"":server.getWorldData().getLevelName(),TOKEN,TOKEN)
                && mc.player!=null && mc.level!=null;
    }
    private static void write(Path path,JsonObject data) {
        try {
            Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,data+"\n");
            Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception error) { com.powerfactor.PowerFactor.LOGGER.warn("Workbench response failed: {}",error.getMessage()); }
    }
    private static void reply(String id,JsonObject result,String error) {
        var data=new JsonObject();data.addProperty("request_id",id);data.addProperty("state",error==null?"completed":"failed");
        if(error!=null)data.addProperty("error",error);else data.add("result",result);
        write(root().resolve("responses").resolve(WorkbenchGuard.id(id)+".json"),data);BUSY.remove(id);
    }
    private static JsonObject object() { return new JsonObject(); }
    /** While a scripted drive runs, the vehicle clients don't send real (idle) key input over it. */
    private static long driveUntil;
    public static boolean scriptedDrive() { return ticks < driveUntil; }
    private static void later(String id,int delay,Runnable operation) { PENDING.add(new Pending(id,ticks+delay,operation)); }
    public static void tick() {
        if(!ENABLED || !TOKEN.matches("[a-f0-9]{32}"))return;
        ticks++;
        if(ticks%20==0) {
            var status=object();status.addProperty("ready",ready());status.addProperty("timestamp",System.currentTimeMillis()/1000.0);
            status.addProperty("world","PowerFactorAgentTest");status.addProperty("game_directory",Minecraft.getInstance().gameDirectory.toString());
            write(root().resolve("status.json"),status);
        }
        var due=PENDING.stream().filter(p->p.at()<=ticks).toList();PENDING.removeAll(due);
        for(var pending:due) {
            try { if(!ready())throw new IllegalStateException("Dedicated test world is no longer active");pending.operation().run(); }
            catch(Exception error){reply(pending.id(),null,error.toString());}
        }
        if(!ready() || ticks%2!=0)return;
        Path requests=root().resolve("requests");if(!Files.isDirectory(requests))return;
        try(var files=Files.list(requests)) {
            for(Path file:files.filter(p->p.getFileName().toString().matches("[a-f0-9]{32}\\.json"))
                    .filter(p->!Files.exists(root().resolve("responses").resolve(p.getFileName()))
                            && !BUSY.contains(p.getFileName().toString().replace(".json",""))).sorted().limit(32).toList()) {
                String id=file.getFileName().toString().replace(".json","");
                if(BUSY.contains(id)||Files.exists(root().resolve("responses").resolve(id+".json")))continue;
                BUSY.add(id);
                try {
                    if(Files.size(file)>131072)throw new IllegalArgumentException("Request too large");
                    var request=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                    if(!id.equals(request.get("id").getAsString()) || !TOKEN.equals(request.get("token").getAsString()))throw new IllegalArgumentException("Wrong session or request ID");
                    var args=request.has("arguments")?request.getAsJsonObject("arguments"):object();
                    dispatch(id,request.get("action").getAsString(),args);
                } catch(Exception error){reply(id,null,error.toString());}
            }
        }catch(Exception error){com.powerfactor.PowerFactor.LOGGER.warn("Workbench request failed: {}",error.getMessage());}
    }
    private static double number(JsonObject args,String name) { return WorkbenchGuard.finite(args.get(name).getAsDouble()); }
    private static BlockPos position(JsonObject args) {
        double dx=number(args,"x"),dy=number(args,"y"),dz=number(args,"z");
        if(dx!=Math.rint(dx)||dy!=Math.rint(dy)||dz!=Math.rint(dz)||Math.abs(dx)>4096||Math.abs(dz)>4096||dy < -64||dy>319)
            throw new IllegalArgumentException("Expected integer coordinates inside test area");
        int x=(int)dx,y=(int)dy,z=(int)dz;
        if(Math.abs((long)x)>4096 || Math.abs((long)z)>4096 || y < -64 || y>319)throw new IllegalArgumentException("Coordinate outside test area");
        return new BlockPos(x,y,z);
    }
    private static void dispatch(String id,String action,JsonObject args) {
        var mc=Minecraft.getInstance();
        switch(action) {
            case "snapshot" -> reply(id,snapshot(),null);
            case "wait_ticks" -> later(id,Math.clamp(args.get("ticks").getAsInt(),1,200),()->reply(id,snapshot(),null));
            case "look" -> {
                mc.player.setYRot((float)number(args,"yaw"));mc.player.setXRot((float)Math.clamp(number(args,"pitch"),-90,90));
                later(id,2,()->reply(id,snapshot(),null));
            }
            case "camera" -> {
                var mode=args.get("mode").getAsString();
                mc.options.setCameraType(switch(mode){case "front"->net.minecraft.client.CameraType.THIRD_PERSON_FRONT;case "back"->net.minecraft.client.CameraType.THIRD_PERSON_BACK;case "first"->net.minecraft.client.CameraType.FIRST_PERSON;default->throw new IllegalArgumentException("Invalid camera mode");});
                later(id,2,()->reply(id,snapshot(),null));
            }
            case "hud" -> { HudPreferences.set(args.get("armor").getAsBoolean(),args.get("movement").getAsBoolean());reply(id,snapshot(),null); }
            case "open_settings" -> { mc.gui.setScreen(new PowerArmorScreen());later(id,2,()->reply(id,snapshot(),null)); }
            case "close_gui" -> { mc.gui.setScreen(null);later(id,2,()->reply(id,snapshot(),null)); }
            case "hover_gui" -> {
                var screen=mc.gui.screen();if(screen==null)throw new IllegalStateException("No GUI open");
                double x=number(args,"x"),y=number(args,"y");
                if(x<0 || y<0 || x>=screen.width || y>=screen.height)throw new IllegalArgumentException("Invalid GUI position");
                double scale=mc.getWindow().getGuiScale();
                double wx=x*scale+scale/2,wy=y*scale+scale/2;
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().handle(),wx,wy);
                // Headless displays don't report the warp: feed the game's own move handler too.
                try{var move=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);move.setAccessible(true);move.invoke(mc.mouseHandler,mc.getWindow().handle(),wx,wy);}
                catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot move the cursor: "+e);}
                later(id,4,()->reply(id,snapshot(),null));
            }
            case "click_gui" -> {
                var screen=mc.gui.screen();if(screen==null)throw new IllegalStateException("No GUI open");
                double x=number(args,"x"),y=number(args,"y");int button=args.has("button")?args.get("button").getAsInt():0;
                if(button<0 || button>1 || x<0 || y<0 || x>=screen.width || y>=screen.height)throw new IllegalArgumentException("Invalid GUI click");
                var event=new MouseButtonEvent(x,y,new MouseButtonInfo(button,0));boolean handled=screen.mouseClicked(event,false);screen.mouseReleased(event);
                later(id,3,()->{var result=snapshot();result.addProperty("handled",handled);reply(id,result,null);});
            }
            case "screenshot" -> later(id,3,()->Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(),image->{
                try(image){Path file=root().resolve("screenshots").resolve(WorkbenchGuard.id(id)+".png");image.writeToFile(file);var result=object();result.addProperty("image",file.toAbsolutePath().toString());reply(id,result,null);}
                catch(Exception error){reply(id,null,error.toString());}
            }));
            case "show_recipes" -> {
                var key=Identifier.parse(args.get("item").getAsString());
                if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown item");
                com.powerfactor.compat.jei.ClientRecipes.show.accept(new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(key)),
                        args.has("uses") && args.get("uses").getAsBoolean());
                later(id,4,()->reply(id,snapshot(),null));
            }
            case "drive" -> {
                // Hold vehicle controls for some ticks, as the keys would, via the same server-side input path.
                float throttle=(float)number(args,"throttle"),steer=args.has("steer")?(float)number(args,"steer"):0;
                boolean boost=args.has("boost")&&args.get("boost").getAsBoolean(),lift=args.has("lift")&&args.get("lift").getAsBoolean();
                int n=Math.clamp(args.get("ticks").getAsInt(),1,200);driveUntil=ticks+n+1;
                for(int i=0;i<n;i++)later(id+"#"+i,i,()->{var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();server.execute(()->{
                    var p=server.getPlayerList().getPlayer(uuid);if(p==null)return;
                    if(p.getVehicle() instanceof com.powerfactor.vehicle.HoverBikeEntity bike)bike.input(p,throttle,steer,false,boost,lift);
                    else if(p.getVehicle() instanceof com.powerfactor.vehicle.RoverEntity rover)rover.input(p,throttle,steer,lift,boost);
                    else if(p.getVehicle() instanceof com.powerfactor.vehicle.MechEntity mech)mech.input(p,throttle,steer,boost,lift);});});
                later(id,n+1,()->reply(id,snapshot(),null));
            }
            case "stop_client" -> {reply(id,object(),null);mc.stop();}
            case "teleport","place_blocks","equip_armor","open_machine","inspect_block","spawn_meteor","place_feature","spawn_vehicle","set_time","fill_tank","drain_tank","network_insert","insert_item","use_item" -> serverAction(id,action,args);
            default -> throw new IllegalArgumentException("Unknown action "+action);
        }
    }
    private static void serverAction(String id,String action,JsonObject args) {
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
        server.execute(()->{
            try {
                if(!server.getWorldData().getLevelName().equals("PowerFactorAgentTest"))throw new IllegalStateException("World changed");
                ServerPlayer player=server.getPlayerList().getPlayer(playerId);if(player==null)throw new IllegalStateException("No test player");
                ServerLevel world=(ServerLevel)player.level();var result=object();
                switch(action) {
                    case "teleport" -> {var pos=position(args);player.stopRiding();player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);}
                    case "equip_armor" -> {
                        PowerArmorItem[] items={ModItems.POWER_HELMET.get(),ModItems.POWER_CHESTPLATE.get(),ModItems.POWER_LEGGINGS.get(),ModItems.POWER_BOOTS.get()};
                        for(var item:items){var stack=new ItemStack(item);stack.set(ModComponents.ENERGY.get(),args.has("energy")?Math.clamp(args.get("energy").getAsInt(),0,item.capacity(stack)):item.capacity(stack));player.setItemSlot(item.part().slot,stack);}
                        if(args.has("grapple") && args.get("grapple").getAsBoolean()){var stack=new ItemStack(ModItems.GRAPPLE_HOOK.get());stack.set(ModComponents.ENERGY.get(),com.powerfactor.item.GrappleItem.CAPACITY);player.setItemSlot(EquipmentSlot.MAINHAND,stack);}
                    }
                    case "place_blocks" -> {
                        var blocks=args.getAsJsonArray("blocks");if(blocks.size()>256)throw new IllegalArgumentException("Too many blocks");
                        var positions=new ArrayList<BlockPos>();var states=new ArrayList<BlockState>();
                        for(var entry:blocks){var block=entry.getAsJsonObject();var pos=position(block);if(!world.hasChunkAt(pos))throw new IllegalArgumentException("Block chunk is not loaded");positions.add(pos);states.add(parseBlock(WorkbenchGuard.block(block.get("block").getAsString())));}
                        int changed=0;
                        for(int i=0;i<positions.size();i++)if(world.setBlock(positions.get(i),states.get(i),Block.UPDATE_ALL))changed++;
                        result.addProperty("requested",positions.size());result.addProperty("changed",changed);
                    }
                    case "open_machine" -> {
                        var pos=position(args);if(!world.hasChunkAt(pos))throw new IllegalArgumentException("Block chunk is not loaded");var direction=args.has("face")?Direction.valueOf(args.get("face").getAsString().toUpperCase(java.util.Locale.ROOT)):Direction.UP;
                        var hit=new BlockHitResult(Vec3.atCenterOf(pos).add(direction.getStepX()*.5,direction.getStepY()*.5,direction.getStepZ()*.5),direction,pos,false);
                        boolean sneak=args.has("sneak") && args.get("sneak").getAsBoolean();player.setShiftKeyDown(sneak);
                        boolean held=args.has("held") && args.get("held").getAsBoolean();
                        try{if(held)player.gameMode.useItemOn(player,world,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,hit);else world.getBlockState(pos).useWithoutItem(world,player,hit);}finally{player.setShiftKeyDown(false);}
                    }
                    case "fill_tank","drain_tank" -> {
                        var pos=position(args);if(!world.hasChunkAt(pos))throw new IllegalArgumentException("Block chunk is not loaded");
                        var handler=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,pos,Direction.UP);
                        if(handler==null)throw new IllegalArgumentException("Block has no fluid capability");
                        var resource=handler.getResource(0);
                        if(args.has("fluid")) {
                            var key=Identifier.parse(args.get("fluid").getAsString());
                            if(!BuiltInRegistries.FLUID.containsKey(key))throw new IllegalArgumentException("Unknown fluid");
                            resource=net.neoforged.neoforge.transfer.fluid.FluidResource.of(BuiltInRegistries.FLUID.getValue(key));
                        }
                        if(args.has("data"))resource=resource.with(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                net.minecraft.world.item.component.CustomData.of(net.minecraft.nbt.CompoundTag.CODEC
                                        .parse(com.mojang.serialization.JsonOps.INSTANCE,args.get("data")).getOrThrow()));
                        int amount=Math.clamp(args.get("amount").getAsInt(),0,1000000);
                        boolean commit=!args.has("commit") || args.get("commit").getAsBoolean();
                        try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                            result.addProperty("transferred",action.equals("fill_tank")?handler.insert(resource,amount,tx):handler.extract(resource,amount,tx));
                            if(commit)tx.commit();
                        }
                        result.addProperty("committed",commit);
                        if(world.getBlockEntity(pos) instanceof com.powerfactor.logistics.FluidTankBlockEntity tank)tank.balance();
                    }
                    case "use_item" -> {
                        // Fixture helper: hold a fully charged item and use it (release after charge_ticks for charged weapons).
                        var key=Identifier.parse(args.get("item").getAsString());
                        if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown item "+key);
                        // keep=true uses the item already in hand (with its contents) instead of a fresh one.
                        boolean keep=args.has("keep")&&args.get("keep").getAsBoolean()&&player.getMainHandItem().is(BuiltInRegistries.ITEM.getValue(key));
                        var stack=keep?player.getMainHandItem():new ItemStack(BuiltInRegistries.ITEM.getValue(key));
                        if(!keep&&stack.getItem() instanceof com.powerfactor.item.PoweredItem powered)stack.set(ModComponents.ENERGY.get(),powered.capacity(stack));
                        if(!keep)player.setItemSlot(EquipmentSlot.MAINHAND,stack);
                        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
                        result.addProperty("use",stack.getItem().use(world,player,hand).toString());
                        if(args.has("charge_ticks")){int t=Math.clamp(args.get("charge_ticks").getAsInt(),0,200);
                            stack.getItem().releaseUsing(stack,world,player,stack.getUseDuration(player)-t);player.stopUsingItem();}
                    }
                    case "insert_item" -> {
                        // Fixture helper: insert items into a block's item capability (machine inputs, fuel).
                        var pos=position(args);if(!world.hasChunkAt(pos))throw new IllegalArgumentException("Block chunk is not loaded");
                        var handler=world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,pos,Direction.UP);
                        if(handler==null)throw new IllegalArgumentException("Block has no item capability");
                        var key=Identifier.parse(args.get("item").getAsString());
                        if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown item "+key);
                        var resource=net.neoforged.neoforge.transfer.item.ItemResource.of(BuiltInRegistries.ITEM.getValue(key));
                        if(args.has("custom_data")){var stack=resource.toStack(1);
                            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,stack,
                                    t->args.getAsJsonObject("custom_data").entrySet().forEach(e->t.putString(e.getKey(),e.getValue().getAsString())));
                            resource=net.neoforged.neoforge.transfer.item.ItemResource.of(stack);}
                        try(var tx=net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()){
                            result.addProperty("inserted",handler.insert(resource,
                                    Math.clamp(args.get("count").getAsInt(),1,4096),tx));tx.commit();}
                    }
                    case "network_insert" -> {
                        // Seed a test storage network through its controller, for visual review fixtures.
                        var pos=position(args);if(!world.hasChunkAt(pos))throw new IllegalArgumentException("Block chunk is not loaded");
                        if(!(world.getBlockEntity(pos) instanceof com.powerfactor.storage.StorageControllerBlockEntity controller) || controller.ownNetwork()==null)
                            throw new IllegalArgumentException("No storage controller with a network at that position");
                        if(args.has("install_cells") && args.get("install_cells").getAsBoolean()){int t=0;var tiers=com.powerfactor.storage.CellTier.values();
                            for(var node:controller.ownNetwork().nodes())if(node instanceof com.powerfactor.storage.DriveBayBlockEntity drive)
                                for(int i=0;i<com.powerfactor.storage.DriveBayBlockEntity.SLOTS;i++)if(drive.cells().getItem(i).isEmpty()){
                                    // The last three bays take fluid cells so fixtures cover both kinds.
                                    int f=i-(com.powerfactor.storage.DriveBayBlockEntity.SLOTS-3);var ftiers=com.powerfactor.storage.fluid.FluidCellTier.values();
                                    drive.cells().setItem(i,f>=0?new ItemStack(com.powerfactor.registry.ModStorage.FLUID_CELLS.get(ftiers[f%ftiers.length]).get())
                                            :new ItemStack(com.powerfactor.registry.ModStorage.CELLS.get(tiers[(t++)%tiers.length]).get()));}
                            controller.ownNetwork().markStoragesDirty();}
                        long inserted=0;
                        for(var entry:args.getAsJsonArray("items")){var o=entry.getAsJsonObject();var key=Identifier.parse(o.get("id").getAsString());
                            if(!BuiltInRegistries.ITEM.containsKey(key))throw new IllegalArgumentException("Unknown item "+key);
                            inserted+=controller.ownNetwork().insert(net.neoforged.neoforge.transfer.item.ItemResource.of(BuiltInRegistries.ITEM.getValue(key)),Math.clamp(o.get("count").getAsLong(),1,1_000_000),false);}
                        result.addProperty("inserted",inserted);
                    }
                    case "set_time" -> {
                        long t=Math.clamp(args.get("time").getAsLong(),0,24_000);
                        world.clockManager().setTotalTicks(world.dimensionType().defaultClock().orElseThrow(),t);
                        result.addProperty("time",t);
                    }
                    case "spawn_vehicle" -> {
                        // A charged hover-bike at x,y,z facing the player's way; ride=true seats the test player.
                        var pos=position(args);if(!world.hasChunkAt(pos))throw new IllegalArgumentException("Block chunk is not loaded");
                        boolean mech=args.has("kind")&&args.get("kind").getAsString().equals("mech");
                        net.minecraft.world.entity.Entity bike;
                        if(mech){var m=new com.powerfactor.vehicle.MechEntity(com.powerfactor.registry.ModEntities.MECH.get(),world);
                            var st=new ItemStack(com.powerfactor.registry.ModItems.MECH.get());st.set(com.powerfactor.registry.ModComponents.ENERGY.get(),com.powerfactor.vehicle.MechEntity.CAPACITY);m.restore(st);bike=m;}
                        else{var b=new com.powerfactor.vehicle.HoverBikeEntity(com.powerfactor.registry.ModEntities.HOVER_BIKE.get(),world);
                            var st=new ItemStack(com.powerfactor.registry.ModItems.HOVER_BIKE.get());st.set(com.powerfactor.registry.ModComponents.ENERGY.get(),com.powerfactor.vehicle.HoverBikeEntity.CAPACITY);b.restore(st);bike=b;}
                        bike.setPos(pos.getX()+.5,pos.getY()+(mech?0:.5),pos.getZ()+.5);bike.setYRot(player.getYRot());world.addFreshEntity(bike);
                        if(args.has("ride") && args.get("ride").getAsBoolean())player.startRiding(bike);
                        result.addProperty("id",bike.getId());
                    }
                    case "place_feature" -> {
                        // Run a PowerFactor worldgen feature at a position, for reviewing generated structures.
                        var pos=position(args);if(!world.hasChunkAt(pos))throw new IllegalArgumentException("Block chunk is not loaded");
                        var feature=switch(args.get("feature").getAsString()){case "quantum_lab"->com.powerfactor.registry.ModWorldgen.QUANTUM_LAB.get();
                            default->throw new IllegalArgumentException("Unknown feature");};
                        var ctx=new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(java.util.Optional.empty(),world,
                                world.getChunkSource().getGenerator(),world.getRandom(),pos,net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE);
                        result.addProperty("placed",feature.place(ctx));
                    }
                    case "spawn_meteor" -> {
                        int size=args.has("size")?Math.clamp(args.get("size").getAsInt(),1,3):2;
                        var meteor=com.powerfactor.world.MeteorShowers.launch(world,player.position(),size,true);
                        result.addProperty("x",meteor.getX());result.addProperty("y",meteor.getY());result.addProperty("z",meteor.getZ());
                        var v=meteor.getDeltaMovement();result.addProperty("vx",v.x);result.addProperty("vy",v.y);result.addProperty("vz",v.z);
                    }
                    case "inspect_block" -> {
                        var pos=position(args);if(!world.hasChunkAt(pos))throw new IllegalArgumentException("Block chunk is not loaded");result.addProperty("block",BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).toString());
                        result.addProperty("state",world.getBlockState(pos).toString());
                        var be=world.getBlockEntity(pos);if(be!=null)result.addProperty("data",be.saveWithFullMetadata(world.registryAccess()).toString());
                        if(be instanceof com.powerfactor.logistics.FluidTankBlockEntity tank){var group=tank.getReservoir().group();result.addProperty("amount",group.amount());result.addProperty("capacity",group.capacity());result.addProperty("cells",group.cells().size());}
                    }
                }
                mc.execute(()->later(id,3,()->reply(id,result,null)));
            }catch(Exception error){mc.execute(()->reply(id,null,error.toString()));}
        });
    }
    private static <T extends Comparable<T>> BlockState value(BlockState state,Property<T> property,String text) {
        return state.setValue(property,property.getValue(text).orElseThrow(()->new IllegalArgumentException("Invalid property value "+text)));
    }
    private static BlockState parseBlock(String text) {
        int bracket=text.indexOf('[');String id=bracket<0?text:text.substring(0,bracket);Identifier key=Identifier.parse(id);
        if(!BuiltInRegistries.BLOCK.containsKey(key))throw new IllegalArgumentException("Unknown block "+id);
        var block=BuiltInRegistries.BLOCK.getValue(key);var state=block.defaultBlockState();
        if(bracket>=0)for(String assignment:text.substring(bracket+1,text.length()-1).split(",")) {
            var parts=assignment.split("=",2);var property=block.getStateDefinition().getProperty(parts[0]);
            if(parts.length!=2 || property==null)throw new IllegalArgumentException("Invalid block property");state=value(state,property,parts[1]);
        }
        return state;
    }
    private static JsonObject snapshot() {
        var mc=Minecraft.getInstance();var result=object();result.addProperty("game_time",mc.level.getGameTime());
        result.addProperty("x",mc.player.getX());result.addProperty("y",mc.player.getY());result.addProperty("z",mc.player.getZ());
        result.addProperty("yaw",mc.player.getYRot());result.addProperty("pitch",mc.player.getXRot());
        result.addProperty("mouse_x",mc.mouseHandler.getScaledXPos(mc.getWindow()));result.addProperty("mouse_y",mc.mouseHandler.getScaledYPos(mc.getWindow()));
        result.addProperty("gui_width",mc.getWindow().getGuiScaledWidth());result.addProperty("gui_height",mc.getWindow().getGuiScaledHeight());
        result.addProperty("armor_hud",HudPreferences.armor());result.addProperty("movement_hud",HudPreferences.movement());
        var screen=mc.gui.screen();result.addProperty("screen",screen==null?"none":screen.getClass().getName());
        var equipment=new JsonObject();for(var slot:EquipmentSlot.values())equipment.addProperty(slot.getName(),BuiltInRegistries.ITEM.getKey(mc.player.getItemBySlot(slot).getItem()).toString());result.add("equipment",equipment);
        if(screen instanceof AbstractContainerScreen<?> container) {
            result.addProperty("gui_left",container.getGuiLeft());result.addProperty("gui_top",container.getGuiTop());
            var slots=new JsonArray();for(var slot:container.getMenu().slots){var entry=object();entry.addProperty("index",slot.index);entry.addProperty("x",container.getGuiLeft()+slot.x);entry.addProperty("y",container.getGuiTop()+slot.y);entry.addProperty("active",slot.isActive());entry.addProperty("item",BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString());entry.addProperty("count",slot.getItem().getCount());slots.add(entry);}result.add("slots",slots);
        }
        return result;
    }
    private AgentWorkbenchClient() {}
}
