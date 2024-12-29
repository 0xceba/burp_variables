package com._0xceba;

import burp.api.montoya.logging.Logging;
import burp.api.montoya.MontoyaApi;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;

import java.awt.*;
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
    private final DefaultTableModel tableModel;
    private final HashMap<String, Boolean> toolsEnabledMap;
    private final HashMap<String, String> variablesMap;
    private final JTable table;
    private final Logging logging;
    private final MontoyaApi api;

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
     * @param api            the Montoya API interface
     * @param logging        the logging interface from the Montoya API
     * @param variablesMap   HashMap storing variable names and values
     * @param toolsEnabledMap HashMap storing tool names and their enabled status
     */
    public BurpVariablesTab(Logging logging, MontoyaApi api, HashMap<String, String> variablesMap, HashMap<String, Boolean> toolsEnabledMap) {
        this.logging = logging;
        this.api = api;
        this.variablesMap = variablesMap;
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
        this.tableModel = tableModel;

        // Instantiate and configure the JTable
        JTable table = setupTable(tableModel);
        this.table = table;

        // Add table to a JScrollPane for scrolling
        JScrollPane scrollPane = new JScrollPane(table);
        this.add(scrollPane);

        // Instantiate and configure an additional JPanel for buttons
        // Required to horizontally sort buttons
        JPanel buttonPane = setupButtonPane();
        this.add(buttonPane);

        // For new projects, set the default tool enabled selections
        if(toolsEnabledMap.isEmpty())
            setDefaultToolToggleSelections();
    }

    /**
     * Generates and configures the JTable and its properties.
     *
     * @param newTableModel the TableModel interface used to store JTable data
     * @return the prepared JTable object
     */
    private JTable setupTable(DefaultTableModel newTableModel) {
        JTable newTable = new JTable(newTableModel);

        // Disable reordering of headers
        newTable.getTableHeader().setReorderingAllowed(false);

        // Set single selection mode for removing row functionality
        newTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        // Enable cell selection mode
        newTable.setCellSelectionEnabled(true);

        // Allow sorting by column headers
        newTable.setAutoCreateRowSorter(true);

        // Set default sort to "Variable Name" column
        newTable.getRowSorter().toggleSortOrder(0);

        // Commit cell changes on focus loss
        newTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

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
             * @return true if editing has stopped, false otherwise
             */
            @Override
            public boolean stopCellEditing() {
                // When stopped == true the cell editing is finished
                boolean stopped = super.stopCellEditing();

                // Logic block to save the new cell contents to the variables map
                if (stopped) {
                    // String variables which represent the new key:value pair that is being added
                    String newKey = table.getValueAt(editingRow,0).toString();
                    String newValue = table.getValueAt(editingRow,1).toString();

                    // Check for key duplicates
                    if(editingColumn == 0
                            && variablesMap.containsKey(newKey)
                            && !newKey.equals(oldKey)){
                        logging.raiseInfoEvent("Variable with name '" + newKey + "' already exists.");

                        // Remove the new duplicate key row from the table
                        int selectedRow = table.convertRowIndexToModel(editingRow);
                        tableModel.removeRow(selectedRow);

                        // Remove the new duplicate key row from the variables map
                        variablesMap.remove(oldKey);

                        // Exit stopCellEditing()
                        return stopped;
                    }

                    // Remove the outdated key:value pair from the variables map
                    variablesMap.remove(oldKey);

                    // If the key is not empty, then add the new pair to the variables map
                    if(!newKey.isEmpty())
                        variablesMap.put(newKey, newValue);
                }
                return stopped;
            }

            /**
             * Identifies which row is being edited prior to stopping cell editing.
             *
             * @param table     the JTable that is being edited
             * @param value     the value to be edited
             * @param isSelected whether the cell is selected
             * @param row       the row index of the cell
             * @param column    the column index of the cell
             * @return the component that is being edited
             */
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                // Assign the instance variables to be the cell that is being edited
                editingRow = row;
                editingColumn = column;

                // Store the key value before the edit is finished
                oldKey = table.getValueAt(editingRow,0).toString();

                // Resume default behavior; return the component that is being edited
                return super.getTableCellEditorComponent(table, value, isSelected, row, column);
            }
        };

        // Add the custom TableCellEditor to both columns so all cells receive it
        newTable.getColumnModel().getColumn(0).setCellEditor(customEditor);
        newTable.getColumnModel().getColumn(1).setCellEditor(customEditor);

        return newTable;
    }

    /**
     * Generates and configures the button panel and its properties.
     *
     * @return the prepared JPanel object
     */
    private JPanel setupButtonPane() {
        JPanel newButtonPane = new JPanel();

        // Add row button and listener
        JButton addRowButton = new JButton("Add row");
        addRowButton.addActionListener(e ->
        {
            addRowAndSelect();
        });
        newButtonPane.add(addRowButton);

        // Options button and listener
        JButton optionsButton = new JButton("Options");
        optionsButton.addActionListener(e ->
        {
            displayOptions();
        });
        newButtonPane.add(optionsButton);

        // Delete row button and listener
        JButton deleteRowButton = new JButton("Delete row");
        deleteRowButton.addActionListener(e ->
        {
            deleteRow();
        });
        newButtonPane.add(deleteRowButton);

        return newButtonPane;
    }

    /**
     * Adds an empty row to the table and selects it.
     */
    private void addRowAndSelect()
    {
        tableModel.addRow(new Object[]{"", "",});

        // Select the newly inserted row after it is added
        int lastRow = table.convertRowIndexToView(tableModel.getRowCount() - 1);
        table.changeSelection(lastRow, 0, false, false);
    }

    /**
     * Displays the options panel for toggling tool states.
     */
    private void displayOptions()
    {
        // Create options JPanel with BoxLayout for only vertical components
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));

        // Compound border for border and padding
        Border paddingBorder = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        optionsPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.DARK_GRAY), paddingBorder));

        // Create optionsDialog attached to Burp Frame and add optionsPanel contents
        JDialog optionsDialog = new JDialog(api.userInterface().swingUtils().suiteFrame(), "Burp Variables Options", true);
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
        toolToggleTitle.setFont(toolToggleTitle.getFont().deriveFont(Font.BOLD, 13f));
        optionsPanel.add(toolToggleTitle);

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Tool toggle option body label
        optionsPanel.add(new JLabel("Select which tools should match and replace variable references."));

        // Add vertical spacing
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

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
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        optionsPanel.add(new JSeparator());
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Import option h1 label
        JLabel importTitle = new JLabel("Import variables");
        importTitle.setFont(importTitle.getFont().deriveFont(Font.BOLD, 13f));
        optionsPanel.add(importTitle);

        // Vertical spacing
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Import option body labels
        optionsPanel.add(new JLabel("Import variable key:value pairs from a CSV file. The CSV file should"));
        optionsPanel.add(new JLabel("be formatted without a header row. The imported pairs will be"));
        optionsPanel.add(new JLabel("appended to the variables table."));

        // Vertical spacing
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Import option button and listener
        JButton importButton = new JButton("Import variables");
        importButton.addActionListener(e ->
        {
            importCSV();
        });
        optionsPanel.add(importButton);

        // Option separator
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        optionsPanel.add(new JSeparator());
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Export option h1 label
        JLabel exportTitle = new JLabel("Export variables");
        exportTitle.setFont(exportTitle.getFont().deriveFont(Font.BOLD, 13f));
        optionsPanel.add(exportTitle);

        // Vertical spacing
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Export option body label
        optionsPanel.add(new JLabel("Export the current variables table to a CSV file."));

        // Vertical spacing
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Export option button and listener
        JButton exportButton = new JButton("Export variables");
        exportButton.addActionListener(e ->
        {
            exportCSV();
        });
        optionsPanel.add(exportButton);

        // Option separator
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        optionsPanel.add(new JSeparator());
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Clear option h1 label
        JLabel clearTitle = new JLabel("Clear variables");
        clearTitle.setFont(clearTitle.getFont().deriveFont(Font.BOLD, 13f));
        optionsPanel.add(clearTitle);

        // Vertical spacing
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Import option body labels
        optionsPanel.add(new JLabel("Remove all entries from the variables table."));

        // Vertical spacing
        optionsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

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
     * @param container the container whose components should have their focusable property disabled
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
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            // Get row index via convertRowIndexToModel to delete from a sorted table
            int modelRow = table.convertRowIndexToModel(selectedRow);

            // Remove row from the variables map
            variablesMap.remove(tableModel.getValueAt(modelRow, 0).toString());

            // Remove row from table
            tableModel.removeRow(modelRow);
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
            tableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
    }

    /**
     * Derives the Tool Type enum value from the checkbox label.
     *
     * @param toolTitle the label of the tool checkbox
     * @return the corresponding Tool Type enum value, or null if not found
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
        int userSelection = fileChooser.showOpenDialog(null);

        // If the user selected a file to save
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            File fileToImport = fileChooser.getSelectedFile();
            // Initialize a CSVReader object in a try-with-resource statement
            try (CSVReader reader = new CSVReader(new FileReader(fileToImport))) {
                String[] line;
                // Iterate through the CSV file
                while ((line = reader.readNext()) != null) {
                    // Check for duplicate keys
                    if(!variablesMap.containsKey(line[0])) {
                        // Add the first 2 fields of each line to the table model and the variables map
                        tableModel.addRow(new Object[]{line[0], line[1],});
                        variablesMap.put(line[0], line[1]);
                    }
                }
            } catch (IOException | CsvValidationException e) {
                logging.raiseErrorEvent(e.toString());
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
        int userSelection = fileChooser.showSaveDialog(null);

        // If the user selected a file to save
        if (userSelection == JFileChooser.APPROVE_OPTION) {
            // Obtain the file selected by the user
            File fileToExport = fileChooser.getSelectedFile();

            // Check if user is overwriting an existing file
            if (fileToExport.exists()) {
                int response = JOptionPane.showConfirmDialog(null,
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
     * @param fileToExport the file to which the variables will be exported
     */
    private void writeFile(File fileToExport){
        // Initialize a CSVWriter object in a try-with-resource statement
        try (CSVWriter writer = new CSVWriter(new FileWriter(fileToExport))) {
            // Iterate through the variables map and write the fields to the CSVWriter
            for (HashMap.Entry<String, String> entry : variablesMap.entrySet()) {
                writer.writeNext(new String[]{entry.getKey(), entry.getValue()});
            }
        } catch (IOException e) {
            logging.raiseErrorEvent(e.toString());
        }
    }

    /**
     * Clears all variables stored in the variables map and updates the table model.
     */
    private void clearVariables(){
        // Confirm that the user wants to clear the variables table
        int response = JOptionPane.showConfirmDialog(null,
                "Are you sure you want to remove all entries from the variables table? " +
                        "This operation is destructive and non-reversible.",
                "Confirm clear variables", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        // User has confirmed that they want to clear the table
        if (response == JOptionPane.YES_OPTION) {
            // Clear all entries in the variables map
            variablesMap.clear();

            // Remove all rows from the table model
            tableModel.setRowCount(0);
        }
    }
}