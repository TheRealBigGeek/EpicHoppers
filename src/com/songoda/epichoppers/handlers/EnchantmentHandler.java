package com.songoda.epichoppers.handlers;

import java.util.ArrayList;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.songoda.arconix.plugin.Arconix;
import com.songoda.epichoppers.EpicHoppersPlugin;
import com.songoda.epichoppers.utils.Debugger;

/**
 * Created by songoda on 3/22/2017.
 */
public class EnchantmentHandler {

	private EpicHoppersPlugin instance;

	public EnchantmentHandler(EpicHoppersPlugin instance) {
		this.instance = instance;
	}

	public EnchantmentHandler() {
	}

	public ItemStack createSyncTouch(ItemStack item, Block b) {
		try {
			ItemMeta itemmeta = item.getItemMeta();
			ArrayList<String> lore = new ArrayList<String>();
			if (b != null) {
				lore.add(Arconix.pl().getApi().format().formatText("&aSync Touch"));
				lore.add(Arconix.pl().getApi().format().convertToInvisibleString(Arconix.pl().getApi().serialize().serializeLocation(b)));
			} else {
				lore.add(Arconix.pl().getApi().format().formatText("&7Sync Touch"));
			}
			itemmeta.setLore(lore);
			item.setItemMeta(itemmeta);
			return item;
		} catch (Exception e) {
			Debugger.runReport(e);
		}
		return null;
	}

	public void giveSyncTouchBook(Player p) {
		try {
			boolean isEmpty = false;
			for (ItemStack item : p.getInventory().getContents()) {
				if (item == null) {
					isEmpty = true;
				}
			}
			if (!isEmpty) {
				p.sendMessage(instance.references.getPrefix() + instance.getLocale().getMessage("event.inventory.noroom"));
			} else {
				ItemStack book = getbook();
				p.getInventory().addItem(book);
			}
		} catch (Exception e) {
			Debugger.runReport(e);
		}
	}

	public ItemStack getbook() {
		try {
			ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
			ItemMeta meta = book.getItemMeta();
			meta.setDisplayName(Arconix.pl().getApi().format().formatText("&eEnchanted Book"));

			ArrayList<String> lore = new ArrayList<>();
			lore.add(Arconix.pl().getApi().format().formatText("&7Sync Touch"));
			meta.setLore(lore);
			book.setItemMeta(meta);
			return book;
		} catch (Exception e) {
			Debugger.runReport(e);
		}
		return null;
	}
}