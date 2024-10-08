package com._0xceba;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.PersistedObject;

/**
 * Class entry point which implements {@link BurpExtension}.
 */
public class BurpVariablesEntry implements BurpExtension {

    /**
     * Main function.
     *
     * @param api Grants access to the Montoya API operations.
     */
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Burp Variables");

        // Initialize logging and persistence instances
        Logging logging = api.logging();
        PersistedObject persist =  api.persistence().extensionData();

        // Register the GUI tab, add it to the Burp Frame
        api.userInterface().registerSuiteTab("Variables", new BurpVariablesTab(logging, persist, api));

        // Register the HTTP handler to intercept requests
        api.http().registerHttpHandler(new BurpVariablesHTTPHandler(logging, persist));

        // Log load output
        api.logging().logToOutput("Burp Variables v" +
                getClass().getPackage().getImplementationVersion() +
                " loaded successfully.");

        // Register the unload handler
        api.extension().registerUnloadingHandler(() -> {
            api.logging().logToOutput("Burp Variables unloaded successfully.");
        });
    }
}