package com.onevizion.maven.plugin.dbschema.vo;

public class AddTablesCommentsConfig {
     private int lineNum;
    private String tablesDir;
    private String tableType;

    public int getLineNum() {
        return lineNum;
    }

    public void setLineNum(int lineNum) {
        this.lineNum = lineNum;
    }

    public String getTablesDir() {
        return tablesDir;
    }

    public void setTablesDir(String tablesDir) {
        this.tablesDir = tablesDir;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }
}
