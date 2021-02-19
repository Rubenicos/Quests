package com.leonardobishop.quests.quests.tasktypes;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.quests.Task;
import org.bukkit.entity.Player;

import java.util.List;

public class TaskUtils {

    public static boolean validateWorld(Player player, Task task) {
        return validateWorld(player.getLocation().getWorld().getName(), task.getConfigValue("worlds"));
    }

    public static boolean validateWorld(String worldName, Task task) {
        return validateWorld(worldName, task.getConfigValue("worlds"));
    }

    public static boolean validateWorld(String worldName, Object configurationData) {
        if (configurationData == null) {
            return true;
        }

        if (configurationData instanceof List) {
            List allowedWorlds = (List) configurationData;
            if (!allowedWorlds.isEmpty() && allowedWorlds.get(0) instanceof String) {
                List<String> allowedWorldNames = (List<String>) allowedWorlds;
                return allowedWorldNames.contains(worldName);
            }
            return true;
        }

        if (configurationData instanceof String) {
            String allowedWorld = (String) configurationData;
            return worldName.equals(allowedWorld);
        }

        return true;
    }

    public static void configValidateInt(String path, Object object, List<String> problems, boolean allowNull, boolean greaterThanZero, String... args) {
        if (object == null) {
            if (!allowNull) {
                problems.add(String.format("Expected an integer for \"%s\", but got null instead, check path " + path, (Object[]) args));
            }
            return;
        }

        try {
            Integer i = (Integer) object;
            if (greaterThanZero && i <= 0) {
                problems.add(String.format("Value for field '%s' must be greater than 0, check path " + path, (Object[]) args));
            }
        } catch (ClassCastException ex) {
            problems.add(String.format("Expected an integer for \"%s\", but got \"" + object + "\" instead, check path " + path, (Object[]) args));
        }
    }

    public static void configValidateBoolean(String path, Object object, List<String> problems, boolean allowNull, String... args) {
        if (object == null) {
            if (!allowNull) {
                problems.add(String.format("Expected a boolean for \"%s\", but got null instead, check path " + path, (Object[]) args));
            }
            return;
        }

        try {
            Boolean b = (Boolean) object;
        } catch (ClassCastException ex) {
            problems.add(String.format("Expected a boolean for \"%s\", but got \"" + object + "\" instead, check path " + path, (Object[]) args));
        }
    }

    public static void configValidateMaterial(String path, String material, List<String> problems, String... args) {
        if (material == null) {
            problems.add(String.format("Expected a material for \"%s\", but got null instead, check path " + path, (Object[]) args));
            return;
        }

        if (!Quests.get().getItemGetter().isValidMaterial(material)) {
            problems.add("Material" + material + "does not exist, check path " + path);
        }
    }

    public static boolean configValidateExists(String path, Object object, List<String> problems, String... args) {
        if (object == null) {
            problems.add(String.format("Required field \"%s\" is missing for task type \"%s\", create the field at path " + path + " to avoid this issue", (Object[]) args));
            return false;
        }
        return true;
    }
}
