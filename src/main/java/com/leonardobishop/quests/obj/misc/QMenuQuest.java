package com.leonardobishop.quests.obj.misc;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.module.QLocale;
import com.leonardobishop.quests.obj.Items;
import com.leonardobishop.quests.player.QPlayer;
import com.leonardobishop.quests.player.questprogressfile.QuestProgress;
import com.leonardobishop.quests.quests.Quest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Menu for a specific category.
 */
public class QMenuQuest implements QMenu {

    private final Quests plugin;
    private final HashMap<Integer, String> slotsToQuestIds = new HashMap<>();
    private final QMenuCategory superMenu;
    private final String categoryName;
    private final int pageSize = 45;
    private final QPlayer owner;

    private int backButtonLocation = -1;
    private int pagePrevLocation = -1;
    private int pageNextLocation = -1;
    private int currentPage = -1;
    private boolean backButtonEnabled = true;

    public QMenuQuest(Quests plugin, QPlayer owner, String categoryName, QMenuCategory superMenu) {
        this.plugin = plugin;
        this.owner = owner;
        this.categoryName = categoryName;
        this.superMenu = superMenu;
    }

    public void populate(List<Quest> quests) {
        Collections.sort(quests);
        int slot = 0;
        for (Quest quest : quests) {
            if (plugin.getSettings().getBoolean("options.gui-hide-locked")) {
                QuestProgress questProgress = owner.getQuestProgressFile().getQuestProgress(quest);
                long cooldown = owner.getQuestProgressFile().getCooldownFor(quest);
                if (!owner.getQuestProgressFile().hasMetRequirements(quest) || (!quest.isRepeatable() && questProgress.isCompletedBefore()) || cooldown > 0) {
                    continue;
                }
            }
            if (plugin.getSettings().getBoolean("options.gui-hide-quests-nopermission") && quest.isPermissionRequired()) {
                if (!Bukkit.getPlayer(owner.getUuid()).hasPermission("quests.quest." + quest.getId())) {
                    continue;
                }
            }
            slotsToQuestIds.put(slot, quest.getId());
            slot++;
        }
    }

    @Override
    public HashMap<Integer, String> getSlotsToMenu() {
        return slotsToQuestIds;
    }

    @Override
    public QPlayer getOwner() {
        return owner;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public int getPagePrevLocation() {
        return pagePrevLocation;
    }

    public int getPageNextLocation() {
        return pageNextLocation;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageSize() {
        return pageSize;
    }

    public Inventory toInventory(int page) {
        currentPage = page;
        int pageMin = pageSize * (page - 1);
        int pageMax = pageSize * page;
        String title = QLocale.color(plugin.getSettings().getString("options.guinames.quests-menu"));

        ItemStack pageIs;
        ItemStack pagePrevIs;
        ItemStack pageNextIs;
        ItemStack back = Items.BACK_BUTTON.getItem();

        Inventory inventory = Bukkit.createInventory(null, 54, title);

        int invSlot = 0;
        for (int pointer = pageMin; pointer < pageMax; pointer++) {
            if (slotsToQuestIds.containsKey(pointer)) {
                Quest quest = plugin.getQuestManager().getQuestById(slotsToQuestIds.get(pointer));
                QuestProgress questProgress = owner.getQuestProgressFile().getQuestProgress(quest);
                long cooldown = owner.getQuestProgressFile().getCooldownFor(quest);
                if (!owner.getQuestProgressFile().hasMetRequirements(quest)) {
                    List<String> quests = new ArrayList<>();
                    for (String requirement : quest.getRequirements()) {
                        quests.add(plugin.getQuestManager().getQuestById(requirement).getDisplayNameStripped());
                    }
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("{quest}", quest.getDisplayNameStripped());
                    placeholders.put("{requirements}", String.join(", ", quests));
                    ItemStack is = replaceItemStack(Items.QUEST_LOCKED.getItem(), placeholders);
                    inventory.setItem(invSlot, is);
                } else if (!quest.isRepeatable() && questProgress.isCompletedBefore()) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("{quest}", quest.getDisplayNameStripped());
                    ItemStack is = replaceItemStack(Items.QUEST_COMPLETED.getItem(), placeholders);
                    inventory.setItem(invSlot, is);
                } else if (quest.isPermissionRequired() && !Bukkit.getPlayer(owner.getUuid()).hasPermission("quests.quest." + quest.getId())) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("{quest}", quest.getDisplayNameStripped());
                    ItemStack is = replaceItemStack(Items.QUEST_PERMISSION.getItem(), placeholders);
                    inventory.setItem(invSlot, is);
                } else if (cooldown > 0) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("{time}", plugin.convertToFormat(TimeUnit.SECONDS.convert(cooldown, TimeUnit.MILLISECONDS)));
                    placeholders.put("{quest}", quest.getDisplayNameStripped());
                    ItemStack is = replaceItemStack(Items.QUEST_COOLDOWN.getItem(), placeholders);
                    inventory.setItem(invSlot, is);
                } else {
                    inventory.setItem(invSlot, replaceItemStack(plugin.getQuestManager().getQuestById(
                            quest.getId()).getDisplayItem().toItemStack(quest, owner.getQuestProgressFile(), questProgress)));
                }
            }
            invSlot++;
        }

        pageNextLocation = -1;
        pagePrevLocation = -1;

        Map<String, String> pageplaceholders = new HashMap<>();
        pageplaceholders.put("{prevpage}", String.valueOf(page - 1));
        pageplaceholders.put("{nextpage}", String.valueOf(page + 1));
        pageplaceholders.put("{page}", String.valueOf(page));
        pageIs = replaceItemStack(Items.PAGE_DESCRIPTION.getItem(), pageplaceholders);
        pagePrevIs = replaceItemStack(Items.PAGE_PREV.getItem(), pageplaceholders);
        pageNextIs = replaceItemStack(Items.PAGE_NEXT.getItem(), pageplaceholders);

        if (plugin.getSettings().getBoolean("options.categories-enabled") && backButtonEnabled) {
            inventory.setItem(45, back);
            backButtonLocation = 45;
        }
        if (slotsToQuestIds.size() > pageSize) {
            inventory.setItem(49, pageIs);
            if (page != 1) {
                inventory.setItem(48, pagePrevIs);
                pagePrevLocation = 48;
            }
            if (Math.ceil((double) slotsToQuestIds.size() / ((double) 45)) != page) {
                inventory.setItem(50, pageNextIs);
                pageNextLocation = 50;
            }
        } else if (plugin.getSettings().getBoolean("options.trim-gui-size") && page == 1) {
            int slotsUsed = 0;
            for (int pointer = 0; pointer < pageMax; pointer++) {
                if (inventory.getItem(pointer) != null) {
                    slotsUsed++;
                }
            }

            int inventorySize = (slotsUsed >= 54) ? 54 : slotsUsed + (9 - slotsUsed % 9) * Math.min(1, slotsUsed % 9);
            inventorySize = inventorySize <= 0 ? 9 : inventorySize;
            if (inventorySize == 54) {
                return inventory;
            } else if (plugin.getSettings().getBoolean("options.categories-enabled") && backButtonEnabled) {
                inventorySize += 9;
            }

            Inventory trimmedInventory = Bukkit.createInventory(null, inventorySize, title);

            for (int slot = 0; slot < trimmedInventory.getSize(); slot++) {
                if (slot >= (trimmedInventory.getSize() - 9) && backButtonEnabled){
                    if (plugin.getSettings().getBoolean("options.categories-enabled")) {
                        trimmedInventory.setItem(slot, back);
                        backButtonLocation = slot;
                    }
                    break;
                }
                trimmedInventory.setItem(slot, inventory.getItem(slot));
            }
            return trimmedInventory;
        }

        return inventory;
    }

    public boolean isBackButtonEnabled() {
        return backButtonEnabled;
    }

    public void setBackButtonEnabled(boolean backButtonEnabled) {
        this.backButtonEnabled = backButtonEnabled;
    }

    public int getBackButtonLocation() {
        return backButtonLocation;
    }

    public QMenuCategory getSuperMenu() {
        return superMenu;
    }

    public ItemStack replaceItemStack(ItemStack is) {
        return replaceItemStack(is, Collections.emptyMap());
    }

    public ItemStack replaceItemStack(ItemStack is, Map<String, String> placeholders) {
        ItemStack newItemStack = is.clone();
        List<String> lore = newItemStack.getItemMeta().getLore();
        List<String> newLore = new ArrayList<>();
        ItemMeta ism = newItemStack.getItemMeta();
        Player player = Bukkit.getPlayer(owner.getUuid());
        if (lore != null) {
            for (String s : lore) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    s = s.replace(entry.getKey(), entry.getValue());
                    if (QLocale.papi && plugin.getSettings().getBoolean("options.gui-use-placeholderapi")) {
                        s = QLocale.setPlaceholders(player, s, false);
                    }
                }
                newLore.add(s);
            }
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            ism.setDisplayName(ism.getDisplayName().replace(entry.getKey(), entry.getValue()));
            if (QLocale.papi && plugin.getSettings().getBoolean("options.gui-use-placeholderapi")) {
                ism.setDisplayName(QLocale.setPlaceholders(player, ism.getDisplayName(), false));
            }
        }
        ism.setLore(newLore);
        newItemStack.setItemMeta(ism);
        return newItemStack;
    }
}
