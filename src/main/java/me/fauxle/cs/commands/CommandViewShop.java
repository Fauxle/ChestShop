package me.fauxle.cs.commands;

import me.fauxle.cs.ChestShopMeta;
import me.fauxle.cs.ShopPlugin;
import me.fauxle.cs.Utils;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class CommandViewShop implements TabExecutor {

    private final ShopPlugin plugin;

    public CommandViewShop(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: Only players can run this command.");
            return true;
        }

        Player p = (Player) sender;

        Block block = p.getTargetBlock(null, 5);

        if (!Utils.isSign(block.getType())) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: You need to be looking directly at a sign.");
            return true;
        }

        ChestShopMeta shopMeta = plugin.getShopMeta(block);

        if (shopMeta == null) {
            sender.sendMessage(ChatColor.DARK_RED + "Error: This sign is not associated with a ChestShop.");
            return true;
        }

        p.sendMessage(ChatColor.GOLD + "Amount: " + ChatColor.AQUA + shopMeta.getAmount());
        p.sendMessage(ChatColor.GOLD + "Owner: " + ChatColor.AQUA + plugin.getServer().getOfflinePlayer(shopMeta.getOwner()).getName());
        p.sendMessage(ChatColor.GOLD + "Buy: " + ChatColor.AQUA + (shopMeta.isBuying() ? shopMeta.getBuyPrice() : "N/A") + ChatColor.GOLD +
                " | Sell: " + ChatColor.AQUA + (shopMeta.isSelling() ? shopMeta.getSellPrice() : "N/A"));
        p.spigot().sendMessage(new ComponentBuilder("Item: ").color(net.md_5.bungee.api.ChatColor.GOLD)
                .append(String.valueOf(shopMeta.getAmount())).color(net.md_5.bungee.api.ChatColor.AQUA)
                .append(" x ").color(net.md_5.bungee.api.ChatColor.DARK_AQUA)
                .append("[" + WordUtils.capitalizeFully(shopMeta.getItem().getType().toString().replace('_', ' ') + "]")
                ).color(net.md_5.bungee.api.ChatColor.BLUE).event(
                        new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                                Utils.formattedItemStack(shopMeta.getItem())
                        )).create());

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

}
