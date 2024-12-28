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
     * @param api Provides access to the Montoya API operations.
     */
    @Override
    public void initialize(MontoyaApi api) {
        // Set the extension name in Burp
        api.extension().setName("Burp Variables");

        // Initialize logging and persistence mechanisms
        Logging logging = api.logging();
        PersistedObject persistence =  api.persistence().extensionData();

        // Initialize a HashMap for storing tool toggle settings
        // populate the HashMap with data from the persistence object
        HashMap<String, Boolean> toolsEnabledMap = new HashMap<>();
        for (String key : persistence.booleanKeys()) {
            toolsEnabledMap.put(key, persistence.getBoolean(key));
        }

        // Initialize a HashMap for storing variable key:value pairs
        // populate the HashMap with data from the persistence object
        HashMap<String, String> variablesMap = new HashMap<>();
        for (String key : persistence.stringKeys()) {
            variablesMap.put(key, persistence.getString(key));
        }

        // Register a tab labeled "Variables" in the Burp user interface
        api.userInterface().registerSuiteTab("Variables", new BurpVariablesTab(logging, api, variablesMap, toolsEnabledMap));

        // Register an HTTP handler to intercept and modify requests
        api.http().registerHttpHandler(new BurpVariablesHTTPHandler(logging, variablesMap, toolsEnabledMap));

        // Log initialization output
        api.logging().logToOutput("Burp Variables v" +
                getClass().getPackage().getImplementationVersion() +
                " loaded successfully.");

        // Register an unload handler that is called when the extension is unloaded or Burp is exited
        api.extension().registerUnloadingHandler(() -> {
            // Save the tools enabled settings to the persistence object
            for (HashMap.Entry<String, Boolean> entry : toolsEnabledMap.entrySet())
                persistence.setBoolean(entry.getKey(), entry.getValue());

            // Clear the persistence object
            for (String key : persistence.stringKeys()) {
                persistence.deleteString(key);
            }

            // Copy the variables from the storage object to the persistence object
            for (HashMap.Entry<String, String> entry : variablesMap.entrySet()) {
                persistence.setString(entry.getKey(), entry.getValue());
            }

            api.logging().logToOutput("Burp Variables unloaded successfully.");
        });
    }
}