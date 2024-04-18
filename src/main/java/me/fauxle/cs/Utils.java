package me.fauxle.cs;

import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private Utils() {
    }

    public static Container getShopContainer(Block block) {
        BlockData blockData = block.getBlockData();
        if (blockData instanceof WallSign) {
            WallSign sign = (WallSign) blockData;
            Block attachedBlock = block.getRelative(sign.getFacing().getOppositeFace());
            if (isSupportedContainer(attachedBlock.getType())) {
                return (Container) attachedBlock.getState();
            }
        }
        Block below = block.getRelative(BlockFace.DOWN);
        if (isSupportedContainer(below.getType())) {
            return ((Container) below.getState());
        }
        return null;
    }

    private static boolean isSupportedContainer(Material type) {
        return type == Material.CHEST || type == Material.TRAPPED_CHEST;
    }

    public static boolean isSign(Material type) {
        switch (type) {
            case OAK_SIGN:
            case ACACIA_SIGN:
            case SPRUCE_SIGN:
            case SPRUCE_WALL_SIGN:
            case BIRCH_SIGN:
            case CRIMSON_SIGN:
            case ACACIA_WALL_SIGN:
            case BIRCH_WALL_SIGN:
            case CRIMSON_WALL_SIGN:
            case JUNGLE_SIGN:
            case DARK_OAK_SIGN:
            case DARK_OAK_WALL_SIGN:
            case JUNGLE_WALL_SIGN:
            case OAK_WALL_SIGN:
            case WARPED_SIGN:
            case WARPED_WALL_SIGN:
                return true;
            default:
                return false;
        }
    }

    public static Item formattedItemStack(ItemStack item) {

        item = item.clone();

        ItemMeta meta = item.getItemMeta();

        if (meta instanceof Damageable) {

            Damageable damageable = ((Damageable) meta);

            if (damageable.hasDamage()) {

                List<String> lore = meta.getLore();
                if (lore == null)
                    lore = new ArrayList<>();

                double actualDur = damageable.getDamage();
                double maxDur = item.getType().getMaxDurability();

                double totalDur = actualDur / maxDur;
                int durability = (int) Math.floor((1D - totalDur) * 100D);
                lore.add(ChatColor.LIGHT_PURPLE + ChatColor.ITALIC.toString() + "[DURABILITY LEFT]: " + durability + "%");

                meta.setLore(lore);
                item.setItemMeta(meta);

            }

        }

        try {

            String version = getVersion();

            Class<?> nmsStackClazz = Class.forName("net.minecraft.world.item.ItemStack");//Class.forName("net.minecraft.server." + version + ".ItemStack");
            Class<?> craftStackClazz = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nbtClazz = Class.forName("net.minecraft.nbt.NBTTagCompound");//Class.forName("net.minecraft.server." + version + ".NBTTagCompound");

            Method asNMSCopy = craftStackClazz.getDeclaredMethod("asNMSCopy", ItemStack.class);
            Method stackSaveNbt = nmsStackClazz.getDeclaredMethod("save", nbtClazz);
            Method nbtGet = nbtClazz.getDeclaredMethod("get", String.class);
            Constructor<?> newTag = nbtClazz.getConstructor();

            Object nmsStack = asNMSCopy.invoke(null, item);
            Object tag = newTag.newInstance();
            stackSaveNbt.invoke(nmsStack, tag);

            Object tagNbtBase = nbtGet.invoke(tag, "tag");

            if (tagNbtBase != null)
                return new Item(item.getType().getKey().getKey(), item.getAmount(), ItemTag.ofNbt(tagNbtBase.toString()));

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new Item(item.getType().getKey().getKey(), item.getAmount(), ItemTag.ofNbt(""));
    }

    /**
     * Gets the version string for NMS & OBC class paths
     *
     * @return The version string of OBC and NMS packages
     */
    private static String getVersion() {
        String name = Bukkit.getServer().getClass().getPackage().getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

}
