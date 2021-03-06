package org.gestern.gringotts;

import java.util.LinkedHashMap;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.gestern.gringotts.currency.GringottsCurrency;

/**
 * Singleton for global configuration information. 
 * Values are initialized when the plugin is enabled.
 * 
 * @author jast
 *
 */
public enum Configuration {

    /** Central configuration instance. */
    CONF;

    private final Logger log = Gringotts.G.getLogger();

    /** Language to be used for messages. Should be an ISO 639-1 (alpha-2) code. 
     * If a language is not supported by Gringotts, use user-configured or default (English) messages. */
    public String language = "custom";

    /** Currency item types. The item types are ordered by their respective unit value. */
    public GringottsCurrency currency;

    /** Flat tax on every player-to-player transaction. This is a value in currency units. */
    public double transactionTaxFlat = 0;

    /** Rate tax on every player-to-player transaction. This is a fraction, e.g. 0.1 means 10% tax. */ 
    public double transactionTaxRate = 0;

    /** Amount of non-physical money to give to new players */
    // An alternative to flooding new players' inventories with currency items
    public long startBalancePlayer = 0;
    public long startBalanceFaction = 0;
    public long startBalanceTown = 0;
    public long startBalanceNation = 0;

    /** Use container vaults (chest, dispenser, furnace). */
    public boolean usevaultContainer;

    /** Use ender chests as player vaults. */
    public boolean usevaultEnderchest;
    
    /** Allow money to be crafted / used in crafting */
    public boolean isCraftable;

    /** Regular expression defining what patterns on a sign will create a valid vault. Subpattern 1 denotes the type of the vault. */
    // TODO make this actually configurable(?)
    public final String vaultPattern = "[^\\[]*\\[(\\w*) ?vault\\]";




    /**
     * Set configuration from values in a file configuration.
     * @param savedConfig config to read and set values with
     */
    public void readConfig(FileConfiguration savedConfig) {

        String version = Bukkit.getBukkitVersion();

        if (Util.versionAtLeast(version, "1.3.1")) {
            log.info("Found Bukkit version: "+version+". All features enabled.");

            CONF.usevaultEnderchest = savedConfig.getBoolean("usevault.enderchest", true);

        } else {
            log.info("Found Bukkit version: "+version+". Disabling 1.3+ features.");

            CONF.usevaultEnderchest = false;
        }

        // legacy parameter sets digits to 0 (false) or 2 (true)
        int digits = savedConfig.getBoolean("currency.fractional", true) ? 2 : 0;
        // digits param overrides fractional if available
        digits = savedConfig.getInt("currency.digits", digits);

        String currencyNameSingular, currencyNamePlural;
        currencyNameSingular = savedConfig.getString("currency.name.singular", "Emerald");
        currencyNamePlural = savedConfig.getString("currency.name.plural", currencyNameSingular+"s");
        currency = new GringottsCurrency(currencyNameSingular, currencyNamePlural, digits);

        // legacy currency config, overrides defaults if available
        int currencyType = savedConfig.getInt("currency.type",-1);
        if (currencyType > 0) {
            byte currencyDataValue = (byte)savedConfig.getInt("currency.datavalue", 0);
            // TODO use material name instead of id
            ItemStack legacyCurrency = new ItemStack(currencyType, 0, (short)0);
            legacyCurrency.setData(new MaterialData(currencyType, currencyDataValue));
            currency.addDenomination(legacyCurrency, 1);
        } else {
            // regular currency configuration (multi-denomination)
            ConfigurationSection denomSection = savedConfig.getConfigurationSection("currency.denominations");
            parseCurrency(denomSection, savedConfig);
        }
        CONF.isCraftable = savedConfig.getBoolean("currency.craftable", true);

        CONF.transactionTaxFlat = savedConfig.getDouble("transactiontax.flat", 0);
        CONF.transactionTaxRate = savedConfig.getDouble("transactiontax.rate", 0);

        CONF.startBalancePlayer = savedConfig.getLong("startingbalance.player", 0);
        CONF.startBalanceFaction = savedConfig.getLong("startingbalance.faction", 0);
        CONF.startBalanceTown = savedConfig.getLong("startingbalance.town", 0);
        CONF.startBalanceNation = savedConfig.getLong("startingbalance.nation", 0);

        CONF.usevaultContainer = savedConfig.getBoolean("usevault.container", true);

        CONF.language = savedConfig.getString("language", "custom");
    }

    /**
     * Parse currency list from configuration, if present.
     * A currency definition consists of a map of denominations to value.
     * A denomination type is defined either as the item id, 
     * or a semicolon-separated string of item id; damage value; data value
     * @param denomSection config section containing denomination definition
     * @param savedConfig the entire config for if the denom section is "null"
     */
	@SuppressWarnings("deprecation")
	private void parseCurrency(ConfigurationSection denomSection, FileConfiguration savedConfig) {
		//if the denom section is null, it means it doesn't have a dictionary
		//thus we'll read it in the new list format
		if(denomSection == null && savedConfig.isList("currency.denominations")) {
			for(Object denomObj : savedConfig.getList("currency.denominations")) {
				@SuppressWarnings("unchecked")
				LinkedHashMap<String, Object> denomEntry = (LinkedHashMap<String, Object>) denomObj;
				Material type = null;
	            ItemStack denomType = null;
	            short damage = 0;
	            String name = "";
	            double value = 0.0;
	            try {
	            	//TODO support lore?
	            	String[] fullType = ((String) denomEntry.get("item")).split(";");
	            	damage = fullType.length > 1 ? Short.parseShort(fullType[1]) : 0;
	            	type = Material.matchMaterial(fullType[0]);
	            	//if that didn't match anything, treat the input as an item id
		            if(type == null) {
		            	type = Material.getMaterial(Integer.parseInt(fullType[0]));
		            }
		            denomType = new ItemStack(type, 1, damage);
		            name = ChatColor.translateAlternateColorCodes('&', (String) denomEntry.get("name"));
		            value = denomEntry.get("value") instanceof Double ? (double) denomEntry.get("value") : (int) denomEntry.get("value");
		            if(!name.isEmpty()) {
		            	ItemMeta meta = denomType.getItemMeta();
		            	meta.setDisplayName(name);
		            	denomType.setItemMeta(meta);
		            }
		            currency.addDenomination(denomType, value);
	            } catch (Exception e) {
	                throw new GringottsConfigurationException("Encountered an error parsing currency. Please check your Gringotts configuration.", e);
	            }
			}
		}
		else {
			parseLegacyCurrency(denomSection);
		}
    }

	/**
	 * Parse a multi-denomination currency from configuration.
     * A currency definition consists of a map of denominations to value.
     * A denomination type is defined either as the item id, item name,
     * or a semicolon-separated string of item id; damage value; data value
     * @param denomSection config section containing denomination definition
	 */
    @SuppressWarnings("deprecation")
	private void parseLegacyCurrency(ConfigurationSection denomSection) {
		Set<String> denoms = denomSection.getKeys(false);
        if (denoms.isEmpty()) {
			throw new GringottsConfigurationException("No denominations configured. Please check your Gringotts configuration.");
		}

        for (String denomStr : denoms) {
            String[] keyParts = denomStr.split(";");
            String[] valueParts = denomSection.getString(denomStr).split(";");
            Material type;
            short dmg = 0;
            String name = "";
            try {
                // a denomination needs at least a valid item type
	            type = Material.matchMaterial(keyParts[0]);
	            //if that didn't match anything, treat the input as an item id
	            if(type == null) {
	            	type = Material.getMaterial(Integer.parseInt(keyParts[0]));
	            }
	            if (keyParts.length >=2) {
					dmg = Short.parseShort(keyParts[1]);
				}
	            ItemStack denomType = new ItemStack(type, 1, dmg);
	            if(valueParts.length >=2) {
					name = ChatColor.translateAlternateColorCodes('&', valueParts[1]);
				}
	            if(!name.isEmpty()) {
	            	ItemMeta meta = denomType.getItemMeta();
	            	meta.setDisplayName(name);
	            	denomType.setItemMeta(meta);
	            }
	            double value = Double.parseDouble(valueParts[0]);
	            currency.addDenomination(denomType, value);
            } catch (Exception e) {
                throw new GringottsConfigurationException("Encountered an error parsing currency. Please check your Gringotts configuration.", e);
            }
        }
	}

}
