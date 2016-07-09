import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;

import javax.swing.*;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class PHPCIGenerateModels extends AnAction {

    /**
     * Struct for needed column info.
     */
    private class ColInfo {

        private final String name;
        private final String type;
        private final boolean nullable;

        public ColInfo(String name, String type, boolean nullable) {
            this.name = name;
            this.type = type;
            this.nullable = nullable;
        }

    }

    /** Errors will be printed in IntelliJ Event Log window. Infos are visible in Log file (Help->Show Log in Finder/Explorer) */
    private static final com.intellij.openapi.diagnostic.Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance(PHPCIGenerateModels.class.getName());

    @Override
    public void actionPerformed(AnActionEvent e) {
        final Project project = e.getData(CommonDataKeys.PROJECT);
        if (project == null) {
            LOG.error("Project not found");
            return;
        }
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            LOG.error("MySQL JDBC Driver not found");
            return;
        }
        VirtualFile modelsFolder = project.getBaseDir().findFileByRelativePath("photobooth/models");
        if (modelsFolder == null) {
            LOG.error("Model folder not found");
            return;
        }
        Configuration state = Configuration.getInstance(project);
        state.initIfDefault(); // workaround: Force xml generation of intelliJ!
        try {
            List<String> tables = new LinkedList<>();
            // get all tables
            Connection connection = DriverManager.getConnection(state.dbUrl, state.dbUsername, state.dbPassword);
            DatabaseMetaData md = connection.getMetaData();
            ResultSet rs = md.getTables(null, null, "%", null);
            while (rs.next()) {
                tables.add(0, rs.getString(3));
            }
            // get all current models
            List<String> models = new LinkedList<>();
            for (VirtualFile model : modelsFolder.getChildren()) {
                models.add(0, model.getName().substring(0, model.getName().length() - "_model.php".length()));
            }
            // remove tables with models
            tables.removeAll(models);

            // show tables without a model with checkbox
            final DialogBuilder builder = new DialogBuilder(project);
            builder.addOkAction();
            builder.addCancelAction();
            JPanel missingModelsPanel = new JPanel();
            missingModelsPanel.setLayout(new BoxLayout(missingModelsPanel, BoxLayout.Y_AXIS));
            missingModelsPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
            missingModelsPanel.add(Box.createHorizontalGlue());
            List<JCheckBox> checkboxes = new LinkedList<>();
            for (String missingModel : tables) {
                JCheckBox checkbox = new JCheckBox(missingModel);
                checkbox.setName(missingModel);
                checkboxes.add(0, checkbox);
                missingModelsPanel.add(checkbox);
            }
            JBScrollPane scrollPane = new JBScrollPane(missingModelsPanel);
            builder.setCenterPanel(scrollPane);
            builder.setTitle("PHP CI - Generate missing models");
            final int exitCode = builder.show();

            if (exitCode == DialogWrapper.OK_EXIT_CODE) {
                AccessToken token = WriteAction.start();
                try {
                    // for all selected checkbox -> generate _model php file.
                    for (JCheckBox checkbox : checkboxes) {
                        if (checkbox.isSelected()) {
                            String missingModelName = checkbox.getName();
                            Statement stmt = connection.createStatement();
                            rs = stmt.executeQuery(String.format("SELECT * FROM %s LIMIT 1 ", missingModelName));
                            ResultSetMetaData rsmd = rs.getMetaData();
                            int columnCount = rsmd.getColumnCount();

                            List<ColInfo> colsInfo = new ArrayList<>(columnCount);

                            String softDelete = "FALSE";

                            // get column infos and set softDelete to TRUE  if "deleted" exists in cols.
                            for (int i = 1; i <= columnCount; i++) {
                                if (rsmd.getColumnName(i).equals("deleted")) softDelete = "TRUE";
                                colsInfo.add(new ColInfo(rsmd.getColumnName(i), rsmd.getColumnTypeName(i), rsmd.isNullable(i) == ResultSetMetaData.columnNullable));
                            }

                            VirtualFile modelFile = modelsFolder.createChildData(null, missingModelName + "_model.php");

                            List<String> properties = new LinkedList<>();
                            for (ColInfo colInfo : colsInfo) {
                                properties.add(0, String.format(" * @property %s %s", getType(colInfo.type, colInfo.nullable), colInfo.name));
                            }

                            String content =
                                    "<?php if ( ! defined('BASEPATH')) exit('No direct script access allowed');\n" +
                                            "/**\n" +
                                            " *\n" +
                                            String.join("\n", properties) + "\n" +
                                            " *\n" +
                                            " * " + missingModelName + "\n" +
                                            " *\n" +
                                            " * @author " + state.author + "\n" +
                                            " * @copyright 2016 Performed\n" +
                                            " */\n" +
                                            "class " + this.capitalizeFirstLetter(missingModelName) + "_model" + " extends PB_Model \n" +
                                            "{\n" +
                                            "\n" +
                                            "    /** @var string */\n" +
                                            "    public $_table = '" + missingModelName + "';\n" +
                                            "    \n" +
                                            "    /** @var bool */\n" +
                                            "    protected $soft_delete = " + softDelete + ";\n" +
                                            "\n" +
                                            "}";

                            modelFile.setWritable(true);
                            modelFile.setBinaryContent(content.getBytes());
                        }
                    }
                } finally {
                    token.finish();
                }
            }
        } catch (SQLException|IOException e1) {
            LOG.error(e1);
        }
    }

    /**
     *
     * Map Mysql Type to PHP Type
     *
     * @param p Mysql Type name
     * @param nullable Is nullable?
     * @return PHP Type name
     */
    private String getType(String p, boolean nullable) {

        final String[] stringTypes = new String[] { "CHAR", "VARCHAR", "BLOB", "TEXT", "ENUM", "SET", "DATE", "TIME", "DATETIME" };
        final String[] floatTypes = new String[] { "FLOAT", "REAL", "DOUBLE", "PRECISION", "TIMESTAMP" };
        final String[] integerTypes = new String[] { "NUMERIC", "DECIMAL", "TINYINT", "SMALLINT", "MEDIUMINT", "INTEGER", "BIGINT" };

        String result;
        if (Arrays.stream(stringTypes).anyMatch(s -> s.equals(p)))
            result = "string";
        else if (Arrays.stream(floatTypes).anyMatch(s -> s.equals(p)))
            result = "float";
        else if (Arrays.stream(integerTypes).anyMatch(s -> s.equals(p)))
            result = "int";
        else
            result = "mixed";

        if (nullable)
            result += "|null";

        return result;

    }

    private String capitalizeFirstLetter(String original) {
        if (original == null || original.length() == 0) {
            return original;
        }
        return original.substring(0, 1).toUpperCase() + original.substring(1);
    }

}
