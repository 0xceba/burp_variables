# Burp Variables
### Description
This extension extends Burp Suite to support variables. This allows users to store and reuse values in Burp Suite requests. This extension supports referencing values in requests that come from the Repeater, Proxy, Intruder, Scanner, or Extensions tools. Variables are referenced with the notation `((variableName))` and can be included anywhere in a request. Variable data can be copied between disk projects by using Burp's [Import project file](https://portswigger.net/burp/documentation/desktop/projects/manage-project-files#importing-project-files) feature to copy extension data.

### Usage
1. Set the variable name-value pairs in the Variables tab:

    ![Burp Variables tab](burp_variables1.png)
2. Reference the variables in any request, such as in a Repeater request:

    ![Repeater tab](burp_variables2.png)
3. Send the request and confirm that the variable references were replaced by viewing the request in the Logger tool:

   ![Logging tab](burp_variables3.png)