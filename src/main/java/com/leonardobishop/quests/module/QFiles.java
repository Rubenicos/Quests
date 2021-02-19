package com.leonardobishop.quests.module;

import com.leonardobishop.quests.Quests;
import com.leonardobishop.quests.obj.misc.QItemStack;
import com.leonardobishop.quests.quests.Category;
import com.leonardobishop.quests.quests.Quest;
import com.leonardobishop.quests.quests.Task;
import com.leonardobishop.quests.quests.tasktypes.TaskType;
import org.apache.commons.lang.StringUtils;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QFiles {

    private static final Quests pl = Quests.get();
    private static final Map<String, Problem> problems = new HashMap<>();
    private static int problemsCount = 0;

    private static final HashMap<String, Map<String, Object>> globalTaskConfig = new HashMap<>();

    // TODO: Move quests loader to better place
    public static void reloadQuests() {
        if (!pl.isBrokenConfig()) {
            // TODO: Make categories into separated files to allow custom menus for categories
            pl.getConfig().getConfigurationSection("categories").getKeys(false).forEach(key -> {
                ItemStack displayItem = pl.getItemStack("categories." + key + ".display", pl.getConfig());
                boolean permissionRequired = pl.getConfig().getBoolean("categories." + key + ".permission-required", false);

                Category category = new Category(key, displayItem, permissionRequired);
                pl.getQuestManager().registerCategory(category);
            });

            if (pl.getConfig().isConfigurationSection("global-task-configuration.types")) {
                pl.getConfig().getConfigurationSection("global-task-configuration.types").getKeys(false).forEach(type -> {
                    HashMap<String, Object> configValues = new HashMap<>();
                    for (String key : pl.getConfig().getConfigurationSection("global-task-configuration.types." + type).getKeys(false)) {
                        configValues.put(key, pl.getConfig().get("global-task-configuration.types." + type + "." + key));
                    }
                    globalTaskConfig.putIfAbsent(type, configValues);
                });
            }

        }

        File qFolder = getFromFolder("quests");
        if (!qFolder.isDirectory()) qFolder.delete();
        if (!qFolder.exists()) {
            pl.saveResource("quests/example1.yml", false);
            pl.saveResource("quests/example2.yml", false);
            pl.saveResource("quests/example3.yml", false);
            pl.saveResource("quests/example4.yml", false);
            pl.saveResource("quests/example5.yml", false);
            pl.saveResource("quests/example6.yml", false);
            pl.saveResource("quests/example7.yml", false);
            pl.saveResource("quests/README.txt", false);
        }

        loadQuestFolder(qFolder);

        for (Map.Entry<String, Quest> quest : pl.getQuestManager().getQuests().entrySet()) {
            for (String req : quest.getValue().getRequirements()) {
                if (pl.getQuestManager().getQuestById(req) == null) {
                    addProblem("<QUEST> " + quest.getKey() + ".yml", "WARNING", "Quest requirement \"" + req + "\" does not exist");
                }
            }
        }

        for (TaskType taskType : pl.getTaskTypeManager().getTaskTypes()) {
            try {
                taskType.onReady();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Load all quest files in folder, including all subfolders
     * @param folder quests folder
     */
    public static void loadQuestFolder(File folder) {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                loadQuestFolder(file);
            } else {
                loadQuest(file);
            }
        }
    }

    public static void loadQuest(File file) {
        if (!file.getName().endsWith(".yml")) return;
        if (!isSafe("<QUEST> ", file)) return;

        String id = file.getName().replace(".yml", "");
        if (!StringUtils.isAlphanumeric(id)) {
            addProblem("<QUEST> " + file.getName(), "ERROR", "ID \"" + id + "\" is invalid, must be alphanumeric, unique and with no spaces");
            return;
        }

        YamlConfiguration qConfig = YamlConfiguration.loadConfiguration(file);
        String category = qConfig.getString("options.category");
        if (category != null) {
            Category c = pl.getQuestManager().getCategoryById(category);
            if (c != null) {
                c.registerQuestId(id);
            } else {
                addProblem("<QUEST> " + file.getName(), "WARNING", "Category \"" + category + "\" does not exist");
            }
        }

        QItemStack displayItem = QItemStack.getQItemStack("display", qConfig);
        List<String> rewards = qConfig.getStringList("rewards");
        List<String> requirements = qConfig.getStringList("options.requires");
        List<String> rewardString = qConfig.getStringList("rewardstring");
        List<String> startString = qConfig.getStringList("startstring");
        boolean repeatable = qConfig.getBoolean("options.repeatable", false);
        boolean cooldown = qConfig.getBoolean("options.cooldown.enabled", false);
        boolean permissionRequired = qConfig.getBoolean("options.permission-required", false);
        int cooldownTime = qConfig.getInt("options.cooldown.time", 10);
        int sortOrder = qConfig.getInt("options.sort-order", 1);
        Map<String, String> placeholders = new HashMap<>();

        Quest quest = new Quest(id, displayItem, rewards, requirements, repeatable, cooldown, cooldownTime, permissionRequired, rewardString, startString, placeholders, sortOrder, category);

        List<String> validTasks = new ArrayList<>();

        if (!qConfig.isConfigurationSection("tasks")) {
            addProblem("<QUEST> " + file.getName(), "ERROR", "Quest no contains tasks section");
        } else {
            List<String> messages = new ArrayList<>();
            for (String taskID : qConfig.getConfigurationSection("tasks").getKeys(false)) {

                String taskRoot = "tasks." + taskID;
                if (!qConfig.isConfigurationSection(taskRoot)) {
                    messages.add("Task \"" + taskRoot + "\" is not a configuration section (has no fields)");
                    continue;
                }

                String taskType = qConfig.getString(taskRoot + ".type");
                if (taskType == null) {
                    messages.add("Task \"" + taskID + "\" not specified a task type");
                    continue;
                }

                TaskType type = pl.getTaskTypeManager().getTaskType(taskType);
                if (type == null) {
                    messages.add("Task \"" + taskID + "\" has invalid task type \"" + taskType + "\"");
                } else {
                    HashMap<String, Object> taskValues = new HashMap<>();
                    for (String key : qConfig.getConfigurationSection(taskRoot).getKeys(false)) {
                        taskValues.put(key, qConfig.get(taskRoot + "." + key));
                    }
                    List<String> taskProblems = type.detectProblemsInConfig(taskRoot, taskValues);
                    if (!taskProblems.isEmpty()) {
                        messages.addAll(taskProblems);
                    } else {
                        Task task = new Task(taskID, taskType);
                        task.addConfigValues(taskValues);
                        if (globalTaskConfig.containsKey(taskType)) {
                            for (Map.Entry<String, Object> entry : globalTaskConfig.get(taskType).entrySet()) {
                                if (pl.getSettings().getBoolean("options.global-task-configuration-override") && task.getConfigValue(entry.getKey()) != null) continue;
                                task.addConfigValue(entry.getKey(), entry.getValue());
                            }
                        }
                        quest.registerTask(task);
                        validTasks.add(taskID);
                    }
                }
            }

            messages.forEach(msg -> addProblem("<QUEST> " + file.getName(), "WARNING", msg));
            if (validTasks.size() == 0) {
                addProblem("<QUEST> " + file.getName(), "ERROR", "Quest contains no valid tasks");
                return;
            } else {
                getInvalidReferences(displayItem.getLoreNormal(), validTasks).forEach(t -> addProblem("<QUEST> " + file.getName(), "WARNING", "Attempt to reference unknown task \"" + t + "\" on lore-normal"));
                getInvalidReferences(displayItem.getLoreStarted(), validTasks).forEach(t -> addProblem("<QUEST> " + file.getName(), "WARNING", "Attempt to reference unknown task \"" + t + "\" on lore-started"));
            }
        }

        if (qConfig.isConfigurationSection("placeholders")) {
            qConfig.getConfigurationSection("placeholders").getKeys(false).forEach(p -> {
                placeholders.put(p, qConfig.getString("placeholders." + p));
                if (validTasks.size() != 0) {
                    getInvalidReferences(Collections.singletonList(qConfig.getString("placeholders." + p)), validTasks).forEach(t -> addProblem("<QUEST> " + file.getName(), "WARNING", "Attempt to reference unknown task \"" + t + "\" on placeholders." + p));
                }
            });
        }

        if (problems.get("<QUEST> " + file.getName()) != null) {
            if (!problems.get("<QUEST> " + file.getName()).getErrors().isEmpty() && !pl.getSettings().getBoolean("options.error-checking.override-errors")) {
                return;
            }
        }

        if (pl.getSettings().getBoolean("options.show-quest-registrations")) {
            pl.getQuestsLogger().info("Registering quest " + quest.getId() + " with " + quest.getTasks().size() + " tasks.");
        }
        pl.getQuestManager().registerQuest(quest);
        pl.getTaskTypeManager().registerQuestTasksWithTaskTypes(quest);
    }

    private static List<String> getInvalidReferences(List<String> lore, List<String> validTasks) {
        List<String> tasks = new ArrayList<>();
        Pattern pattern = Pattern.compile("\\{([^}]+)}");
        for (String line : lore) {
            Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                String taskID = matcher.group(1).split(":")[0];
                if (!validTasks.contains(taskID)) {
                    tasks.add(taskID);
                }
            }
        }
        return tasks;
    }

    public static boolean isSafe(String alias, File file) {
        try {
            new YamlConfiguration().load(file);
            return true;
        } catch (FileNotFoundException e) {
            addProblem(alias + file.getName(), "ERROR", "File cannot be opened");
            return false;
        } catch (IOException e) {
            addProblem(alias + file.getName(), "ERROR", "File cannot be read");
            return false;
        } catch (InvalidConfigurationException e) {
            addProblem(alias + file.getName(), "ERROR", "Malformed YAML file, cannot read config");
            return false;
        }
    }

    public static File getFromFolder(String path) {
        return new File(pl.getDataFolder() + File.separator + path);
    }

    public static FileConfiguration getFromFile(String path, String def, boolean update) {
        File file = getFromFolder(path);
        if (!file.exists()) {
            try {
                pl.saveResource(path, false);
            } catch (IllegalArgumentException e) {
                return getFromPlugin(def);
            }
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        if (update) update(path, config);
        return config;
    }

    public static YamlConfiguration getFromPlugin(String path) {
        try {
            return YamlConfiguration.loadConfiguration(new InputStreamReader(pl.getResource(path)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void update(String path, YamlConfiguration oldCfg) {
        YamlConfiguration newCfg = getFromPlugin(path);
        if (newCfg == null) return;
        oldCfg.getKeys(true).forEach(key -> {
            if (!newCfg.contains(key)) {
                oldCfg.set(key, null);
            }
        });
        newCfg.getKeys(true).forEach(key -> {
            if (!oldCfg.contains(key)) {
                oldCfg.set(key, newCfg.get(key));
            }
        });
        try {
            oldCfg.save(pl.getDataFolder() + File.separator + path);
        } catch (IOException ignored) { }
    }

    public static Map<String, Problem> getProblems() {
        return problems;
    }

    private static void addProblem(String name, String type, String description) {
        if (problems.containsKey(name)) {
            problems.get(name).add(type, description);
        } else {
            Problem problem = new Problem();
            problem.add(type, description);
            problems.put(name, problem);
        }
        problemsCount++;
    }

    public static void resetCount() {
        problems.clear();
        problemsCount = 0;
    }

    public static int getProblemsCount() {
        return problemsCount;
    }

    public static class Problem {
        private final List<String> errors;
        private final List<String> warnings;

        public Problem() {
            errors = new ArrayList<>();
            warnings = new ArrayList<>();
        }

        public void add(String type, String description) {
            if (type.equals("ERROR")) {
                errors.add(description);
            } else {
                warnings.add(description);
            }
        }

        public List<String> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
