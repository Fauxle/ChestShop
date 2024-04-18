package me.fauxle.cs;

import com.google.common.base.Preconditions;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ChestShopMeta {

    private UUID owner;
    private Integer buyPrice;
    private Integer sellPrice;
    private ItemStack item;

    public ChestShopMeta(UUID owner, Integer buyPrice, Integer sellPrice, ItemStack item) {
        setOwner(owner);
        setBuyPrice(buyPrice);
        setSellPrice(sellPrice);
        setItem(item);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        Preconditions.checkNotNull(owner, "Owner cannot be null");
        this.owner = owner;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        Preconditions.checkNotNull(item, "Item cannot be null");
        this.item = item;
    }

    public int getAmount() {
        return item.getAmount();
    }

    public boolean isBuying() {
        return buyPrice != null;
    }

    public boolean isSelling() {
        return sellPrice != null;
    }

    public int getBuyPrice() {
        return buyPrice;
    }

    public int getSellPrice() {
        return sellPrice;
    }

    public void setBuyPrice(Integer buyPrice) {
        Preconditions.checkArgument((buyPrice == null || buyPrice >= 0), "Buy price cannot be negative");
        this.buyPrice = buyPrice;
    }

    public void setSellPrice(Integer sellPrice) {
        Preconditions.checkArgument((sellPrice == null || sellPrice >= 0), "Sell price cannot be negative");
        this.sellPrice = sellPrice;
    }

}
