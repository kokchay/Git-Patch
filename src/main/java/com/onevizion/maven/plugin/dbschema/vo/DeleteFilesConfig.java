package com.onevizion.maven.plugin.dbschema.vo;

public class DeleteFilesConfig {
    private String inputDirectory;
    private String[] includes;

    public String getInputDirectory() {
        return inputDirectory;
    }

    public void setInputDirectory(String inputDirectory) {
        this.inputDirectory = inputDirectory;
    }

    public String[] getIncludes() {
        return includes;
    }

    public void setIncludes(String[] includes) {
        this.includes = includes;
    }
}
