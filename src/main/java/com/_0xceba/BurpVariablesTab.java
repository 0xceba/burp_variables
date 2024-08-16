package com._0xceba;

import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.PersistedObject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import burp.api.montoya.MontoyaApi;

/**
 * Provides the extension's UI elements.
 * Includes two JPanels arranged in a BoxLayout with a JTable of the variables.
 * Implements the FocusListener interface to save the table's state when the focus changes.
 */
public class BurpVariablesTab extends JPanel {
    Logging logging;
    PersistedObject persistence;
    JTable table;
    DefaultTableModel tableModel;
    MontoyaApi api;

    // Constant 2d array holding enum class ToolType values and label values
    final String[][] mapToolNameAndToolLabel = {
            {"Repeater", "Repeater"},
            {"Proxy", "Proxy (in-scope requests only)"},
            {"Intruder", "Intruder"},
            {"Scanner", "Scanner"},
            {"Extensions", "Extensions"}
    };

    /**
     * Constructs a BurpVariablesTab.
     *
     * @param logging logging interface from {@link burp.api.montoya.MontoyaApi}
     * @param persistence persistence object from {@link burp.api.montoya.MontoyaApi}
     */
    public BurpVariablesTab(Logging logging, PersistedObject persistence, MontoyaApi api) {
        // Set instance variables
        this.logging = logging;
        this.persistence = persistence;
        this.api = api;

        // Set the panel's layout to BoxLayout aligned along Y-axis
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // Set an empty border to serve as padding around JPanel
        this.setBorder(new EmptyBorder(20, 40, 20, 40));

        String[] columnNames = {
                "Variable name",
                "Variable value"
        };

        // Create the empty tableModel
        DefaultTableModel tableModel = new DefaultTableModel(null, columnNames);
        this.tableModel = tableModel;

        // Instantiate and configure table
        JTable table = setupTable(tableModel);

        // Set table instance variable
        this.table = table;

        // Add table to JScrollPane for scrolling
        JScrollPane scrollPane = new JScrollPane(table);
        this.add(scrollPane);

        // Instantiate and configure additional JPanel for buttons
        // Required to horizontally sort buttons
        JPanel buttonPane = setupButtonPane();
        this.add(buttonPane);

        // For fresh projects, set the default settings panel tool toggle selections
        if(persistence.getBoolean(mapToolNameAndToolLabel[0][0]) == null)
            setSettingsDefaultToolToggleSelections();
    }

    /**
     * Method to generate the table and set its properties.
     *
     * @param newTableModel Copy of the TableModel interface used to store JTable data.
     * @return The prepared JTable object.
     */
    private JTable setupTable(DefaultTableModel newTableModel) {
        JTable newTable = new JTable(newTableModel);

        // Disable reordering of headers
        newTable.getTableHeader().setReorderingAllowed(false);

        // Set single selection mode for remove row functionality
        newTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        // Enable cell selection mode
        newTable.setCellSelectionEnabled(true);

        // Allow sorting by column headers
        newTable.setAutoCreateRowSorter(true);

        // Set default sort to "Variable Name" column
        newTable.getRowSorter().toggleSortOrder(0);

        // Commit cell changes on focus lost
        newTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        // Fill table with persisted data
        populateTable();

        /*
          Create a custom TableCellEditor object with an overridden stopCellEditing
          which saves the table to the PersistedObject any time a cell is modified
         */
        TableCellEditor customEditor = new DefaultCellEditor(new JTextField()) {
            private int editingRow;
            private int editingColumn;
            private String oldKey;

            // Override stopCellEditing to commit table changes to the persistence object
            // after the user loses focuses on a cell they've been editing
            @Override
            public boolean stopCellEditing() {
                // True value means editing has stopped
                boolean stopped = super.stopCellEditing();

                // Start save logic after cell is done being modified
                if (stopped) {
                    // Variables to represent the new key:value pair that is being added
                    String newKey = table.getValueAt(editingRow,0).toString();
                    String newValue = table.getValueAt(editingRow,1).toString();

                    // Logic to prevent duplicate keys
                    if(editingColumn == 0) {
                        // Loop through all table rows
                        for (int i = 0; i < table.getRowCount(); i++) {
                            // Assign a temporary key:value pair for the current row
                            String keyValue = table.getValueAt(i, 0).toString();
                            String valueValue = table.getValueAt(i, 1).toString();

                            // Duplicate row condition: if a looping row has the same key
                            // but different value as the new row
                            if (keyValue != null && keyValue.equals(newKey) && !valueValue.equals(newValue)) {
                                logging.raiseInfoEvent("Variable with name '" + newKey + "' already exists in table.");

                                // Remove the new row from the table
                                int selectedRow = table.convertRowIndexToModel(editingRow);
                                tableModel.removeRow(selectedRow);

                                // Remove the new row from the persistence object
                                persistence.deleteString(oldKey);

                                // Exit stopCellEditing()
                                return stopped;
                            }
                        }
                    }
                    // Delete key:value pair from persistence object
                    persistence.deleteString(oldKey);

                    // Recreate key:value pair in persistence object
                    persistence.setString(newKey,newValue);
                }

                return stopped;
            }

            // Helper function to identify which row is being edited prior to stopCellEditing
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
                // Set private variables of the cell being edited
                editingRow = row;
                editingColumn = column;

                // Set private variable of the key value before edit is complete
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
     * Method to generate the horizontal button panel and set its properties.
     *
     * @return The prepared JPanel object.
     */
    private JPanel setupButtonPane() {
        JPanel newButtonPane = new JPanel();

        // Add row button and listener
        JButton addRowButton = new JButton("Add row");
        addRowButton.addActionListener(e ->
        {
            addRow();
        });
        newButtonPane.add(addRowButton);

        // Settings button and listener
        JButton settingsButton = new JButton("Settings");
        settingsButton.addActionListener(e ->
        {
            displaySettings();
        });
        newButtonPane.add(settingsButton);

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
     * Method to add an empty row to the table.
     */
    private void addRow()
    {
        tableModel.addRow(new Object[]{"", "",});

        // Select the newly inserted row after it is added
        int lastRow = table.convertRowIndexToView(tableModel.getRowCount() - 1);
        table.changeSelection(lastRow, 0, false, false);
    }

    /**
     * Method to display the settings panel.
     */
    private void displaySettings()
    {
        // Create settings JPanel with BoxLayout for only vertical components
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));

        // Compound border for border and padding
        Border paddingBorder = BorderFactory.createEmptyBorder(10, 10, 10, 10);
        settingsPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.DARK_GRAY), paddingBorder));

        // Tool toggle section h1 label
        JLabel toolToggleTitle = new JLabel("Toggle tools");
        toolToggleTitle.setFont(toolToggleTitle.getFont().deriveFont(Font.BOLD, 13f));
        toolToggleTitle.setForeground(Color.WHITE);
        settingsPanel.add(toolToggleTitle);

        // Add vertical spacing
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Tool toggle section h2 label
        JLabel toolToggleSubTitle = new JLabel("Select which tools should match and replace variable references.");
        settingsPanel.add(toolToggleSubTitle);

        // Add vertical spacing
        settingsPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Define an ItemListener to apply to all components of the settings panel
        // to prevent components from taking focus
        ItemListener preventFocusChangeItemListener = new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {

                // Check the source of the event to determine which checkbox was selected
                JCheckBox sourceCheckBox = (JCheckBox) e.getSource();

                // Derive the Tool Type enum value from the checkbox label
                String toolName = lookupToolTypeEnum(sourceCheckBox.getText());

                // Set the boolean persistence object's value if the checkbox is (un)checked
                persistence.setBoolean(toolName, e.getStateChange() == ItemEvent.SELECTED);
            }
        };

        // Create a checkbox for each enum Tool Types
        for (String[] strings : mapToolNameAndToolLabel) {
            JCheckBox checkBox = new JCheckBox(strings[1]);
            settingsPanel.add(checkBox);
            checkBox.addItemListener(preventFocusChangeItemListener);

            // Select the checkboxes whose boolean persistence value is true
            if(persistence.getBoolean(strings[0]))
                checkBox.setSelected(true);
        }

        // Disable focusable on panel components to prevent them from consuming ESC key events
        setAllComponentsNotFocusable(settingsPanel);

        // Create settingsDialog attached to Burp Frame and add settingsPanel contents
        JDialog settingsDialog = new JDialog(api.userInterface().swingUtils().suiteFrame(), "Variables Settings", true);
        settingsDialog.setContentPane(settingsPanel);

        // Allow user to close the settingsDialog window
        settingsDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // Size the settingsDialog object based on its contents
        settingsDialog.pack();

        // Center the settingsDialog dialog window
        settingsDialog.setLocationRelativeTo(null);

        // Add listener to close the settingsDialog dialog window on ESC key press
        settingsDialog.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    settingsDialog.dispose();
                }
            }
        });

        // Show the settingsDialog dialog window
        settingsDialog.setVisible(true);
    }

    /**
     * Method to disable focusable on all the container's components.
     * Does not recursively loop, so nested container components will remain focusable
     */
    private static void setAllComponentsNotFocusable(Container container) {
        Component[] components = container.getComponents();

        for (Component component : components) {
            component.setFocusable(false);
        }
    }

    /**
     * Method to delete the select row from the table.
     */
    private void deleteRow()
    {
        int selectedRow = table.getSelectedRow();
        if (selectedRow != -1) {
            // Get row index via convertRowIndexToModel to delete from a sorted table
            int modelRow = table.convertRowIndexToModel(selectedRow);

            // Remove row from persistence
            persistence.deleteString(tableModel.getValueAt(modelRow, 0).toString());

            // Remove row from table
            tableModel.removeRow(modelRow);
        }
    }

    /**
     * Method to populate the table from the persisted data.
     */
    private void populateTable()
    {
        for (String variableKey : persistence.stringKeys()){
            String variableValue = persistence.getString(variableKey);
            // Add a row for each persisted key/value string
            tableModel.addRow(new Object[]{variableKey, variableValue});
        }
    }

    /**
     * Method to derive the Tool Type enum value from the checkbox label.
     */
    private String lookupToolTypeEnum(String toolTitle){
        // Loops through first element of each inner array
        for (String[] strings : mapToolNameAndToolLabel) {
            // Checks if the second element value matches the checkbox label
            if (strings[1].equals(toolTitle))
                // If so, return the first element value
                return strings[0];
        }
        return null;
    }

    /**
     * Method to set the settings panel's default tool toggle selections.
     * Enables all tools except the proxy tool.
     */
    private void setSettingsDefaultToolToggleSelections(){
        for (String[] strings : mapToolNameAndToolLabel) {
            // Set the boolean value to each ToolType key as true for
            // all selections except the proxy tool
            persistence.setBoolean(strings[0], !strings[0].equals("Proxy"));
        }
    }
}