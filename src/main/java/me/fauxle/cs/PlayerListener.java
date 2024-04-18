package me.fauxle.cs;

import me.fauxle.econ.api.EconomyResponse;
import org.apache.commons.lang.WordUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class PlayerListener implements Listener {

    private final ShopPlugin plugin;

    public PlayerListener(ShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignEdit(SignChangeEvent event) {
        // If we edit a shop sign, delete the shop meta (de-active the ChestShop)
        if (plugin.getShopMeta(event.getBlock()) != null) {
            plugin.deleteShopMeta(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_BLOCK) {

            if (event.getClickedBlock() == null)
                return;

            if (!Utils.isSign(event.getClickedBlock().getType()))
                return;

            ChestShopMeta shopMeta = plugin.getShopMeta(event.getClickedBlock());

            if (shopMeta == null)
                return;

            event.setCancelled(true); // Cancel the interaction

            Container shopContainer = Utils.getShopContainer(event.getClickedBlock());

            if (shopContainer == null) {
                event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: Could not find the chest for this ChestShop.");
                return;
            }

            Inventory shopInv = shopContainer.getInventory();

            String itemName = WordUtils.capitalizeFully(shopMeta.getItem().getType().toString().replace('_', ' '));
            String transactionDescription = "ChestShop " + shopMeta.getAmount() + " " + itemName + " (" +
                    event.getClickedBlock().getWorld().getName() + ", " +
                    event.getClickedBlock().getX() + ", " +
                    event.getClickedBlock().getY() + ", " +
                    event.getClickedBlock().getZ() + ")";

            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {

                if (!shopMeta.isSelling()) {
                    event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: This ChestShop is not configured for selling items to it.");
                    return;
                }

                Inventory playerInv = event.getPlayer().getInventory();

                if (!playerInv.containsAtLeast(shopMeta.getItem(), shopMeta.getAmount())) {
                    event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: " + ChatColor.RED + "You do not have the item(s) required to sell.");
                    return;
                }

                EconomyResponse withdrawTransaction = plugin.getEconomy().withdraw(
                        shopMeta.getOwner(),
                        shopMeta.getSellPrice(),
                        transactionDescription
                );

                if (withdrawTransaction.transactionSuccess()) {

                    Map<Integer, ItemStack> shopInvAddResult = shopInv.addItem(shopMeta.getItem().clone());

                    if (shopInvAddResult.isEmpty()) {
                        // Successfully added the item to sell into the shop

                        if (playerInv.removeItem(shopMeta.getItem().clone()).isEmpty()) {
                            // Successfully removed the item from the player's inventory

                            EconomyResponse depositTransaction = plugin.getEconomy().deposit(
                                    event.getPlayer().getUniqueId(),
                                    withdrawTransaction.getAmount(),
                                    transactionDescription
                            );

                            if (depositTransaction.transactionSuccess()) {

                                event.getPlayer().sendMessage(ChatColor.DARK_GREEN + "[Shop] " + ChatColor.AQUA + "You sold " + shopMeta.getAmount() + " "
                                        + itemName + " for " + depositTransaction.getAmount() + " " +
                                        ((depositTransaction.getAmount() == 1) ? plugin.getEconomy().currencyNameSingular()
                                                : plugin.getEconomy().currencyNamePlural()) + "!");

                            } else {

                                event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: Failed to deposit funds - " + depositTransaction.getErrorMessage());

                            }

                        } else {
                            // This shouldn't really be possible because we did containsAtLeast() check earlier
                            event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: Failed to remove the item from your inventory.");
                            shopInv.removeItem(shopMeta.getItem().clone());
                        }

                    } else {

                        // Would be easier if there was a canAdd() method implemented, oh well
                        int amountAttemptedAdd = shopMeta.getAmount();
                        int amountActuallyAdded = shopInvAddResult.values().stream().mapToInt(ItemStack::getAmount).sum();
                        int difference = amountAttemptedAdd - amountActuallyAdded;

                        if (difference > 0) {

                            ItemStack item = shopMeta.getItem().clone();
                            item.setAmount(difference);
                            if (!shopInv.removeItem(item).isEmpty()) {
                                plugin.getLogger().severe("Failed to remove out of stock ChestShop items! Item duplication might be possible!");
                            }

                        }

                        event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: " + ChatColor.RED + "ChestShop is full.");

                    }

                } else {
                    event.getPlayer().sendMessage(ChatColor.DARK_RED + "Transaction error with ChestShop owner: " + withdrawTransaction.getErrorMessage());
                }

            } else {

                if (!shopMeta.isBuying()) {
                    event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: This ChestShop is not configured for buying items from it.");
                    return;
                }

                //Bukkit.broadcastMessage("Chest contains at least " + item.getAmount() + " " + item.getType() + "?");

                if (!shopInv.containsAtLeast(shopMeta.getItem(), shopMeta.getAmount())) {
                    event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: " + ChatColor.RED + "ChestShop is out of stock.");
                    return;
                }

                EconomyResponse withdrawTransaction = plugin.getEconomy().withdraw(
                        event.getPlayer().getUniqueId(),
                        shopMeta.getBuyPrice(),
                        transactionDescription
                );

                if (withdrawTransaction.transactionSuccess()) {

                    EconomyResponse depositTransaction = plugin.getEconomy().deposit(
                            shopMeta.getOwner(),
                            withdrawTransaction.getAmount(),
                            transactionDescription
                    );

                    if (depositTransaction.transactionSuccess()) {

                        if (shopInv.removeItem(shopMeta.getItem().clone()).isEmpty()) {

                            Map<Integer, ItemStack> addItemResult = event.getPlayer().getInventory().addItem(shopMeta.getItem().clone());

                            if (!addItemResult.isEmpty()) {
                                Location location = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation();
                                for (ItemStack i : addItemResult.values())
                                    event.getPlayer().getWorld().dropItemNaturally(location, i);
                            }

                            event.getPlayer().sendMessage(ChatColor.DARK_GREEN + "[Shop] " + ChatColor.AQUA + "You bought " + shopMeta.getAmount() + " "
                                    + WordUtils.capitalizeFully(shopMeta.getItem().getType().toString().replace('_', ' '))
                                    + " for " + depositTransaction.getAmount() + " " +
                                    ((depositTransaction.getAmount() == 1) ? plugin.getEconomy().currencyNameSingular()
                                            : plugin.getEconomy().currencyNamePlural()) + "!");

                        } else {

                            event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: Failed to remove item from ChestShop.");

                        }

                    } else {

                        event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: Transaction failed to deposit ChestShop owner.");
                        plugin.getLogger().warning("Failed to deposit owner upon ChestShop buy: " + depositTransaction.getErrorMessage());

                    }

                } else {

                    event.getPlayer().sendMessage(ChatColor.DARK_RED + "Error: " + withdrawTransaction.getErrorMessage());

                }

            }

        }
    }

}
