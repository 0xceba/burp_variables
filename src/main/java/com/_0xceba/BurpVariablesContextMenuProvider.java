package com._0xceba;

import burp.api.montoya.logging.Logging;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.contextmenu.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import javax.swing.*;

/**
 * Context menu provider to add menu items for inserting variables at
 * the user's caret.
 */
public class BurpVariablesContextMenuProvider implements ContextMenuItemsProvider {
    private final Logging burpLogging;
    private final HashMap<String, VariableData> variablesMap;

    /**
     * Constructs a new context menu provider.
     *
     * @param burpLogging   The logging interface from the Montoya API.
     * @param variablesMap  HashMap storing variable names and VariableData.
     */
    public BurpVariablesContextMenuProvider(Logging burpLogging, HashMap<String, VariableData> variablesMap) {
        this.burpLogging = burpLogging;
        this.variablesMap = variablesMap;
    }

    /**
     * Generates and returns a list of context menu items for each variable if
     * the context menu is executed against a message editor request. The menu
     * items insert the selected variable key.
     *
     * @param contextMenuEvent  The event that triggered the context menu.
     * @return  A list of the context menu items to be added to the menu.
     */
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent contextMenuEvent) {
        // Check if the event originated from MESSAGE_EDITOR_REQUEST
        if(contextMenuEvent.messageEditorRequestResponse().isPresent()
                && contextMenuEvent.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST)) {

            // Return null if no variables are defined to avoid an empty context menu
            if (variablesMap.isEmpty()) {
                return null;
            }

            // List of context menu items to be returned
            List<Component> contextMenuProviderList = new ArrayList<>();

            // Sort variablesMap keys alphabetically
            List<String> sortedVariablesMapKeys = new ArrayList<>(variablesMap.keySet());
            Collections.sort(sortedVariablesMapKeys);

            // Iterate through sorted variablesMap keys
            for(String variableKey : sortedVariablesMapKeys) {
                // Create a new JMenuItem with the label containing the variablesMap key
                JMenuItem contextMenuItem = new JMenuItem("Insert ((" + variableKey + "))");

                // Add an action listener to handle user interaction
                contextMenuItem.addActionListener(e -> {

                    // Variable to store the modified HTTP request string
                    String modifiedRequestString;

                    MessageEditorHttpRequestResponse messageEditor = contextMenuEvent.messageEditorRequestResponse().get();
                    // If the user has selected text, replace the selection; otherwise insert at caret
                    if(messageEditor.selectionOffsets().isPresent()) {
                        // Get the starting and ending indexes of the selected text of the request
                        int startIndex = messageEditor.selectionOffsets().get().startIndexInclusive();
                        int endIndex = messageEditor.selectionOffsets().get().endIndexExclusive();

                        // Use StringBuilder to make a String copy of the request with the replaced variable name
                        StringBuilder unmodifiedRequestStringBuilder = new StringBuilder(messageEditor.requestResponse().request().toString());
                        unmodifiedRequestStringBuilder.replace(startIndex, endIndex, "((" + variableKey + "))");
                        modifiedRequestString = unmodifiedRequestStringBuilder.toString();
                    } else {
                        // Get the caret position from the message editor
                        int caretPosition = messageEditor.caretPosition();

                        // Use StringBuilder to make a String copy of the request with the added variable name
                        StringBuilder unmodifiedRequestStringBuilder = new StringBuilder(messageEditor.requestResponse().request().toString());
                        unmodifiedRequestStringBuilder.insert(caretPosition, "((" + variableKey + "))");
                        modifiedRequestString = unmodifiedRequestStringBuilder.toString();
                    }

                    // Retrieve the HTTP service from the original request
                    HttpService requestService = messageEditor.requestResponse().request().httpService();
                    // Create a modified HTTP request using the retrieved HTTP service
                    HttpRequest modifiedRequest = HttpRequest.httpRequest(requestService, modifiedRequestString);

                    // Set the modified request in the message editor
                    messageEditor.setRequest(modifiedRequest);
                });
                // Add the context menu item to the provider list
                contextMenuProviderList.add(contextMenuItem);
            }
            // Return the context menu provider list
            return contextMenuProviderList;
        } else {
            return null;
        }
    }
}