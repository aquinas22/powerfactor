package com.powerfactor.powernet;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

/** One wireless power network: its name, colour, owner and who else may use it. Runtime stats live alongside. */
public final class PowerNet {
    public enum Access { PUBLIC, PRIVATE, PASSWORD }

    public enum Role { USER, ADMIN, OWNER }

    /** Wireless charging from the controller: which parts of members' inventories it fills. */
    public static final int CHARGE_HOTBAR = 1, CHARGE_ARMOR = 2, CHARGE_OFFHAND = 4, CHARGE_INVENTORY = 8;

    public final int id;
    public String name;
    public int color;
    public UUID owner;
    public Access access = Access.PRIVATE;
    public String password = "";
    public final Map<UUID, Role> members = new HashMap<>();
    public int charging;

    // ---------------------------------------------------------------- runtime (not saved)
    /** Last tick's flows (FE/t) and totals. */
    public long input, output, charged, stored, capacity;
    /** One sample a second for the last minute: input, output, stored. */
    public final long[][] history = new long[3][60];
    public int historyHead;
    long accIn, accOut;
    int accTicks;

    public PowerNet(int id, String name, int color, UUID owner) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.owner = owner;
    }

    public Role role(Player player) {
        return role(player.getUUID());
    }

    public Role role(UUID who) {
        if (who.equals(owner)) return Role.OWNER;
        return members.get(who);
    }

    /** May this player connect devices to the network (or see it in the list)? */
    public boolean canUse(Player player) {
        return access == Access.PUBLIC || role(player) != null || player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public boolean canEdit(Player player) {
        Role r = role(player);
        return r == Role.OWNER || r == Role.ADMIN || player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    void sample() {
        int h = historyHead = (historyHead + 1) % 60;
        history[0][h] = accTicks == 0 ? 0 : accIn / accTicks;
        history[1][h] = accTicks == 0 ? 0 : accOut / accTicks;
        history[2][h] = stored;
        accIn = accOut = accTicks = 0;
    }

    private record Member(UUID id, Role role) {
        static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Member::id),
                Codec.STRING.xmap(Role::valueOf, Role::name).fieldOf("role").forGetter(Member::role)).apply(i, Member::new));
    }

    public static final Codec<PowerNet> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(n -> n.id),
            Codec.STRING.fieldOf("name").forGetter(n -> n.name),
            Codec.INT.fieldOf("color").forGetter(n -> n.color),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(n -> n.owner),
            Codec.STRING.xmap(Access::valueOf, Access::name).optionalFieldOf("access", Access.PRIVATE).forGetter(n -> n.access),
            Codec.STRING.optionalFieldOf("password", "").forGetter(n -> n.password),
            Member.CODEC.listOf().optionalFieldOf("members", List.of()).forGetter(n -> n.members.entrySet().stream().map(e -> new Member(e.getKey(), e.getValue())).toList()),
            Codec.INT.optionalFieldOf("charging", 0).forGetter(n -> n.charging)
    ).apply(i, (id, name, color, owner, access, password, members, charging) -> {
        PowerNet n = new PowerNet(id, name, color, owner);
        n.access = access;
        n.password = password;
        for (Member m : members) n.members.put(m.id(), m.role());
        n.charging = charging;
        return n;
    }));
}
