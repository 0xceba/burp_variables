package com._0xceba;

import burp.api.montoya.logging.Logging;
import burp.api.montoya.MontoyaApi;

import burp.api.montoya.ui.Theme;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.*;

/**
 * Provides the extension's UI elements.
 * This class extends JPanel and includes two JPanels arranged in a BoxLayout with a JTable for displaying variables.
 * It also contains logic to handle variable persistence and user interactions.
 */
public class BurpVariablesTab extends JPanel {
    private DefaultTableModel variablesTableModel;
    private Frame burpFrame;
    private HashMap<String, Boolean> toolsEnabledMap;
    private HashMap<String, String> variablesMap;
    private JTable variablesTable;
    private Logging burpLogging;
    private MontoyaApi montoyaApi;

    // Constant 2D array holding enum class ToolType values and corresponding label values
    final String[][] mapToolNameAndToolLabel = {
            {"Repeater", "Repeater"},
            {"Proxy", "Proxy (in-scope requests only)"},
            {"Intruder", "Intruder"},
            {"Scanner", "Scanner"},
            {"Extensions", "Extensions"}
    };

    /**
     * Constructs a BurpVariablesTab with the specified parameters.
     *
     * @param montoyaApi        The Montoya API interface.
     * @param burpLogging       The logging interface from the Montoya API.
     * @param variablesMap      HashMap storing variable names and values.
     * @param toolsEnabledMap   HashMap storing tool names and their enabled status.
     */
    public BurpVariablesTab(MontoyaApi montoyaApi, Logging burpLogging, HashMap<String, String> variablesMap, HashMap<String, Boolean> toolsEnabledMap) {
        this.burpLogging = burpLogging;
        this.montoyaApi = montoyaApi;
        this.variablesMap = variablesMap;
        this.burpFrame = montoyaApi.userInterface().swingUtils().suiteFrame();
        this.toolsEnabledMap = toolsEnabledMap;

        // Set the panel's layout to BoxLayout aligned along the Y-axis
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Set an empty border to serve as padding around the JPanel
        this.setBorder(new EmptyBorder(20, 40, 20, 40));

        String[] columnNames = {
                "Variable name",
                "Variable value"
        };

        // Create the table model with column names
        DefaultTableModel tableModel = new DefaultTableModel(null, columnNames);
        this.variablesTableModel = tableModel;

        // Instantiate and configure the JTable
        JTable table = setupTable(tableModel);
        this.variablesTable = table;

        // Add table to a JScrollPane for scrolling
        JScrollPane scrollPane = new JScrollPane(table);
        this.add(scrollPane);

        // Instantiate and configure the footer panel
        JPanel footerPanel = setupFooterPanel();
        this.add(footerPanel);

        // For new projects, set the default tool enabled selections
        if(toolsEnabledMap.isEmpty())
            setDefaultToolToggleSelections();
    }

    /**
     * Generates and configures the JTable and its properties.
     *
     * @param tableModel    The TableModel interface used to store JTable data.
     * @return The prepared JTable object.
     */
    private JTable setupTable(DefaultTableModel tableModel) {
        JTable variablesTable = new JTable(tableModel);

        // Disable reordering of headers
        variablesTable.getTableHeader().setReorderingAllowed(false);

        // Set single selection mode for removing row functionality
        variablesTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        // Allow sorting by column headers
        variablesTable.setAutoCreateRowSorter(true);

        // Set default sort to "Variable Name" column
        variablesTable.getRowSorter().toggleSortOrder(0);

        // Commit cell changes on focus loss
        variablesTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Fill the table with persisted data
        populateTable();

        // Create a custom TableCellEditor to save the table state when a cell is modified
        TableCellEditor customEditor = new DefaultCellEditor(new JTextField()) {
            private int editingRow;
            private int editingColumn;
            private String oldKey;

            /**
             * Stops cell editing and commits changes to the variablesMap HashMap.
             * This method is called when the user finishes editing a cell in the JTable.
             * It ensures that the changes are persisted and handles duplicate key prevention.
             *
             * @return  True if editing has stopped, false otherwise.
             */
            @Override
            public boolean stopCellEditing() {
                // When stopped == true the cell editing is finished
                boolean stopped = super.stopCellEditing();

                // Logic block to save the new cell contents to the variables map
                if (stopped) {
                    // String variables which represent the new key:value pair that is being added
                    String newKey = BurpVariablesTab.this.variablesTable.getValueAt(editingRow, 0).toString();
                    String newValue = BurpVariablesTab.this.variablesTable.getValueAt(editingRow, 1).toString();

                    // Start key validation if user is modifying a key
                     if(editingColumn == 0
                            // Check if the new key is empty
                            && newKey.isEmpty()
                            // Check if the new key already exists in a different row
                            || (variablesMap.containsKey(newKey)
                                // Disregard cases when user is modifying the same key row
                                && !newKey.equals(oldKey))){
                         burpLogging.raiseInfoEvent("Unable to save modified variable because the variable name is empty or already exists.");

                        // Remove the new duplicate key row from the table
                        int selectedRow = BurpVariablesTab.this.variablesTable.convertRowIndexToModel(editingRow);
                        variablesTableModel.removeRow(selectedRow);

                        // Remove the new duplicate key row from the variables map
                        variablesMap.remove(oldKey);

                        // Exit stopCellEditing()
                        return stopped;
                    }

                    // Remove the outdated key:value pair from the variables map
                    variablesMap.remove(oldKey);

                    // Add the new pair to the variables map
                    variablesMap.put(newKey, newValue);
                }
                return stopped;
            }

            /**
             * Identifies which row is being edited prior to stopping cell editing.
             *
             * @param table         The JTable that is being edited.
             * @param value         The value to be edited.
             * @param isSelected    Whether the cell is selected.
             * @param row           The row index of the cell.
             * @param column        The column index of the cell.
             * @return  The component that is being edited.
             */
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                // Assign the instance variables to be the cell that is being edited
                editingRow = row;
                editingColumn = column;

                // Store the key value before the edit is finished
                oldKey = table.getValueAt(editingRow, 0).toString();

                // Resume default behavior; return the component that is being edited
                return super.getTableCellEditorComponent(table, value, isSelected, row, column);
            }
        };

        // Add the custom TableCellEditor to both columns so all cells receive it
        variablesTable.getColumnModel().getColumn(0).setCellEditor(customEditor);
        variablesTable.getColumnModel().getColumn(1).setCellEditor(customEditor);

        return variablesTable;
    }

    /**
     * Sets up a footer panel for the user interface, containing
     * nested panels for adding variables and delete/options buttons.
     *
     * @return The footer JPanel object.
     */
    private JPanel setupFooterPanel() {
        // Create footer JPanel with GridLayout for horizontal components
        JPanel footerPanel = new JPanel(new GridLayout(1, 2, 10, 0));

        // Set footer panel size to maximum width by 150 height to display add variables panel cleanly
        footerPanel.setPreferredSize(new Dimension(Integer.MAX_VALUE, 150));
        footerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE,150));

        // Create add variables panel with GridBagLayout so components can stretch across grids
        JPanel addVariablesPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        // Add panel to the footer panel
        footerPanel.add(addVariablesPanel);

        // Outer border to pad components
        Border footerPanelsOuterPaddingBorder = BorderFactory.createEmptyBorder(5, 0, 0, 0);
        // Visible border that uses the application's JTable border color codes for visual continuity
        Border addVariablesPanelVisibleBorder = montoyaApi.userInterface().currentTheme().equals(Theme.LIGHT)
                ? BorderFactory.createLineBorder(new Color(182, 182, 182), 1)
                : BorderFactory.createLineBorder(new Color(96, 96, 96), 1);
        // Inner border to pad components
        Border addVariablesPanelInnerPaddingBorder = BorderFactory.createEmptyBorder(0, 5, 0, 5);
        // Create a compound border by combining padding and visible borders
        Border innerCompound = new CompoundBorder(addVariablesPanelVisibleBorder, addVariablesPanelInnerPaddingBorder);
        Border fullBorder = new CompoundBorder(footerPanelsOuterPaddingBorder, innerCompound);
        // Apply the full border to the addVariablesPanel
        addVariablesPanel.setBorder(fullBorder);

        // Set the horizontal stretching behavior to allow components to stretch horizontally
        gbc.fill = GridBagConstraints.HORIZONTAL;
        // Set padding insets around components
        gbc.insets = new Insets(5, 5, 5, 5);

        // Set component position to 0,0
        gbc.gridx = 0;
        gbc.gridy = 0;
        // Add centered label to the panel at specified grid position
        addVariablesPanel.add(new JLabel("Variable name", SwingConstants.CENTER), gbc);

        // Set component position to 1,0
        gbc.gridx = 1;
        // Add centered label to the panel at specified grid position
        addVariablesPanel.add(new JLabel("Variable value", SwingConstants.CENTER), gbc);

        // Allow component to expand horizontally
        gbc.weightx = 1.0;
        // Set component position to 0,1
        gbc.gridx = 0;
        gbc.gridy = 1;
        // Create variable name text field
        JTextField variableNameField = new JTextField();
        // Set a fixed width for the text field
        variableNameField.setColumns(32);
        // Add text field to the panel at the specified grid position
        addVariablesPanel.add(variableNameField, gbc);

        // Set component position to 1,1
        gbc.gridx = 1;
        gbc.gridy = 1;
        // Create variable value text field
        JTextField variableValueField = new JTextField();
        // Set a fixed width for the text field
        variableValueField.setColumns(32);
        // Add text field to the panel at the specified grid position
        addVariablesPanel.add(variableValueField, gbc);

        // Add variables button
        JButton addVariableButton = new JButton("Add variable");
        // Disallow component from expanding horizontally
        gbc.fill = GridBagConstraints.NONE;
        // Set component to span one column only
        gbc.gridwidth = 0;
        // Set component position to 0,2
        gbc.gridx = 0;
        gbc.gridy = 2;
        // Add button to the panel at the specified grid position
        addVariablesPanel.add(addVariableButton, gbc);

        // Create an ActionListener for adding a variable when an add event occurs (click or "enter" key)
        ActionListener addVariableListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Call addVariable and clear both text fields if the variable is added successfully
                if(addVariable(variableNameField.getText(), variableValueField.getText())) {
                    variableNameField.setText("");
                    variableValueField.setText("");
                    // Move focus to the variable name field for the next input
                    variableNameField.requestFocusInWindow();
                }
            }
        };
        // Add the action listener to the text fields, so it is triggered by the "enter" key
        variableNameField.addActionListener(addVariableListener);
        variableValueField.addActionListener(addVariableListener);
        // Add the action listener to the add variables button
        addVariableButton.addActionListener(addVariableListener);

        // Create buttons panel
        JPanel buttonsPanel = new JPanel();
        // Add panel to the footer panel
        footerPanel.add(buttonsPanel);
        // Apply padding border to buttons panel
        buttonsPanel.setBorder(footerPanelsOuterPaddingBorder);

        // Delete row button and listener
        JButton deleteRowButton = new JButton("Delete selected variable");
        deleteRowButton.addActionListener(e ->
        {
            deleteRow();
        });
        buttonsPanel.add(deleteRowButton);

        // Options button and listener
        JButton optionsButton = new JButton("⚙ Options");
        optionsButton.addActionListener(e ->
        {
            displayOptions();
        });
        buttonsPanel.add(optionsButton);

        return footerPanel;
    }

    /**
     * Adds a new variable to the table and the variables map if valid.
     * Validates that the variable key is not empty and does not already exist in the map.
     *
     * @param variableKey   Variable name key.
     * @param variableValue Variable value
     * @return  True if the variable is added successfully, false otherwise.
     */
    private boolean addVariable(String variableKey, String variableValue)
    {
        // Check if the variable key is not empty and does not already exist in the variables map
        if(!variableKey.isEmpty() && !variablesMap.containsKey(variableKey)) {
            // Add a new row to the variables table with the variable's key and value
            variablesTableModel.addRow(new Object[]{variableKey, variableValue});
            // Update the variables map with the new key:value pair
            variablesMap.put(variableKey, variableValue);
            return true;
        }
        burpLogging.raiseInfoEvent("Unable to add variable because the variable name is empty or already exists.");
        return false;
    }

    /**
     * Displays the options panel for toggling tool states.
     */
    private void displayOptions()
    {
        // Vertical spacing constant of 10 pixels
        final Dimension VERTICAL_SPACING = new Dimension(0, 10);

        // Create options JPanel with BoxLayout for only vertical components
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

        // Compound border for border and padding
        Border paddingBorder = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        optionsPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.DARK_GRAY), paddingBorder));

        // Create optionsDialog attached to Burp Frame and add optionsPanel contents
        JDialog optionsDialog = new JDialog(burpFrame, "Burp variables options", false);
        optionsDialog.setContentPane(optionsPanel);

        // Permits users to close the optionsDialog window
        optionsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Add listener to close the optionsDialog dialog window on ESC key press
        optionsDialog.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    optionsDialog.dispose();
                }
            }
        });

        // Tool toggle option h1 label
        JLabel toolToggleTitle = new JLabel("Toggle tools");
        toolToggleTitle.setFont(toolToggleTitle.getFont().deriveFont(Font.BOLD));
        optionsPanel.add(toolToggleTitle);

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Tool toggle option body label
        optionsPanel.add(new JLabel("Select which tools should match and replace variable references."));

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Define an ItemListener to apply to all components of the options panel; prevents components from taking focus
        ItemListener preventFocusChangeItemListener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {

                // Check the source of the event to determine which checkbox was selected
                JCheckBox sourceCheckBox = (JCheckBox) e.getSource();

                // Derive the ToolType enum value from the checkbox label
                String toolName = lookupToolTypeEnum(sourceCheckBox.getText());

                // Set the tool's state in the tools map
                toolsEnabledMap.put(toolName, e.getStateChange() == ItemEvent.SELECTED);
            }
        };

        // Create a checkbox for each tool
        for (String[] strings : mapToolNameAndToolLabel) {
            JCheckBox checkBox = new JCheckBox(strings[1]);
            optionsPanel.add(checkBox);
            checkBox.addItemListener(preventFocusChangeItemListener);

            // Select (check) the checkboxes whose value is true from the tools map
            if(toolsEnabledMap.get(strings[0]))
                checkBox.setSelected(true);
        }

        // Option separator
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));
        optionsPanel.add(new JSeparator());
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Import option h1 label
        JLabel importTitle = new JLabel("Import variables");
        importTitle.setFont(importTitle.getFont().deriveFont(Font.BOLD));
        optionsPanel.add(importTitle);

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Import option body labels
        optionsPanel.add(new JLabel("Import variable key:value pairs from a CSV file. The CSV file should"));
        optionsPanel.add(new JLabel("be formatted without a header row. The imported pairs will be"));
        optionsPanel.add(new JLabel("appended to the variables table."));

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Import option button and listener
        JButton importButton = new JButton("Import variables");
        importButton.addActionListener(e ->
        {
            importCSV();
            // Return focus to the options dialog
            optionsDialog.toFront();
        });
        optionsPanel.add(importButton);

        // Option separator
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));
        optionsPanel.add(new JSeparator());
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Export option h1 label
        JLabel exportTitle = new JLabel("Export variables");
        exportTitle.setFont(exportTitle.getFont().deriveFont(Font.BOLD));
        optionsPanel.add(exportTitle);

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Export option body label
        optionsPanel.add(new JLabel("Export the current variables table to a CSV file."));

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Export option button and listener
        JButton exportButton = new JButton("Export variables");
        exportButton.addActionListener(e ->
        {
            // Return focus to the options dialog
            exportCSV();
        });
        optionsPanel.add(exportButton);

        // Option separator
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));
        optionsPanel.add(new JSeparator());
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Clear option h1 label
        JLabel clearTitle = new JLabel("Clear variables");
        clearTitle.setFont(clearTitle.getFont().deriveFont(Font.BOLD));
        optionsPanel.add(clearTitle);

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Import option body labels
        optionsPanel.add(new JLabel("Remove all entries from the variables table."));

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(VERTICAL_SPACING));

        // Clear option button and listener
        JButton clearButton = new JButton("Clear variables");
        clearButton.addActionListener(e ->
        {
            clearVariables();
        });
        optionsPanel.add(clearButton);

        // Disable focusable on panel components to prevent checkboxes from consuming ESC key events
        setAllComponentsNotFocusable(optionsPanel);

        // Set the size of optionsDialog based on its contents
        optionsDialog.pack();

        // Center the optionsDialog dialog window
        optionsDialog.setLocationRelativeTo(null);

        // Display the optionsDialog dialog window
        optionsDialog.setVisible(true);
    }

    /**
     * Disables focusable property on all components of the given container.
     * Note: This method does not recursively disable nested container components.
     *
     * @param container The container whose components should have their focusable property disabled.
     */
    private static void setAllComponentsNotFocusable(Container container) {
        Component[] components = container.getComponents();

        for (Component component : components) {
            component.setFocusable(false);
        }
    }

    /**
     * Deletes the selected row from the table model and from the tools map.
     */
    private void deleteRow()
    {
        int selectedRow = variablesTable.getSelectedRow();
        if (selectedRow != -1) {
            // Get row index via convertRowIndexToModel to delete from a sorted table
            int modelRow = variablesTable.convertRowIndexToModel(selectedRow);

            // Remove row from the variables map
            variablesMap.remove(variablesTableModel.getValueAt(modelRow, 0).toString());

            // Remove row from table
            variablesTableModel.removeRow(modelRow);
        }
    }

    /**
     * Populates the table with data from the variables map.
     */
    private void populateTable()
    {
        // Iterate through the variables map
        for (HashMap.Entry<String, String> entry : variablesMap.entrySet()) {
            // Add a row for each key:value pair from the variables map
            variablesTableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
    }

    /**
     * Derives the Tool Type enum value from the checkbox label.
     *
     * @param toolTitle The label of the tool checkbox.
     * @return  The corresponding Tool Type enum value, or null if not found.
     */
    private String lookupToolTypeEnum(String toolTitle){
        // Loops through first element of each inner array
        for (String[] strings : mapToolNameAndToolLabel) {
            // Checks if the second element value matches the checkbox label
            if (strings[1].equals(toolTitle))
                // Returns the first element value
                return strings[0];
        }
        return null;
    }

    /**
     * Sets the default tool toggle selections in the tools map.
     */
    private void setDefaultToolToggleSelections(){
        for (String[] strings : mapToolNameAndToolLabel) {
            // Enable all tools by default except for the "Proxy" tool
            toolsEnabledMap.put(strings[0], !strings[0].equals("Proxy"));
        }
    }

    /**
     * Imports variables from a CSV file into the variables map and table model.
     * This method opens a file chooser dialog to let the user select a CSV file for import.
     * It reads the CSV file and adds the variables to the map and table if they do not already exist.
     */
    private void importCSV(){
        // Create a file chooser
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Import variables from a CSV file");

        // Set a file filter to show only .csv files
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV files", "csv");
        fileChooser.setFileFilter(filter);

        // Open an open dialog window and wait for the user to select a file or cancel
        int userSelection = fileChooser.showOpenDialog(burpFrame);

        // If the user selected a file to save
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToImport = fileChooser.getSelectedFile();
            // Initialize a CSVReader object in a try-with-resource statement
            try (CSVReader reader = new CSVReader(new FileReader(fileToImport))) {
                String[] line;
                // Iterate through the CSV file
                while ((line = reader.readNext()) != null) {
                    // Validate and create a new variable with the first 2 fields of each line
                    addVariable(line[0], line[1]);
                }
            } catch (IOException | CsvValidationException e) {
                burpLogging.raiseErrorEvent(e.toString());
            }
        }
    }

    /**
     * Exports the variables stored in the variables map to a CSV file.
     * This method opens a file chooser dialog to let the user select a location to save the CSV file.
     * If the selected file already exists, the user is prompted to confirm overwriting the file.
     */
    private void exportCSV(){
        // Create a file chooser
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Export variables to a CSV file");

        // Open a save dialog window and wait for the user to select a file or cancel
        int userSelection = fileChooser.showSaveDialog(burpFrame);

        // If the user selected a file to save
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            // Obtain the file selected by the user
            File fileToExport = fileChooser.getSelectedFile();

            // Check if user is overwriting an existing file
            if (fileToExport.exists()) {
                int response = JOptionPane.showConfirmDialog(
                        burpFrame,
                        "The file already exists. Do you want to replace it?",
                        "Confirm overwrite", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                // User has elected to overwrite the existing file, proceed with write operation
                if (response == JOptionPane.YES_OPTION) {
                    writeFile(fileToExport);
                }
                else {
                    // User elected not to overwrite the existing file; call the method again to open a new file chooser dialog
                    exportCSV();
                }
            } else {
                // File does not exist, proceed with write operation
                writeFile(fileToExport);
            }
        }
    }

    /**
     * Writes the variables stored in the variables map to a specified file in CSV format.
     * This method uses a try-with-resources statement to ensure the CSVWriter is closed automatically.
     *
     * @param fileToExport  The file to which the variables will be exported.
     */
    private void writeFile(File fileToExport){
        // Initialize a CSVWriter object in a try-with-resource statement
        try (CSVWriter writer = new CSVWriter(new FileWriter(fileToExport))) {
            // Iterate through the variables map and write the fields to the CSVWriter
            for (HashMap.Entry<String, String> entry : variablesMap.entrySet()) {
                writer.writeNext(new String[]{entry.getKey(), entry.getValue()});
            }
        } catch (IOException e) {
            burpLogging.raiseErrorEvent(e.toString());
        }
    }

    /**
     * Clears all variables stored in the variables map and updates the table model.
     */
    private void clearVariables(){
        // Confirm that the user wants to clear the variables table
        int response = JOptionPane.showConfirmDialog(
                burpFrame,
                "Are you sure you want to remove all entries from the variables table? " +
                        "This operation is destructive and non-reversible.",
                "Confirm clear variables",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        // User has confirmed that they want to clear the table
        if (response == JOptionPane.YES_OPTION) {
            // Clear all entries in the variables map
            variablesMap.clear();

            // Remove all rows from the table model
            variablesTableModel.setRowCount(0);
        }
    }
}