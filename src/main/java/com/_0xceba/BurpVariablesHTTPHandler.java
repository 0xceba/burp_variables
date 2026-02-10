package com._0xceba;

import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * HTTP handler to intercept and modify HTTP requests within Burp.
 */
public class BurpVariablesHTTPHandler implements HttpHandler{
    private final HashMap<String, Boolean> toolsEnabledMap;
    private final HashMap<String, VariableData> variablesMap;
    private final Logging burpLogging;
    private final BurpVariablesTab variablesTab;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\(\\(.+?\\)\\)");

    /**
     * Constructs a new instance of BurpVariablesHTTPHandler.
     *
     * @param burpLogging     The logging interface from the Montoya API.
     * @param variablesMap    HashMap containing variable names and their corresponding VariableData.
     * @param toolsEnabledMap HashMap indicating which tools are enabled or disabled.
     * @param variablesTab    The UI tab for updating table display when variables change.
     */
    public BurpVariablesHTTPHandler(Logging burpLogging, HashMap<String, VariableData> variablesMap, HashMap<String, Boolean> toolsEnabledMap, BurpVariablesTab variablesTab) {
        this.burpLogging = burpLogging;
        this.variablesMap = variablesMap;
        this.toolsEnabledMap = toolsEnabledMap;
        this.variablesTab = variablesTab;
    }

    /**
     * Handles HTTP requests before they are sent from Burp.
     *
     * @param requestToBeSent   HTTP request before it is sent from Burp Suite.
     * @return  Modified HTTP request if variables are replaced, otherwise the original request.
     */
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // Convert the request to a string
        String requestAsString = requestToBeSent.toString();

        // Check if the tool type is enabled and the request contains variables
        if(
            toolsEnabledMap.get(requestToBeSent.toolSource().toolType().toolName())
                && containsVariable(requestAsString)
        ){
            // Continue without modification if the request is from the Proxy tool and not in scope
            if((requestToBeSent.toolSource().toolType().toolName().equals("Proxy"))
                    && !requestToBeSent.isInScope()){
                return RequestToBeSentAction.continueWith(requestToBeSent);
            }

            // Replace the variables in a string copy of the request
            requestAsString = replaceVariables(requestAsString);

            // Create an HttpService instance for the modified request
            HttpService requestService = requestToBeSent.httpService();

            // Create a new HTTP request with the modified string
            HttpRequest modifiedRequest = HttpRequest.httpRequest(requestService, requestAsString);

            // Update Content-Length header for requests with a body
            if (modifiedRequest.body().length() > 0)
                modifiedRequest = modifiedRequest.withBody((modifiedRequest.bodyToString()));

            // Continue with the modified request
            return RequestToBeSentAction.continueWith(modifiedRequest);
        }
        // Continue with the original request
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    /**
     * Handles HTTP responses after they are received by Burp.
     * When auto-update variables is enabled, searches responses for regex matches
     * and updates variable values with the first capture group.
     *
     * @param responseReceived  HTTP response before it is received by Burp.
     * @return  The unmodified HTTP response.
     */
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        // Check if auto-update variables feature is enabled in settings
        Boolean variableAutoUpdateEnabled = toolsEnabledMap.get("variableAutoUpdate");
        if (variableAutoUpdateEnabled != null && variableAutoUpdateEnabled) {
            // Convert response to string for regex matching (includes headers and body)
            String responseAsString = responseReceived.toString();

            // Iterate through all variables to check for regex matches
            for (HashMap.Entry<String, VariableData> entry : variablesMap.entrySet()) {
                String regex = entry.getValue().regex();

                // Skip variables without a regex pattern defined
                if (regex == null || regex.isEmpty()) {
                    continue;
                }

                // Skip invalid regex patterns or patterns without capture groups
                if (!isValidRegexWithCaptureGroup(regex)) {
                    continue;
                }

                try {
                    // Compile and execute the regex against the response
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(responseAsString);

                    // Check if a match was found with at least one capture group
                    if (matcher.find() && matcher.groupCount() > 0) {
                        // Extract the first capture group value
                        String capturedValue = matcher.group(1);
                        if (capturedValue != null) {
                            // Update the variable's value in the map while preserving the regex
                            String variableName = entry.getKey();
                            variablesMap.put(variableName, new VariableData(capturedValue, regex));
                            // Notify the UI tab to refresh the table display
                            variablesTab.updateVariableInTable(variableName, capturedValue);
                            burpLogging.logToOutput("Auto-updated variable '" + variableName + "' to: " + capturedValue);
                        }
                    }
                } catch (PatternSyntaxException e) {
                    // Log error for invalid regex (should not occur due to prior validation)
                    burpLogging.logToError("Invalid regex for variable '" + entry.getKey() + "': " + e.getMessage());
                }
            }
        }
        // Always return the response unmodified; this handler only extracts data
        return ResponseReceivedAction.continueWith(responseReceived);
    }

    /**
     * Checks if a regex pattern is valid and contains at least one capture group.
     *
     * @param regex The regex pattern to validate.
     * @return True if the regex is valid and has at least one capture group, false otherwise.
     */
    private boolean isValidRegexWithCaptureGroup(String regex) {
        try {
            // Attempt to compile the regex; throws PatternSyntaxException if invalid
            Pattern pattern = Pattern.compile(regex);
            // Check if the pattern has at least one capture group using an empty string matcher
            // groupCount() returns the number of capturing groups, excluding group 0 (the entire match)
            return pattern.matcher("").groupCount() > 0;
        } catch (PatternSyntaxException e) {
            // Invalid regex syntax
            return false;
        }
    }

    /**
     * Checks if the HTTP request contains variable indicator characters.
     *
     * @param passedRequestAsString HTTP request converted to a string.
     * @return  True if the request contains variable indicator characters, false otherwise.
     */
    private boolean containsVariable(String passedRequestAsString){
        // Regex to match variable names enclosed in double parentheses
        Matcher matcher = VARIABLE_PATTERN.matcher(passedRequestAsString);

        // Return true if a match is found, otherwise false
        return matcher.find();
    }

    /**
     * Replaces each instance of variables found in the HTTP request.
     * Variables are referenced in the format ((key)).
     *
     * @param passedRequestAsString HTTP request converted to a string.
     * @return  Modified HTTP request with variables replaced.
     */
    private String replaceVariables(String passedRequestAsString){
        // Iterate through the variables map
        for (HashMap.Entry<String, VariableData> entry : variablesMap.entrySet()) {
            // Don't replace if the key is empty
            if(!entry.getKey().isEmpty()) {
                // Replace the variable references in the HTTP request
                // Pattern.quote is used to escape special characters in the key string
                passedRequestAsString = passedRequestAsString.replaceAll(Pattern.quote("((" + entry.getKey() + "))"), entry.getValue().value());
            }
        }
        return passedRequestAsString;
    }
}