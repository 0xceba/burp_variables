package com._0xceba;

import burp.api.montoya.http.handler.HttpHandler;
import burp.api.montoya.http.handler.HttpRequestToBeSent;
import burp.api.montoya.http.handler.HttpResponseReceived;
import burp.api.montoya.http.handler.RequestToBeSentAction;
import burp.api.montoya.http.handler.ResponseReceivedAction;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.PersistedObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP handler to intercept HTTP requests.
 */
public class BurpVariablesHTTPHandler implements HttpHandler{
    Logging logging;
    PersistedObject persistence;

    /**
     * Constructs the handler.
     *
     * @param logging logging interface from {@link burp.api.montoya.MontoyaApi}
     * @param persistence persistence object from {@link burp.api.montoya.MontoyaApi}
     */
    public BurpVariablesHTTPHandler(Logging logging, PersistedObject persistence) {
        this.logging = logging;
        this.persistence = persistence;
    }

    /**
     * Type RequestToBeSentAction for requests before they are sent from Burp.
     *
     * @param requestToBeSent HTTP request before it is sent from burp.
     * @return The modified HTTP request or null if unmodified.
     */
    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent requestToBeSent) {
        // Convert the request into a new string
        String requestAsString = requestToBeSent.toString();

        // Check if the tool type is enabled by checking that the tool's persistence boolean value == true
        if(
            persistence.getBoolean(requestToBeSent.toolSource().toolType().toolName())
                    && containsVariable(requestAsString)
        ){
            // Do not match and replace if request from proxy is not in scope
            if((requestToBeSent.toolSource().toolType().toolName().equals("Proxy"))
                    && !requestToBeSent.isInScope()){
                return RequestToBeSentAction.continueWith(requestToBeSent);
            }

            // Replace the variables in a string copy of the request
            requestAsString = replaceVariables(requestAsString);

            // Create an object of type HttpService for the modified request
            HttpService requestService = requestToBeSent.httpService();

            // Create a new HTTP request
            HttpRequest modifiedRequest = HttpRequest.httpRequest(requestService, requestAsString);

            // Update Content-Length header for requests that have a body
            if (modifiedRequest.body().length() > 0)
                modifiedRequest = modifiedRequest.withBody((modifiedRequest.bodyToString()));

            // Send the new HTTP request
            return RequestToBeSentAction.continueWith(modifiedRequest);
        }
        // Request is unmodified
        return RequestToBeSentAction.continueWith(requestToBeSent);
    }

    /**
     * Type handleHttpResponseReceived for responses before they are received by Burp.
     * Unused; responses are not modified.
     *
     * @param responseReceived HTTP response before it is received by Burp.
     * @return The unmodified HTTP response.
     */
    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived responseReceived) {
        return ResponseReceivedAction.continueWith(responseReceived);
    }

    /**
     * Method to check if the request contains variable indicator characters.
     *
     * @param passedRequestAsString HTTP request converted to a string.
     * @return True boolean if the request contains variable indicator characters. False if not found.
     */
    private boolean containsVariable(String passedRequestAsString){
        // Regex to match variable names that include alphanumeric and special characters
        Pattern pattern = Pattern.compile("\\(\\([a-zA-Z0-9!@#$%^&*_+=`:;'<>,.\" -]+\\)\\)");
        Matcher matcher = pattern.matcher(passedRequestAsString);

        // Return true/false if matcher finds a match or not
        return matcher.find();
    }

    /**
     * Method to replace each instance of variables found in the request.
     * Variables are identified as ((KEY)).
     *
     * @param passedRequestAsString HTTP request converted to a string.
     * @return Modified HTTP request with variable replacement.
     */
    private String replaceVariables(String passedRequestAsString){
        for (String key : persistence.stringKeys()) {
            // Replaces each key in place (with prepended/appended parenthesis) with the key's value
            passedRequestAsString = passedRequestAsString.replaceAll("\\(\\(" + key + "\\)\\)",persistence.getString(key));
        }
        return passedRequestAsString;
    }
}