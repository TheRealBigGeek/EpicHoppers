package com.songoda.epichoppers.hooks;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.sk89q.worldguard.bukkit.RegionQuery;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.songoda.epichoppers.api.utils.ProtectionPluginHook;

public class HookWorldGuard implements ProtectionPluginHook {

	private final WorldGuardPlugin worldGuard;

	public HookWorldGuard() {
		this.worldGuard = WorldGuardPlugin.inst();
	}

	@Override
	public JavaPlugin getPlugin() {
		return WorldGuardPlugin.inst();
	}

	@Override
	public boolean canBuild(Player player, Location location) {
		RegionQuery q = worldGuard.getRegionContainer().createQuery();
		ApplicableRegionSet ars = q.getApplicableRegions(player.getLocation());
		return ars.testState(worldGuard.wrapPlayer(player), DefaultFlag.BUILD);
	}

}