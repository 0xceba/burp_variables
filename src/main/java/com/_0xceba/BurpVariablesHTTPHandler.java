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

/**
 * HTTP handler to intercept and modify HTTP requests within Burp.
 */
public class BurpVariablesHTTPHandler implements HttpHandler{
    private HashMap<String, Boolean> toolsEnabledMap;
    private HashMap<String, String> variablesMap;
    private Logging burpLogging;

    /**
     * Constructs a new instance of BurpVariablesHTTPHandler.
     *
     * @param burpLogging     The logging interface from the Montoya API.
     * @param variablesMap    HashMap containing variable names and their corresponding values.
     * @param toolsEnabledMap HashMap indicating which tools are enabled or disabled.
     */
    public BurpVariablesHTTPHandler(Logging burpLogging, HashMap<String, String> variablesMap, HashMap<String, Boolean> toolsEnabledMap) {
        this.burpLogging = burpLogging;
        this.variablesMap = variablesMap;
        this.toolsEnabledMap = toolsEnabledMap;
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
     * Handles HTTP responses before they are received by Burp.
     * Responses are not modified but are used to update the variable values based on the given lookup.
     *
     * @param responseReceived  HTTP response before it is received by Burp.
     * @return  The unmodified HTTP response.
     */
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        // Convert the response to a string
        String responseAsString = responseReceived.toString();

        // Update the value of each variable using response. 
        refreshValuesUsingLookup(responseAsString);

        return ResponseReceivedAction.continueWith(responseReceived);
    }

    /**
     * Checks if the HTTP request contains variable indicator characters.
     *
     * @param passedRequestAsString HTTP request converted to a string.
     * @return  True if the request contains variable indicator characters, false otherwise.
     */
    private boolean containsVariable(String passedRequestAsString){
        // Regex to match variable names enclosed in double parentheses
        Pattern pattern = Pattern.compile("\\(\\(.+\\)\\)");
        Matcher matcher = pattern.matcher(passedRequestAsString);

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
        // Iterate through the storage object
        for (HashMap.Entry<String, String> entry : variablesMap.entrySet()) {
            // Don't replace if the key is empty
            if(!entry.getKey().isEmpty()) {
                // Replace the variable references in the HTTP request
                // Pattern.quote is used to escape special characters in the key string
                String[] values = entry.getValue().split(",", 2);
                passedRequestAsString = passedRequestAsString.replaceAll(Pattern.quote("((" + entry.getKey() + "))"), values[0]);
            }
        }
        return passedRequestAsString;
    }

    /**
     * Updates the value of each variable based on the given lookup, using the response data.
     * Values are replaced according to matched value of the given lookup.
     *
     * @param receivedResponseAsString HTTP response converted to a string.
     * @return HTTP response.
     */
    private String refreshValuesUsingLookup(String receivedResponseAsString) {
        // Iterate through the storage object
        for (HashMap.Entry<String, String> entry : variablesMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Don't update if the key is empty
            if (key.isEmpty()) {
                continue;
            }

            // Don't update if there's no lookup
            String[] values = value.split(",", 2);
            if (values.length < 2) {
                continue;
            }

            // Don't update if lookup is empty
            String lookup = values[1].trim();
            if (lookup.isEmpty()) {
                continue;
            }

            // Get new value from response based on the given lookup
            Pattern pattern = Pattern.compile(lookup, Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(receivedResponseAsString);
            if (matcher.find()) {
                // Update the variable value
                values[0] = matcher.group(1).trim();
                variablesMap.put(entry.getKey(), String.join(",", values));
            }
        }
        return receivedResponseAsString;
    }
}