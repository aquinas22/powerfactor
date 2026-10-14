package com.powerfactor.gametest;

import com.powerfactor.machine.MachineBlock;
import com.powerfactor.quantum.ColliderBlockEntity;
import com.powerfactor.quantum.QuarryBlockEntity;
import com.powerfactor.quantum.ZeroPointBlockEntity;
import com.powerfactor.registry.ModQuantum;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Quarry, collider, zero-point generator and arsenal checks. Uses the 9x3x3 arena. */
final class QuantumGameTests {
    static final List<String> NAMES = List.of("quarry_mines_area_into_chest", "collider_makes_qubits", "zero_point_powers_neighbour",
            "railgun_pierces_a_line", "exotic_armor_set_bonuses",
            "plasma_turret_defends", "railgun_turret_pierces", "teleporter_sends_player",
            "robot_arm_moves_filtered",
            "orbital_collector_needs_night_sky",
            "logic_controller_programs", "space_elevator_raises_and_powers",
            "shield_repels_hostiles");

    static final List<String> TALL = List.of("starforge_fuses_paxel", "fusion_ignites_and_quenches", "meteor_leaves_meteorite",
            "hover_bike_hovers_and_drives", "gravity_lift_raises_items", "mech_walks_jets_and_stomps");

    static void register(DeferredRegister<Consumer<GameTestHelper>> functions) {
        functions.register("starforge_fuses_paxel", () -> QuantumGameTests::starforgeFuses);
        functions.register("fusion_ignites_and_quenches", () -> QuantumGameTests::fusionIgnites);
        functions.register("meteor_leaves_meteorite", () -> QuantumGameTests::meteorLands);
        functions.register("hover_bike_hovers_and_drives", () -> QuantumGameTests::hoverBike);
        functions.register("gravity_lift_raises_items", () -> QuantumGameTests::gravityLift);
        functions.register("mech_walks_jets_and_stomps", () -> QuantumGameTests::mechSuit);
        functions.register("quantum_lab_generates", () -> QuantumGameTests::labGenerates);
        functions.register("quarry_mines_area_into_chest", () -> QuantumGameTests::quarryMines);
        functions.register("collider_makes_qubits", () -> QuantumGameTests::colliderMakesQubits);
        functions.register("zero_point_powers_neighbour", () -> QuantumGameTests::zeroPointPowers);
        functions.register("railgun_pierces_a_line", () -> QuantumGameTests::railgunPierces);
        functions.register("exotic_armor_set_bonuses", () -> QuantumGameTests::armorSetBonuses);
        functions.register("plasma_turret_defends", () -> QuantumGameTests::plasmaTurretDefends);
        functions.register("railgun_turret_pierces", () -> QuantumGameTests::railTurretPierces);
        functions.register("teleporter_sends_player", () -> QuantumGameTests::teleporterSends);
        functions.register("robot_arm_moves_filtered", () -> QuantumGameTests::robotArmMoves);
        functions.register("orbital_collector_needs_night_sky", () -> QuantumGameTests::orbitalCollector);
        functions.register("logic_controller_programs", () -> QuantumGameTests::logicController);
        functions.register("space_elevator_raises_and_powers", () -> QuantumGameTests::spaceElevator);
        functions.register("shield_repels_hostiles", () -> QuantumGameTests::shieldRepels);
    }

    private static void quarryMines(GameTestHelper h) {
        BlockPos q = new BlockPos(0, 1, 1);
        h.setBlock(q, ModQuantum.QUANTUM_QUARRY.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(0, 2, 1), Blocks.CHEST);
        QuarryBlockEntity quarry = h.getBlockEntity(q, QuarryBlockEntity.class);
        quarry.getEnergy().set(QuarryBlockEntity.Tier.QUANTUM.capacity);
        // Smallest square (9x9) starts one block east; put identifiable ore in it at quarry level.
        BlockPos ore = new BlockPos(2, 1, 1);
        h.setBlock(ore, Blocks.DIAMOND_ORE);
        h.setBlock(new BlockPos(3, 1, 1), Blocks.COBBLESTONE);
        for (int i = 0; i < 20; i++) quarry.button(0);
        h.assertValueEqual(quarry.size(), 9, "shrunk to the minimum");
        ChestBlockEntity chest = h.getBlockEntity(new BlockPos(0, 2, 1), ChestBlockEntity.class);
        h.runAfterDelay(40, () -> {
            h.assertBlockPresent(Blocks.AIR, ore);
            h.assertTrue(chest.countItem(Items.DIAMOND) >= 1, "diamond delivered to the chest");
            h.assertValueEqual(chest.countItem(Items.COBBLESTONE), 0, "rubble voided by default");
            h.assertTrue(quarry.getEnergy().getAmountAsInt() < QuarryBlockEntity.Tier.QUANTUM.capacity, "energy spent");
            h.succeed();
        });
    }

    private static void colliderMakesQubits(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, ModQuantum.PARTICLE_COLLIDER.get().defaultBlockState());
        ColliderBlockEntity c = h.getBlockEntity(p, ColliderBlockEntity.class);
        c.getEnergy().set(ColliderBlockEntity.CAPACITY);
        try (Transaction tx = Transaction.openRoot()) {
            c.getItems().insert(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.DIAMOND), 2, tx);
            tx.commit();
        }
        // 4M FE at 250k FE/t: 16 ticks per collision.
        h.runAfterDelay(40, () -> {
            var out = c.getItems().getResource(1);
            h.assertTrue(out.is(ModQuantum.QUBIT_CRYSTAL.get()), "made qubits: " + out);
            h.assertValueEqual(c.getItems().getAmountAsInt(1), 4, "two collisions, two crystals each");
            h.succeed();
        });
    }

    private static void zeroPointPowers(GameTestHelper h) {
        BlockPos z = new BlockPos(1, 1, 1);
        h.setBlock(z, ModQuantum.ZERO_POINT_GENERATOR.get().defaultBlockState());
        h.setBlock(new BlockPos(2, 1, 1), ModQuantum.PARTICLE_COLLIDER.get().defaultBlockState());
        ZeroPointBlockEntity gen = h.getBlockEntity(z, ZeroPointBlockEntity.class);
        ColliderBlockEntity sink = h.getBlockEntity(new BlockPos(2, 1, 1), ColliderBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            gen.getItems().insert(net.neoforged.neoforge.transfer.item.ItemResource.of(ModQuantum.EXOTIC_MATTER.get()), 1, tx);
            tx.commit();
        }
        h.runAfterDelay(200, () -> {
            h.assertTrue(sink.getEnergy().getAmountAsInt() > 0, "neighbour received zero-point energy");
            h.assertTrue(gen.getItems().getAmountAsInt(0) == 0, "exotic matter consumed");
            h.succeed();
        });
    }

    private static void railgunPierces(GameTestHelper h) {
        var player = h.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var a = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
        var b = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(6, 1, 1));
        a.setNoAi(true);
        b.setNoAi(true);
        h.runAfterDelay(2, () -> {
            var from = h.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 2.0, 1.5));
            var dir = b.position().add(0, 1.0, 0).subtract(from);
            com.powerfactor.weapon.RailgunItem.fire(h.getLevel(), player, from, dir, 1F);
            h.assertTrue(a.getHealth() < a.getMaxHealth() || !a.isAlive(), "first zombie hit");
            h.assertTrue(b.getHealth() < b.getMaxHealth() || !b.isAlive(), "second zombie behind it also hit");
            h.succeed();
        });
    }

    private static net.minecraft.world.entity.monster.zombie.Zombie suited(GameTestHelper h, BlockPos at, com.powerfactor.exotic.ExoticMaterial m) {
        var z = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, at);
        z.setNoAi(true);
        var set = ModQuantum.ARMOR.get(m);
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(set.get(net.minecraft.world.item.equipment.ArmorType.HELMET).get()));
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(set.get(net.minecraft.world.item.equipment.ArmorType.CHESTPLATE).get()));
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(set.get(net.minecraft.world.item.equipment.ArmorType.LEGGINGS).get()));
        z.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(set.get(net.minecraft.world.item.equipment.ArmorType.BOOTS).get()));
        return z;
    }

    /** Full exotic sets: quantum phases through the first blow, faradaenium arcs back, cortanium burns attackers. */
    private static void armorSetBonuses(GameTestHelper h) {
        var level = h.getLevel();
        var quantum = suited(h, new BlockPos(1, 1, 1), com.powerfactor.exotic.ExoticMaterial.QUANTUM);
        var fara = suited(h, new BlockPos(4, 1, 1), com.powerfactor.exotic.ExoticMaterial.FARADAENIUM);
        var cort = suited(h, new BlockPos(7, 1, 1), com.powerfactor.exotic.ExoticMaterial.CORTANIUM);
        // Separate attackers: quantum also retaliates, and a struck mob is briefly invulnerable.
        var brute = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        var brute2 = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(4, 1, 2));
        var brute3 = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(7, 1, 2));
        for (var b : List.of(brute, brute2, brute3)) b.setNoAi(true);
        h.runAfterDelay(2, () -> {
            h.assertValueEqual(com.powerfactor.exotic.ExoticArmor.fullSet(quantum), com.powerfactor.exotic.ExoticMaterial.QUANTUM, "full set seen");
            quantum.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
            h.assertTrue(com.powerfactor.exotic.ExoticArmor.fullSet(quantum) == null, "three pieces are not a set");
            quantum.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                    new ItemStack(ModQuantum.ARMOR.get(com.powerfactor.exotic.ExoticMaterial.QUANTUM).get(net.minecraft.world.item.equipment.ArmorType.HELMET).get()));
            float before = quantum.getHealth();
            quantum.hurtServer(level, level.damageSources().mobAttack(brute), 6F);
            h.assertValueEqual(quantum.getHealth(), before, "first blow phases through");
            quantum.invulnerableTime = 0;
            quantum.hurtServer(level, level.damageSources().mobAttack(brute), 6F);
            h.assertTrue(quantum.getHealth() < before, "second blow inside the cooldown lands");
            float bruteBefore = brute2.getHealth();
            fara.hurtServer(level, level.damageSources().mobAttack(brute2), 2F);
            h.assertTrue(brute2.getHealth() < bruteBefore, "dynamo arcs into the attacker");
            brute3.clearFire();
            cort.hurtServer(level, level.damageSources().mobAttack(brute3), 2F);
            h.assertTrue(brute3.getRemainingFireTicks() > 0, "crucible sets the attacker alight");
            h.succeed();
        });
    }

    private static com.powerfactor.defense.TurretBlockEntity turret(GameTestHelper h, BlockPos at, boolean rail) {
        h.setBlock(at, (rail ? com.powerfactor.registry.ModDefense.RAIL_TURRET : com.powerfactor.registry.ModDefense.PLASMA_TURRET).get().defaultBlockState());
        var t = h.getBlockEntity(at, com.powerfactor.defense.TurretBlockEntity.class);
        t.getEnergy().set(t.tier().capacity);
        return t;
    }

    /** Shoots the zombie, spares the villager standing just as close. */
    private static void plasmaTurretDefends(GameTestHelper h) {
        turret(h, new BlockPos(0, 1, 1), false);
        var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(6, 1, 1));
        var villager = h.spawn(net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(0, 1, 2));
        zombie.setNoAi(true);
        villager.setNoAi(true);
        h.runAfterDelay(80, () -> {
            h.assertTrue(zombie.getHealth() < zombie.getMaxHealth() || !zombie.isAlive(), "zombie was shot");
            h.assertValueEqual(villager.getHealth(), villager.getMaxHealth(), "villager spared");
            h.succeed();
        });
    }

    /** One charged slug passes through two zombies in a line; never targets its owner. */
    private static void railTurretPierces(GameTestHelper h) {
        var t = turret(h, new BlockPos(0, 1, 1), true);
        var owner = h.makeMockServerPlayerInLevel();
        t.setOwner(owner.getUUID());
        t.toggle(com.powerfactor.defense.TurretBlockEntity.MODE_PLAYERS);
        h.assertFalse(t.mayTarget(owner), "owner is never a target");
        var a = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(4, 1, 1));
        var b = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(7, 1, 1));
        a.setNoAi(true);
        b.setNoAi(true);
        h.runAfterDelay(100, () -> {
            h.assertTrue(a.getHealth() < a.getMaxHealth() || !a.isAlive(), "near zombie hit");
            h.assertTrue(b.getHealth() < b.getMaxHealth() || !b.isAlive(), "far zombie pierced");
            h.assertTrue(t.getEnergy().getAmountAsInt() < t.tier().capacity, "shot drew power");
            h.succeed();
        });
    }

    /** Linked pads move a player who stands still, charge for it, and never bounce them straight back. */
    private static void teleporterSends(GameTestHelper h) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(7, 1, 1);
        h.setBlock(a, ModQuantum.TELEPORTER.get().defaultBlockState());
        h.setBlock(b, ModQuantum.TELEPORTER.get().defaultBlockState());
        var padA = h.getBlockEntity(a, com.powerfactor.quantum.TeleporterBlockEntity.class);
        var padB = h.getBlockEntity(b, com.powerfactor.quantum.TeleporterBlockEntity.class);
        padA.link(net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(b)));
        padB.link(net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(a)));
        padA.getEnergy().set(com.powerfactor.quantum.TeleporterBlockEntity.CAPACITY);
        padB.getEnergy().set(com.powerfactor.quantum.TeleporterBlockEntity.CAPACITY);
        var player = h.makeMockServerPlayerInLevel();
        var start = h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1.25, 1.5));
        player.teleportTo(start.x, start.y, start.z);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        h.runAfterDelay(com.powerfactor.quantum.TeleporterBlockEntity.CHARGE_TICKS + 10, () -> {
            var at = player.position();
            var dest = h.absoluteVec(new net.minecraft.world.phys.Vec3(7.5, 1.25, 1.5));
            h.assertTrue(at.distanceTo(dest) < 0.8, "player arrived on the far pad: " + at);
            h.assertTrue(padA.getEnergy().getAmountAsInt() < com.powerfactor.quantum.TeleporterBlockEntity.CAPACITY, "jump drew power");
            h.runAfterDelay(com.powerfactor.quantum.TeleporterBlockEntity.CHARGE_TICKS + 10, () -> {
                h.assertTrue(player.position().distanceTo(dest) < 0.8, "arrivals are not bounced straight back");
                h.succeed();
            });
        });
    }

    /** Forms, ignites on a 50M FE pulse, burns hydrogen into power through its casings, then quenches dry. */
    private static void fusionIgnites(GameTestHelper h) {
        BlockPos c = new BlockPos(3, 2, 1);
        h.setBlock(c, ModQuantum.FUSION_REACTOR.get().defaultBlockState());
        var reactor = h.getBlockEntity(c, com.powerfactor.quantum.FusionReactorBlockEntity.class);
        var facing = h.getBlockState(c).getValue(MachineBlock.FACING);
        BlockPos abs = h.absolutePos(c);
        var S = com.powerfactor.quantum.FusionReactorBlockEntity.STRUCTURE;
        for (var part : S.parts()) h.getLevel().setBlock(S.worldPos(abs, facing, part), part.block().get().defaultBlockState(), 3);
        reactor.getEnergy().set(com.powerfactor.quantum.FusionReactorBlockEntity.CAPACITY);
        reactor.getTank().add(0, 2_000);
        h.runAfterDelay(90, () -> {
            h.assertTrue(reactor.formed(), "structure formed");
            var casing = S.parts().stream().filter(p -> p.block().get() == ModQuantum.FUSION_CASING.get()).findFirst().orElseThrow();
            var cap = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, S.worldPos(abs, facing, casing), null);
            h.assertTrue(cap != null && cap.getAmountAsLong() > 0, "casings expose the reactor's energy");
            h.assertTrue(reactor.getTank().amount(0) < 2_000, "burning hydrogen");
            h.assertTrue(reactor.scadaReading().status() == com.powerfactor.scada.ScadaReading.Status.RUNNING, "plasma burning");
            // Starve it: the plasma cools below ignition and quenches.
            reactor.getTank().add(0, -reactor.getTank().amount(0));
            h.runAfterDelay(80, () -> {
                h.assertTrue(reactor.scadaReading().status() == com.powerfactor.scada.ScadaReading.Status.OFF, "quenched without fuel");
                h.succeed();
            });
        });
    }

    /** A meteor dropped onto the arena floor blasts a crater and leaves Meteorite (or exotic ore) in it. */
    private static void meteorLands(GameTestHelper h) {
        var from = h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 7.5, 3.5));
        var meteor = new com.powerfactor.world.MeteorEntity(h.getLevel(), from, new net.minecraft.world.phys.Vec3(0, -1.2, 0), 1);
        h.getLevel().addFreshEntity(meteor);
        h.runAfterDelay(30, () -> {
            h.assertFalse(meteor.isAlive(), "meteor struck");
            BlockPos centre = h.absolutePos(new BlockPos(3, 0, 3));
            boolean found = false;
            for (BlockPos p : BlockPos.betweenClosed(centre.offset(-3, -6, -3), centre.offset(3, 2, 3))) {
                var st = h.getLevel().getBlockState(p);
                if (st.is(ModQuantum.METEORITE.get()) || st.is(ModQuantum.ADAMANTIUM_ORE.get()) || st.is(ModQuantum.FARADAENIUM_ORE.get())
                        || st.is(ModQuantum.QUBIT_ORE.get()) || st.is(ModQuantum.CORTANIUM_ORE.get())) {
                    found = true;
                    break;
                }
            }
            h.assertTrue(found, "meteorite core left in the crater");
            h.succeed();
        });
    }

    /** In solid deepslate the lab forms with its collider, rift spawner and loot chest; beside water it refuses. */
    private static void labGenerates(GameTestHelper h) {
        var level = h.getLevel();
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(10, 8, 10)))) {
            level.setBlock(p, Blocks.DEEPSLATE.defaultBlockState(), 2);
        }
        BlockPos origin = h.absolutePos(new BlockPos(5, 2, 5));
        var feature = com.powerfactor.registry.ModWorldgen.QUANTUM_LAB.get();
        var ctx = new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(java.util.Optional.empty(), level,
                level.getChunkSource().getGenerator(), level.getRandom(), origin,
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE);
        h.assertTrue(feature.place(ctx), "lab placed in solid rock");
        h.assertBlockPresent(ModQuantum.PARTICLE_COLLIDER.get(), new BlockPos(5, 3, 5));
        h.assertBlockPresent(Blocks.SPAWNER, new BlockPos(8, 3, 8));
        h.assertBlockPresent(Blocks.CHEST, new BlockPos(2, 3, 2));
        var chest = h.getBlockEntity(new BlockPos(2, 3, 2), ChestBlockEntity.class);
        h.assertTrue(com.powerfactor.world.QuantumLabFeature.LOOT.equals(chest.getLootTable()), "researcher's chest has the lab loot table");
        // A second lab where water seeps in is refused.
        level.setBlock(h.absolutePos(new BlockPos(5, 8, 5)), Blocks.WATER.defaultBlockState(), 2);
        var wet = new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(java.util.Optional.empty(), level,
                level.getChunkSource().getGenerator(), level.getRandom(), h.absolutePos(new BlockPos(5, 4, 5)),
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE);
        h.assertFalse(feature.place(wet), "no lab where water would flood it");
        h.succeed();
    }

    /** Moves only the filtered item from the chest behind to the chest in front, and loses nothing. */
    private static void robotArmMoves(GameTestHelper h) {
        h.setBlock(new BlockPos(0, 1, 1), Blocks.CHEST);
        h.setBlock(new BlockPos(1, 1, 1), ModQuantum.ROBOT_ARM.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.EAST));
        h.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        var src = h.getBlockEntity(new BlockPos(0, 1, 1), ChestBlockEntity.class);
        var dst = h.getBlockEntity(new BlockPos(2, 1, 1), ChestBlockEntity.class);
        var arm = h.getBlockEntity(new BlockPos(1, 1, 1), com.powerfactor.logistics.RobotArmBlockEntity.class);
        src.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        src.setItem(1, new ItemStack(Items.DIRT, 5));
        arm.getEnergy().set(com.powerfactor.logistics.RobotArmBlockEntity.CAPACITY);
        arm.setFilter(new ItemStack(Items.COBBLESTONE));
        h.runAfterDelay(220, () -> {
            h.assertValueEqual(dst.countItem(Items.COBBLESTONE), 40, "all the cobblestone delivered");
            h.assertValueEqual(src.countItem(Items.DIRT), 5, "dirt left alone by the filter");
            h.assertValueEqual(dst.countItem(Items.DIRT), 0, "no dirt delivered");
            h.assertTrue(arm.getEnergy().getAmountAsInt() < com.powerfactor.logistics.RobotArmBlockEntity.CAPACITY, "moves cost power");
            h.succeed();
        });
    }

    /** Collects only at night under open sky, draws power doing it, and yields Cosmic Dust. */
    private static void orbitalCollector(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 1);
        h.setBlock(p, ModQuantum.ORBITAL_COLLECTOR.get().defaultBlockState());
        var be = h.getBlockEntity(p, com.powerfactor.quantum.OrbitalCollectorBlockEntity.class);
        be.getEnergy().set(com.powerfactor.quantum.OrbitalCollectorBlockEntity.CAPACITY);
        h.setTime(18_000);
        h.runAfterDelay(5, () -> {
            h.assertValueEqual(be.state(), com.powerfactor.quantum.OrbitalCollectorBlockEntity.WORKING, "collecting at midnight under open sky");
            h.assertTrue(be.getEnergy().getAmountAsInt() < com.powerfactor.quantum.OrbitalCollectorBlockEntity.CAPACITY, "collection draws power");
            be.completePass(h.getLevel());
            h.assertTrue(be.output().countItem(ModQuantum.COSMIC_DUST.get()) >= 2, "a pass yields cosmic dust");
            h.setBlock(p.above(), Blocks.STONE);
            h.runAfterDelay(2, () -> {
                h.assertValueEqual(be.state(), com.powerfactor.quantum.OrbitalCollectorBlockEntity.NO_SKY, "a roof stops it");
                h.setTime(6_000);
                h.succeed();
            });
        });
    }

    private static com.powerfactor.vehicle.HoverBikeEntity bike(GameTestHelper h, double x, double z, int energy) {
        var bike = new com.powerfactor.vehicle.HoverBikeEntity(com.powerfactor.registry.ModEntities.HOVER_BIKE.get(), h.getLevel());
        var stack = new ItemStack(com.powerfactor.registry.ModItems.HOVER_BIKE.get());
        stack.set(com.powerfactor.registry.ModComponents.ENERGY.get(), energy);
        bike.restore(stack);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(x, 4, z));
        bike.setPos(at.x, at.y, at.z);
        bike.setYRot(0);
        h.getLevel().addFreshEntity(bike);
        return bike;
    }

    /** A charged bike rides its cushion; a flat one settles; a rider's throttle carries it forward. */
    private static void hoverBike(GameTestHelper h) {
        var charged = bike(h, 1.5, 1.5, com.powerfactor.vehicle.HoverBikeEntity.CAPACITY);
        var flat = bike(h, 5.5, 1.5, 0);
        h.runAfterDelay(60, () -> {
            // Clearance is measured straight down to the first surface, as the bike itself does.
            h.assertTrue(charged.clearance() > 0.8 && charged.clearance() < 1.5, "charged bike rides its cushion: " + charged.clearance());
            h.assertTrue(flat.clearance() < 0.2, "flat bike settles on the ground: " + flat.clearance());
            var rider = h.makeMockServerPlayerInLevel();
            rider.startRiding(charged);
            double z0 = charged.getZ();
            for (int i = 0; i < 20; i++) {
                h.runAfterDelay(1 + i, () -> charged.input(rider, 1, 0, false, false, false));
            }
            h.runAfterDelay(24, () -> {
                h.assertTrue(charged.getZ() - z0 > 1.5, "throttle drives it forward: " + (charged.getZ() - z0));
                h.assertTrue(charged.charge() < com.powerfactor.vehicle.HoverBikeEntity.CAPACITY, "driving draws charge");
                h.succeed();
            });
        });
    }

    /** Items in the column float up toward its top; unpowered, they fall back. */
    private static void gravityLift(GameTestHelper h) {
        BlockPos pad = new BlockPos(3, 1, 3);
        h.setBlock(pad, ModQuantum.GRAVITY_LIFT.get().defaultBlockState());
        var lift = h.getBlockEntity(pad, com.powerfactor.logistics.GravityLiftBlockEntity.class);
        lift.getEnergy().set(com.powerfactor.logistics.GravityLiftBlockEntity.CAPACITY);
        var item = h.spawnItem(Items.DIAMOND, new BlockPos(3, 2, 3));
        double y0 = item.getY();
        h.runAfterDelay(30, () -> {
            h.assertTrue(item.getY() - y0 > 2.5, "item carried up the column: " + (item.getY() - y0));
            h.assertTrue(lift.getEnergy().getAmountAsInt() < com.powerfactor.logistics.GravityLiftBlockEntity.CAPACITY, "lifting costs power");
            h.succeed();
        });
    }

    /** AND needs both side inputs; the output reaches the block in front; the latch holds until reset. */
    private static void logicController(GameTestHelper h) {
        // Facing south: front is +Z, left (clockwise of south) is west, right is east.
        BlockPos c = new BlockPos(4, 1, 1);
        h.setBlock(c, ModQuantum.LOGIC_CONTROLLER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.SOUTH));
        var ctl = h.getBlockEntity(c, com.powerfactor.device.LogicControllerBlockEntity.class);
        BlockPos left = c.west(), right = c.east();
        h.setBlock(left, Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(2, () -> {
            h.assertFalse(ctl.output(), "AND with one input is off");
            h.setBlock(right, Blocks.REDSTONE_BLOCK);
            h.runAfterDelay(2, () -> {
                h.assertTrue(ctl.output(), "AND with both inputs is on");
                h.assertValueEqual(h.getLevel().getSignal(h.absolutePos(c), Direction.NORTH), 15, "signal leaves the front");
                h.assertTrue(h.getLevel().hasNeighborSignal(h.absolutePos(c.south())), "the block in front is powered");
                ctl.button(com.powerfactor.device.LogicControllerBlockEntity.BUTTON_PROGRAM + 4);   // LATCH
                h.setBlock(right, Blocks.AIR);
                h.runAfterDelay(2, () -> {
                    h.assertTrue(ctl.output(), "latch set by the left input");
                    h.setBlock(left, Blocks.AIR);
                    h.runAfterDelay(2, () -> {
                        h.assertTrue(ctl.output(), "latch holds with no inputs");
                        h.setBlock(right, Blocks.REDSTONE_BLOCK);
                        h.runAfterDelay(2, () -> {
                            h.assertFalse(ctl.output(), "right input resets the latch");
                            h.succeed();
                        });
                    });
                });
            });
        });
    }

    /** A piloted mech walks on throttle, climbs on its jets, and a hard landing hurts creatures nearby. */
    private static void mechSuit(GameTestHelper h) {
        var mech = new com.powerfactor.vehicle.MechEntity(com.powerfactor.registry.ModEntities.MECH.get(), h.getLevel());
        var stack = new ItemStack(com.powerfactor.registry.ModItems.MECH.get());
        stack.set(com.powerfactor.registry.ModComponents.ENERGY.get(), com.powerfactor.vehicle.MechEntity.CAPACITY);
        mech.restore(stack);
        var at = h.absoluteVec(new net.minecraft.world.phys.Vec3(3.5, 1, 1.5));
        mech.setPos(at.x, at.y, at.z);
        mech.setYRot(0);
        h.getLevel().addFreshEntity(mech);
        var pilot = h.makeMockServerPlayerInLevel();
        pilot.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);   // creative pilots run free
        h.runAfterDelay(5, () -> {
            pilot.startRiding(mech);
            double z0 = mech.getZ();
            for (int i = 0; i < 16; i++) h.runAfterDelay(1 + i, () -> mech.input(pilot, 1, 0, false, false));
            h.runAfterDelay(20, () -> {
                h.assertTrue(mech.getZ() - z0 > 1.5, "walks forward: " + (mech.getZ() - z0));
                double y0 = mech.getY();
                for (int i = 0; i < 14; i++) h.runAfterDelay(1 + i, () -> mech.input(pilot, 0, 0, false, true));
                h.runAfterDelay(16, () -> {
                    h.assertTrue(mech.getY() - y0 > 2, "jump-jets lift it: " + (mech.getY() - y0));
                    var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 5));
                    zombie.setNoAi(true);
                    h.runAfterDelay(40, () -> {
                        h.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "the landing stomp hurt the zombie");
                        h.assertTrue(mech.charge() < com.powerfactor.vehicle.MechEntity.CAPACITY, "walking and jets draw charge");
                        h.succeed();
                    });
                });
            });
        });
    }

    /** Raises a tether segment from its slot on power; complete and under open sky, it supplies power. */
    private static void spaceElevator(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 1);
        h.setBlock(p, ModQuantum.SPACE_ELEVATOR.get().defaultBlockState());
        var anchor = h.getBlockEntity(p, com.powerfactor.quantum.SpaceElevatorBlockEntity.class);
        anchor.getEnergy().set(com.powerfactor.quantum.SpaceElevatorBlockEntity.CAPACITY);
        anchor.tetherSlot().setItem(0, new ItemStack(ModQuantum.NANOTUBE_TETHER.get(), 2));
        h.runAfterDelay(com.powerfactor.quantum.SpaceElevatorBlockEntity.SPOOL_TICKS + 5, () -> {
            h.assertValueEqual(anchor.segments(), 1, "one segment raised");
            h.assertValueEqual(anchor.tetherSlot().getItem(0).getCount(), 1, "one tether used");
            h.assertFalse(anchor.complete(), "not yet complete");
            anchor.setSegmentsForTest(com.powerfactor.quantum.SpaceElevatorBlockEntity.SEGMENTS);
            anchor.getEnergy().set(0);
            h.runAfterDelay(3, () -> {
                h.assertTrue(anchor.complete(), "complete at 64 segments");
                h.assertTrue(anchor.getEnergy().getAmountAsInt() > 0, "the station beams power down");
                h.succeed();
            });
        });
    }

    /** A zombie inside the dome is shoved out past the surface; the repulse costs power. */
    private static void shieldRepels(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, com.powerfactor.registry.ModDefense.SHIELD_GENERATOR.get().defaultBlockState());
        var shield = h.getBlockEntity(p, com.powerfactor.defense.ShieldGeneratorBlockEntity.class);
        shield.setRadius(4);
        shield.getEnergy().set(com.powerfactor.defense.ShieldGeneratorBlockEntity.CAPACITY);
        var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
        // (AI on: NoAI mobs ignore velocity, so they could never be shoved.)
        var centre = net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(p));
        h.runAfterDelay(25, () -> {
            h.assertTrue(zombie.position().distanceTo(centre) > 3.5, "zombie pushed out of the dome: " + zombie.position().distanceTo(centre));
            h.assertTrue(shield.hits() > 0, "a repulse was counted");
            h.succeed();
        });
    }

    private static void starforgeFuses(GameTestHelper h) {
        BlockPos c = new BlockPos(3, 1, 2);
        h.setBlock(c, ModQuantum.STARFORGE.get().defaultBlockState());
        var forge = h.getBlockEntity(c, com.powerfactor.quantum.StarforgeBlockEntity.class);
        var facing = h.getBlockState(c).getValue(MachineBlock.FACING);
        BlockPos abs = h.absolutePos(c);
        for (var part : com.powerfactor.quantum.StarforgeBlockEntity.STRUCTURE.parts()) {
            h.getLevel().setBlock(com.powerfactor.quantum.StarforgeBlockEntity.STRUCTURE.worldPos(abs, facing, part), part.block().get().defaultBlockState(), 3);
        }
        forge.getEnergy().set(com.powerfactor.quantum.StarforgeBlockEntity.CAPACITY);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var ench = h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        pick.enchant(ench.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 4);
        ItemStack axe = new ItemStack(Items.DIAMOND_AXE);
        axe.enchant(ench.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), 5);
        forge.slots().setItem(0, pick);
        forge.slots().setItem(1, axe);
        forge.slots().setItem(2, new ItemStack(Items.DIAMOND_SHOVEL));
        forge.slots().setItem(3, new ItemStack(Items.DIAMOND_BLOCK));
        // Forms within 20 ticks, then 10M FE at 400k FE/t is 25 ticks.
        h.runAfterDelay(70, () -> {
            ItemStack out = forge.slots().getItem(4);
            h.assertTrue(out.is(ModQuantum.DIAMOND_PAXEL.get()), "forged " + out);
            h.assertValueEqual(net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                    ench.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY), out), 5, "kept the best enchantment");
            h.assertTrue(forge.slots().getItem(0).isEmpty() && forge.slots().getItem(3).isEmpty(), "inputs consumed");
            h.succeed();
        });
    }

    private QuantumGameTests() {}
}
