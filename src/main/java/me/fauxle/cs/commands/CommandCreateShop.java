package me.fauxle.cs.commands;

import me.fauxle.cs.ChestShopMeta;
import me.fauxle.cs.ShopPlugin;
import me.fauxle.cs.Utils;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandCreateShop implements TabExecutor {

    private final ShopPlugin plugin;

    public CommandCreateShop(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: Only players can run this command.");
            return true;
        }

        if (args.length != 3) {
            sender.sendMessage(ChatColor.DARK_GRAY + "Example of creating a ChestShop which only sells 5 of an item for 50 "
                    + plugin.getEconomy().currencyNamePlural() + ": /" + command.getName() + " 5 50 -1");
            sender.sendMessage(ChatColor.DARK_GRAY + "Example of creating a ChestShop which buys 10 of an item for 100 "
                    + plugin.getEconomy().currencyNamePlural() + " and sells 10 of an item for 50 "
                    + plugin.getEconomy().currencyNamePlural() + ": /" + command.getName() + " 10 100 50");
            sender.sendMessage(ChatColor.DARK_GRAY + "Negative numbers in the price will disable the option" +
                    " (i.e. if sell price is -1, the ChestShop will not sell items).");
            sender.sendMessage(ChatColor.DARK_GRAY + "The item of the ChestShop will be whatever the first item is in the chest. " +
                    "Or if nothing is in the chest, it will be what you are holding in your main hand.");
            sender.sendMessage(ChatColor.DARK_RED + "Error: Invalid command usage.");
            sender.sendMessage(ChatColor.DARK_RED + "Usage: /" + command.getName() + " <amount> <buy price> <sell price>");
            return true;
        }

        int buyPrice, sellPrice, amount;

        try {
            amount = Integer.parseInt(args[0]);
            buyPrice = Integer.parseInt(args[1]);
            sellPrice = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: Invalid number format provided.");
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: Amount cannot be less than 1.");
            return true;
        }

        if (buyPrice == -1 && sellPrice == -1) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: The ChestShop must be at selling or buying an item.");
            return true;
        }

        Block block = p.getTargetBlock(null, 5);

        if (!Utils.isSign(block.getType())) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: You need to be looking directly at a sign to edit.");
            return true;
        }

        Container shopContainer = Utils.getShopContainer(block);

        if (shopContainer == null) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: Could not find any chest nearby. " +
                    "Please ensure that the sign is attached to the chest or is directly above it.");
            return true;
        }

        ItemStack item = Arrays.stream(shopContainer.getInventory().getStorageContents())
                .filter(i -> i != null && !i.getType().isAir())
                .findFirst()
                .orElse(null);

        if (item == null)
            item = p.getInventory().getItemInMainHand();

        if (item.getType().isAir()) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: Could not find any item in the Chest or your main hand.");
            return true;
        }

        item = item.clone(); // Ensure we are working with a clone
        item.setAmount(amount); // Correct the amount expected by the owner

        ChestShopMeta shopMeta = new ChestShopMeta(
                p.getUniqueId(),
                (buyPrice == -1 ? null : buyPrice),
                (sellPrice == -1 ? null : sellPrice),
                item
        );

        Sign sign = (Sign) block.getState();

        sign.setLine(0, p.getName());
        sign.setLine(1, String.valueOf(shopMeta.getItem().getAmount()));
        if (shopMeta.isBuying() && shopMeta.isSelling()) {
            sign.setLine(2, "B " + shopMeta.getBuyPrice() + " : " + shopMeta.getSellPrice() + " S");
        } else if (shopMeta.isBuying()) {
            sign.setLine(2, "B " + shopMeta.getBuyPrice());
        } else if (shopMeta.isSelling()) {
            sign.setLine(2, "S " + shopMeta.getSellPrice());
        }
        sign.setLine(3, WordUtils.capitalizeFully(shopMeta.getItem().getType().toString().replace('_', ' ')));

        // Never knew there were so many fields
        // We call this event mainly for region permission checking
        // I guess the block logger can log it as a block place, it's cool
        // No SignEditEvent because it'll conflict with the SignEditEvent in PlayerListener
        BlockPlaceEvent event = new BlockPlaceEvent(block, block.getState(), shopContainer.getBlock(),
                p.getInventory().getItemInMainHand(), p, true, EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(event);

        // Arm swing event called mainly for anti-cheat plugin
        plugin.getServer().getPluginManager().callEvent(new PlayerAnimationEvent(p));

        if (event.isCancelled())
            return true;

        sign.update();
        plugin.setShopMeta(block, shopMeta, true);
        p.sendMessage(ChatColor.GREEN + "[Shop] " + ChatColor.AQUA + "ChestShop created!");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        switch (args.length) {
            case 1:
                return Collections.singletonList("<amount>");
            case 2:
                return Collections.singletonList("<buy price (-1 to disable)>");
            case 3:
                return Collections.singletonList("<sell price (-1 to disable)>");
            default:
                return Collections.emptyList();
        }
    }

}
