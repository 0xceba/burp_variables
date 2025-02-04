package com._0xceba;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.MontoyaApi;
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

        // Initialize a HashMap for storing variable key:value pairs
        // populate the HashMap with data from the persistence object
        HashMap<String, String> variablesMap = new HashMap<>();
        for (String key : burpPersistence.stringKeys()) {
            variablesMap.put(key, burpPersistence.getString(key));
        }

        // Register a tab labeled "Variables" in the Burp user interface
        montoyaApi.userInterface().registerSuiteTab("Variables", new BurpVariablesTab(montoyaApi, burpLogging, variablesMap, toolsEnabledMap));

        // Register an HTTP handler to intercept and modify requests
        montoyaApi.http().registerHttpHandler(new BurpVariablesHTTPHandler(burpLogging, variablesMap, toolsEnabledMap));

        // Register a context menu provider to add items to the context menu
        montoyaApi.userInterface().registerContextMenuItemsProvider(new BurpVariablesContextMenuProvider(burpLogging, variablesMap));

        // Log initialization output
        montoyaApi.logging().logToOutput("Burp Variables v" +
                getClass().getPackage().getImplementationVersion() +
                " loaded successfully.");

        // Register an unload handler that is called when the extension is unloaded or Burp is exited
        montoyaApi.extension().registerUnloadingHandler(() -> {
            // Save the tools enabled settings to the persistence object
            for (HashMap.Entry<String, Boolean> entry : toolsEnabledMap.entrySet())
                burpPersistence.setBoolean(entry.getKey(), entry.getValue());

            // Clear the persistence object
            for (String key : burpPersistence.stringKeys()) {
                burpPersistence.deleteString(key);
            }

            // Copy the variables from the storage object to the persistence object
            for (HashMap.Entry<String, String> entry : variablesMap.entrySet()) {
                burpPersistence.setString(entry.getKey(), entry.getValue());
            }

            montoyaApi.logging().logToOutput("Burp Variables unloaded successfully.");
        });
    }
}