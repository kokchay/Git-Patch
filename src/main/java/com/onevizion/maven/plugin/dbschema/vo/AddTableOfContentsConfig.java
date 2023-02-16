package com.onevizion.maven.plugin.dbschema.vo;

public class AddTableOfContentsConfig {
    private int lineNum;
    private String inputDir;

    public int getLineNum() {
        return lineNum;
    }

    public void setLineNum(int lineNum) {
        this.lineNum = lineNum;
    }

    public String getInputDir() {
        return inputDir;
    }

    public void setInputDir(String inputDir) {
        this.inputDir = inputDir;
    }
}
