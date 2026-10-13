import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Java source launcher: enable cheats only in the launcher's dedicated flat test world. */
class EnableTestCheats {
    record Tag(int type, Object value) {}
    record ListTag(int elementType, List<Tag> values) {}
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: java tools/EnableTestCheats.java <test-world-folder>");
        Path file = Path.of(args[0], "level.dat"); String name; Tag root;
        try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            int type = in.readUnsignedByte(); name = in.readUTF(); root = read(in, type, 0);
        }
        var data = compound(compound(root).get("Data"));
        Map<String, Tag> settings;
        Path settingsFile = file.getParent().resolve("data/minecraft/world_gen_settings.dat");
        if (Files.exists(settingsFile)) {
            try (var in = new DataInputStream(new GZIPInputStream(Files.newInputStream(settingsFile)))) {
                int type = in.readUnsignedByte(); in.readUTF(); settings = compound(compound(read(in, type, 0)).get("data"));
            }
        } else settings = compound(data.get("WorldGenSettings"));
        var dimensions = compound(settings.get("dimensions"));
        var overworld = compound(dimensions.get("minecraft:overworld"));
        var generator = compound(overworld.get("generator"));
        if (!"minecraft:flat".equals(generator.get("type").value())) throw new IOException("Test world is not flat; no files were changed.");
        data.put("allowCommands", new Tag(1, (byte) 1));
        data.put("GameType", new Tag(3, 1));
        Path backup = file.resolveSibling("level.dat.before-test-cheats");
        if (!Files.exists(backup)) Files.copy(file, backup);
        Path temp = file.resolveSibling("level.dat.powerfactor.tmp");
        try (var out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(temp)))) {
            out.writeByte(root.type()); out.writeUTF(name); write(out, root);
        }
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        System.out.println("Flat test world ready: Creative default, cheats enabled. Use /gamemode survival for testing.");
    }
    @SuppressWarnings("unchecked") static Map<String, Tag> compound(Tag tag) throws IOException {
        if (tag == null || tag.type() != 10) throw new IOException("Expected NBT compound in test world.");
        return (Map<String, Tag>) tag.value();
    }
    static int length(DataInputStream in) throws IOException {
        int n = in.readInt(); if (n < 0 || n > 16_777_216) throw new IOException("Invalid NBT length"); return n;
    }
    static Tag read(DataInputStream in, int type, int depth) throws IOException {
        if (depth > 512) throw new IOException("NBT nesting too deep");
        Object value = switch (type) {
            case 1 -> in.readByte(); case 2 -> in.readShort(); case 3 -> in.readInt(); case 4 -> in.readLong();
            case 5 -> in.readFloat(); case 6 -> in.readDouble(); case 8 -> in.readUTF();
            case 7 -> { int n = length(in); byte[] a = new byte[n]; in.readFully(a); yield a; }
            case 9 -> { int t = in.readUnsignedByte(), n = length(in); var a = new ArrayList<Tag>(n); for (int i = 0; i < n; i++) a.add(read(in, t, depth + 1)); yield new ListTag(t, a); }
            case 10 -> { var map = new LinkedHashMap<String, Tag>(); int t; while ((t = in.readUnsignedByte()) != 0) { String k = in.readUTF(); map.put(k, read(in, t, depth + 1)); } yield map; }
            case 11 -> { int[] a = new int[length(in)]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); yield a; }
            case 12 -> { long[] a = new long[length(in)]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); yield a; }
            default -> throw new IOException("Unsupported NBT type " + type);
        };
        return new Tag(type, value);
    }
    static void write(DataOutputStream out, Tag tag) throws IOException {
        switch (tag.type()) {
            case 1 -> out.writeByte((byte) tag.value()); case 2 -> out.writeShort((short) tag.value()); case 3 -> out.writeInt((int) tag.value());
            case 4 -> out.writeLong((long) tag.value()); case 5 -> out.writeFloat((float) tag.value()); case 6 -> out.writeDouble((double) tag.value());
            case 8 -> out.writeUTF((String) tag.value());
            case 7 -> { byte[] a = (byte[]) tag.value(); out.writeInt(a.length); out.write(a); }
            case 9 -> { var list = (ListTag) tag.value(); out.writeByte(list.elementType()); out.writeInt(list.values().size()); for (var t : list.values()) write(out, t); }
            case 10 -> { for (var entry : compound(tag).entrySet()) { out.writeByte(entry.getValue().type()); out.writeUTF(entry.getKey()); write(out, entry.getValue()); } out.writeByte(0); }
            case 11 -> { int[] a = (int[]) tag.value(); out.writeInt(a.length); for (int v : a) out.writeInt(v); }
            case 12 -> { long[] a = (long[]) tag.value(); out.writeInt(a.length); for (long v : a) out.writeLong(v); }
            default -> throw new IOException("Unsupported NBT type " + tag.type());
        }
    }
}
