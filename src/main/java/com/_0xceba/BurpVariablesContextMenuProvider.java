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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;

/**
 * Context menu provider to add menu items for inserting variables at
 * the user's caret, swapping variable references to their resolved values,
 * and quickly creating or updating variables from selected text.
 */
public class BurpVariablesContextMenuProvider implements ContextMenuItemsProvider {
    private final Logging burpLogging;
    private final HashMap<String, VariableData> variablesMap;
    private final BurpVariablesTab variablesTab;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\(\\(.+?\\)\\)");

    /**
     * Constructs a new context menu provider.
     *
     * @param burpLogging   The logging interface from the Montoya API.
     * @param variablesMap  HashMap storing variable names and VariableData.
     * @param variablesTab  The Variables tab for creating/updating variables from the context menu.
     */
    public BurpVariablesContextMenuProvider(Logging burpLogging, HashMap<String, VariableData> variablesMap, BurpVariablesTab variablesTab) {
        this.burpLogging = burpLogging;
        this.variablesMap = variablesMap;
        this.variablesTab = variablesTab;
    }

    /**
     * Generates and returns a list of context menu items based on the invocation context.
     * <ul>
     *   <li>MESSAGE_EDITOR_REQUEST: Insert variable, Swap variable to value, Quick set variable</li>
     *   <li>All other message editor/viewer contexts: Quick set variable (when text is selected)</li>
     * </ul>
     *
     * @param contextMenuEvent  The event that triggered the context menu.
     * @return  A list of the context menu items to be added to the menu.
     */
    @Override
    public List<Component> provideMenuItems(ContextMenuEvent contextMenuEvent) {
        // Only provide items when a message editor or viewer is present
        if (!contextMenuEvent.messageEditorRequestResponse().isPresent()) {
            return null;
        }

        List<Component> contextMenuProviderList = new ArrayList<>();

        // --- Insert variable and Swap: only for MESSAGE_EDITOR_REQUEST ---
        if (contextMenuEvent.isFrom(InvocationType.MESSAGE_EDITOR_REQUEST) && !variablesMap.isEmpty()) {
            // Sort variablesMap keys alphabetically
            List<String> sortedVariablesMapKeys = new ArrayList<>(variablesMap.keySet());
            Collections.sort(sortedVariablesMapKeys);

            // --- "Insert variable" sub-menu ---
            JMenu insertMenu = new JMenu("Insert variable");

            // Iterate through sorted variablesMap keys
            for(String variableKey : sortedVariablesMapKeys) {
                // Create a new JMenuItem with the label containing the variablesMap key
                JMenuItem contextMenuItem = new JMenuItem("((" + variableKey + "))");

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
                // Add the context menu item to the insert sub-menu
                insertMenu.add(contextMenuItem);
            }
            contextMenuProviderList.add(insertMenu);

            // --- "Swap variable to value" menu item ---
            // Resolves ((variableName)) references in the request to their actual values.
            // This is the reverse of what the HTTP handler does at send-time, letting the
            // user preview the resolved request directly in the editor.
            JMenuItem swapVariableToValueItem = new JMenuItem("Swap variable to value");

            swapVariableToValueItem.addActionListener(e -> {
                MessageEditorHttpRequestResponse messageEditor = contextMenuEvent.messageEditorRequestResponse().get();
                String requestString = messageEditor.requestResponse().request().toString();

                // Replace all ((variableName)) references with their resolved values
                String modifiedRequestString = resolveVariables(requestString);

                // Only update the request if replacements were made
                if (!modifiedRequestString.equals(requestString)) {
                    HttpService requestService = messageEditor.requestResponse().request().httpService();
                    HttpRequest modifiedRequest = HttpRequest.httpRequest(requestService, modifiedRequestString);
                    messageEditor.setRequest(modifiedRequest);
                }
            });
            contextMenuProviderList.add(swapVariableToValueItem);
        }

        // --- "Quick set variable" menu item ---
        // Available in any message editor/viewer context when text is selected.
        // Creates a new variable or updates an existing one with the selected text as the value.
        MessageEditorHttpRequestResponse messageEditor = contextMenuEvent.messageEditorRequestResponse().get();
        if (messageEditor.selectionOffsets().isPresent()) {
            JMenuItem quickSetItem = new JMenuItem("Quick set variable");

            quickSetItem.addActionListener(e -> {
                // Determine the displayed message based on the invocation context
                String displayedMessage;
                if (contextMenuEvent.isFrom(
                        InvocationType.MESSAGE_EDITOR_REQUEST,
                        InvocationType.MESSAGE_VIEWER_REQUEST)) {
                    displayedMessage = messageEditor.requestResponse().request().toString();
                } else {
                    if (messageEditor.requestResponse().response() == null) {
                        return;
                    }
                    displayedMessage = messageEditor.requestResponse().response().toString();
                }

                // Extract the selected text using selection offsets
                int startIndex = messageEditor.selectionOffsets().get().startIndexInclusive();
                int endIndex = messageEditor.selectionOffsets().get().endIndexExclusive();
                String selectedText = displayedMessage.substring(startIndex, endIndex);

                // Prompt the user for the variable name
                String variableName = (String) JOptionPane.showInputDialog(
                        SwingUtilities.getWindowAncestor(variablesTab),
                        "Selected value: " + (selectedText.length() > 80
                                ? selectedText.substring(0, 80) + "..."
                                : selectedText)
                                + "\n\nVariable name:",
                        "Quick set variable",
                        JOptionPane.PLAIN_MESSAGE,
                        null, null,
                        null);

                // Create or update the variable if the user provided a name
                if (variableName != null && !variableName.trim().isEmpty()) {
                    String result = variablesTab.addOrUpdateVariable(variableName.trim(), selectedText);
                    boolean isSuccess = result.contains("successfully");
                    JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(variablesTab),
                            result,
                            "Quick set variable",
                            isSuccess ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
                }
            });
            contextMenuProviderList.add(quickSetItem);
        }

        // Return the list or null if no items were added
        return contextMenuProviderList.isEmpty() ? null : contextMenuProviderList;
    }

    /**
     * Replaces each ((variableName)) reference in the given string with the
     * variable's current value from the variables map. References whose key
     * is not found in the map are left unchanged.
     *
     * @param input The string containing variable references.
     * @return The string with variable references resolved to their values.
     */
    private String resolveVariables(String input) {
        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            // Extract the variable name from between (( and ))
            String match = matcher.group();
            String variableName = match.substring(2, match.length() - 2);

            // Look up the variable in the map; if found replace with value, otherwise keep as-is
            VariableData variableData = variablesMap.get(variableName);
            if (variableData != null && !variableData.value().isEmpty()) {
                // Use Matcher.quoteReplacement to handle special characters (e.g. $ and \) in values
                matcher.appendReplacement(result, Matcher.quoteReplacement(variableData.value()));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(match));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }
}