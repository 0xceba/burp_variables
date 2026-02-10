package com._0xceba;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.persistence.PersistedList;
import burp.api.montoya.persistence.PersistedObject;

import java.util.HashMap;

/**
 * This class serves as the entry point for the Burp Variables extension.
 * It implements the BurpExtension interface to integrate with Burp.
 */
public class BurpVariablesEntry implements BurpExtension {

    /**
     * Initializes the Burp Variables extension.
     * This method is called by Burp when the extension is loaded.
     *
     * @param montoyaApi    Provides access to the Montoya API operations.
     */
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        // Set the extension name in Burp
        montoyaApi.extension().setName("Burp Variables");

        // Initialize logging and persistence mechanisms
        Logging burpLogging = montoyaApi.logging();
        PersistedObject burpPersistence =  montoyaApi.persistence().extensionData();

        // Initialize a HashMap for storing tool toggle settings
        // populate the HashMap with data from the persistence object
        HashMap<String, Boolean> toolsEnabledMap = new HashMap<>();
        for (String key : burpPersistence.booleanKeys()) {
            toolsEnabledMap.put(key, burpPersistence.getBoolean(key));
        }

        // Initialize a HashMap for storing runtime variable data (key, [value, regex])
        HashMap<String, VariableData> variablesMap = new HashMap<>();

        // Populate variablesMap with data from the persistence object
        for (String key : burpPersistence.stringListKeys()) {
            // Retrieve each key's persisted string list of [value, regex]
            PersistedList<String> variableDataList = burpPersistence.getStringList(key);
            if (variableDataList != null && !variableDataList.isEmpty()) {
                String value = variableDataList.get(0);
                // Check if the list has at least 2 elements, get regex value from
                // index 1 or use empty string as fallback
                String regex = variableDataList.size() > 1 ? variableDataList.get(1) : "";
                // Store the runtime variable data
                variablesMap.put(key, new VariableData(value, regex));
            }
        }

        // Migrate the legacy persistence format (stringKey) to the new format (stringKeyList)
        // TODO: Remove this functionality after 2026-02
        for (String key : burpPersistence.stringKeys()) {
            if (!variablesMap.containsKey(key)) {
                String value = burpPersistence.getString(key);
                if (value != null) {
                    variablesMap.put(key, new VariableData(value, ""));
                }
            }
        }

        // Register a tab labeled "Variables" in the Burp user interface
        BurpVariablesTab variablesTab = new BurpVariablesTab(montoyaApi, burpLogging, variablesMap, toolsEnabledMap);
        montoyaApi.userInterface().registerSuiteTab("Variables", variablesTab);

        // Register an HTTP handler to intercept and modify requests
        montoyaApi.http().registerHttpHandler(new BurpVariablesHTTPHandler(burpLogging, variablesMap, toolsEnabledMap, variablesTab));

        // Register a context menu provider to add items to the context menu
        montoyaApi.userInterface().registerContextMenuItemsProvider(new BurpVariablesContextMenuProvider(burpLogging, variablesMap));

        // Log initialization output
        String version = getClass().getPackage().getImplementationVersion();
        burpLogging.logToOutput("Burp Variables v" +
                (version != null ? version : "0.0.0") +
                " loaded successfully.");

        // Register an unload handler that is called when the extension is unloaded or Burp is exited
        montoyaApi.extension().registerUnloadingHandler(() -> {
            // Save the tools enabled settings to the persistence object
            for (HashMap.Entry<String, Boolean> entry : toolsEnabledMap.entrySet())
                burpPersistence.setBoolean(entry.getKey(), entry.getValue());

            // Clear the persisted string lists
            for (String key : burpPersistence.stringListKeys()) {
                burpPersistence.deleteStringList(key);
            }

            // Save the variable data to Burp persistent storage
            for (HashMap.Entry<String, VariableData> entry : variablesMap.entrySet()) {
                PersistedList<String> list = PersistedList.persistedStringList();
                list.add(entry.getValue().value());
                list.add(entry.getValue().regex());
                // Save the list using the variable name as the key
                burpPersistence.setStringList(entry.getKey(), list);
            }

            // Delete the legacy String persistence format
            // TODO: Remove this functionality after 2026-02
            for (String key : burpPersistence.stringKeys()) {
                burpPersistence.deleteString(key);
            }

            burpLogging.logToOutput("Burp Variables unloaded successfully.");
        });
    }
}