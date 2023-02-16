package com.onevizion.maven.plugin.dbschema.mojo;

import com.google.common.collect.Lists;
import com.onevizion.maven.plugin.dbschema.DdlParser;
import com.onevizion.maven.plugin.dbschema.DdlParserImpl;
import com.onevizion.maven.plugin.dbschema.SiteGenerator;
import com.onevizion.maven.plugin.dbschema.vo.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "generateSite", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateSiteMojo extends AbstractMojo implements DdlParser.ParseFileCompleteCallback {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String ddlTablesPathSuf = "/tables";
    private final String ddlViewsPathSuf = "/views";

    @Parameter(property = "sitePath", required = true)
    String sitePath;

    @Parameter(property = "tablesDir", required = true)
    String tablesDir;

    @Parameter(property = "viewsDir", required = true)
    String viewsDir;

    @Parameter(property = "pldocDir", required = true)
    String pldocDir;

    @Parameter(property = "packagesDir", required = true)
    String packagesDir;

    @Parameter(property = "projectName", required = false)
    String projectName;

    @Parameter(property = "reformatFilesConfigs", required = false)
    ReformatFilesConfig[] reformatFilesConfigs;

    @Parameter(property = "copyFilesConfigs", required = false)
    CopyFilesConfig[] copyFilesConfigs;

    @Parameter(property = "deleteFilesConfigs", required = false)
    DeleteFilesConfig[] deleteFilesConfigs;

    @Parameter(property = "addTablesCommentsConfigs", required = false)
    private AddTablesCommentsConfig[] addTablesCommentsConfigs;

    @Parameter(property = "addTableOfContentsConfigs", required = true)
    private AddTableOfContentsConfig[] addTableOfContentsConfigs;

    @Parameter(property = "dbSiteObjects", required = false)
    private String[] dbSiteObjects;

    @Parameter(property = "outputDirectory", required = true)
    private String outputDirectory;

    @Parameter(property = "filterTables", required = false)
    private FilterConfig filterTables;

    @Parameter(property = "filterViews", required = false)
    private FilterConfig filterViews;

    private SiteGenerator siteGenerator;

    @Override
    public void parseFileCompelete(File file) {
        logger.debug("Post processing {}...", file.getAbsolutePath());

        try {
            siteGenerator.generateTablesHtml(tablesDir);
            siteGenerator.generateViewsHtml(viewsDir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (addTablesCommentsConfigs != null && addTablesCommentsConfigs.length > 0) {
            try {
                for (AddTablesCommentsConfig config : addTablesCommentsConfigs) {
                    if (DbObjectType.VIEW.toString().equalsIgnoreCase(config.getTableType())) {
                        siteGenerator.addViewComments(config);
                    } else if (DbObjectType.TABLE.toString().equalsIgnoreCase(config.getTableType())) {
                        siteGenerator.addTableComments(config);
                    } else {
                        logger.error("Table type must be named \"TABLE\" or \"VIEW\"");
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void execute() throws MojoFailureException {
        DdlParser ddlParser = new DdlParserImpl();
        siteGenerator = new SiteGenerator(ddlParser);

        if (filterTables != null) {
            ddlParser.setFilterTables(filterTables);
        }
        if (filterViews != null) {
            ddlParser.setFilterViews(filterViews);
        }

        ddlParser.setParseFileCompleteCallback(this);

        try {
            siteGenerator.copySiteTemplate(sitePath);

            if (projectName != null && !projectName.isEmpty()) {
                siteGenerator.addProjectName(sitePath, projectName);
            } else {
                siteGenerator.addProjectName(sitePath, "Program Scope");
            }
        } catch (IOException e) {
            logger.error(e.getLocalizedMessage());
            throw new MojoFailureException(e.getLocalizedMessage(), e);
        }

        // Generating parse filelist
        FilenameFilter filenameFilter;
        List<File> filesToParse = new ArrayList<File>();
        boolean checkCopyConfigFiles = false;

        if (dbSiteObjects != null && dbSiteObjects.length != 0) {
            final List<String> whitelistedScriptNames = Lists.newArrayList();

            filenameFilter = (dir, name) -> name.endsWith(".sql") && whitelistedScriptNames.contains(name);

            String[][] parseObjects;
            try {
                parseObjects = parseArgs(dbSiteObjects);
            } catch (Exception e) {
                logger.error(e.getLocalizedMessage());
                throw new MojoFailureException(e.getLocalizedMessage(), e);
            }

            List<String> packagesList = new ArrayList<String>();
            List<String> tablesList = new ArrayList<String>();
            List<String> viewsList = new ArrayList<String>();
            for (String[] parseObject : parseObjects) {
                if (DbObjectType.PACKAGE_SPEC.toString().equalsIgnoreCase(parseObject[1])) {
                    packagesList.add(parseObject[0] + ".*");
                }
                if (DbObjectType.TABLE.toString().equalsIgnoreCase(parseObject[1])) {
                    tablesList.add(parseObject[0]);
                }
                if (DbObjectType.VIEW.toString().equalsIgnoreCase(parseObject[1])) {
                    viewsList.add(parseObject[0]);
                }
            }
            if (packagesList.size() > 0) {
                String[] includes = packagesList.toArray(new String[0]);

                CopyFilesConfig copyFilesConfig = new CopyFilesConfig();
                copyFilesConfig.setSrcDir(pldocDir);
                copyFilesConfig.setDestDir(packagesDir);
                copyFilesConfig.setIncludes(includes);
                try {
                    siteGenerator.copyFiles(new CopyFilesConfig[]{copyFilesConfig});
                } catch (Exception e) {
                    logger.error(e.getLocalizedMessage());
                    throw new MojoFailureException(e.getLocalizedMessage(), e);
                }

                if (reformatFilesConfigs != null && reformatFilesConfigs.length > 0) {
                    List<ReformatFilesConfig> packageReformatConfigsList = new ArrayList<ReformatFilesConfig>();
                    for (ReformatFilesConfig reformatConfig : reformatFilesConfigs) {
                        if (new File(packagesDir).equals(new File(reformatConfig.getInputDirectory()))) {
                            reformatConfig.setIncludes(includes);
                            packageReformatConfigsList.add(reformatConfig);
                        }
                    }

                    ReformatFilesConfig[] packageReformatConfigs = new ReformatFilesConfig[packageReformatConfigsList
                            .size()];
                    packageReformatConfigs = packageReformatConfigsList.toArray(packageReformatConfigs);
                    try {
                        siteGenerator.reformatFiles(packageReformatConfigs);
                    } catch (Exception e) {
                        logger.error(e.getLocalizedMessage());
                        throw new MojoFailureException(e.getLocalizedMessage(), e);
                    }
                }
            }

            whitelistedScriptNames.addAll(tablesList);
            whitelistedScriptNames.addAll(viewsList);

            checkCopyConfigFiles = true;
        } else {
            filenameFilter = (dir, name) -> name.endsWith(".sql");
        }

        filesToParse.addAll(Arrays.asList(new File(outputDirectory, ddlTablesPathSuf).listFiles(filenameFilter)));
        filesToParse.addAll(Arrays.asList(new File(outputDirectory, ddlViewsPathSuf).listFiles(filenameFilter)));

        // Parsing
        try {
            ddlParser.doParse(filesToParse);
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
            throw new MojoFailureException(e.getLocalizedMessage(), e);
        }

        //
        try {
            if (checkCopyConfigFiles) {
                if (copyFilesConfigs != null && copyFilesConfigs.length > 0) {
                    List<CopyFilesConfig> newConfigsList = new ArrayList<CopyFilesConfig>();

                    for (CopyFilesConfig config : copyFilesConfigs) {
                        if (!new File(pldocDir).equals(new File(config.getSrcDir()))) {
                            newConfigsList.add(config);
                        }
                    }
                    if (newConfigsList.size() > 0) {
                        CopyFilesConfig[] newConfigs = new CopyFilesConfig[newConfigsList.size()];
                        newConfigs = newConfigsList.toArray(newConfigs);
                        try {
                            siteGenerator.copyFiles(newConfigs);
                        } catch (Exception e) {
                            logger.error(e.getLocalizedMessage());
                            throw new MojoFailureException(e.getLocalizedMessage(), e);
                        }
                    }
                }
            } else {
                if (copyFilesConfigs != null && copyFilesConfigs.length > 0) {
                    siteGenerator.copyFiles(copyFilesConfigs);
                }

                if (reformatFilesConfigs != null && reformatFilesConfigs.length > 0) {
                    siteGenerator.reformatFiles(reformatFilesConfigs);
                }
            }
        } catch (Exception e) {
            logger.error(e.getLocalizedMessage());
            throw new MojoFailureException(e.getLocalizedMessage(), e);
        }
        //

        if (addTableOfContentsConfigs != null && addTableOfContentsConfigs.length > 0) {
            try {
                logger.info("Generating table of contents...");
                siteGenerator.generateTableOfContents(addTableOfContentsConfigs);
            } catch (Exception e) {
                logger.error(e.getLocalizedMessage());
                throw new MojoFailureException(e.getLocalizedMessage(), e);
            }
        }

        if (deleteFilesConfigs != null && deleteFilesConfigs.length > 0) {
            try {
                siteGenerator.deleteFiles(deleteFilesConfigs);
            } catch (Exception e) {
                logger.info(e.getLocalizedMessage());
            }
        }
        logger.info("done");
    }

    private String[][] parseArgs(String[] dbObjects) throws Exception {
        String[][] parsingObjects = new String[dbObjects.length][2];
        Pattern pattern = Pattern.compile("\\W");
        for (int i = 0; i < dbObjects.length; i++) {
            dbObjects[i] = dbObjects[i].toUpperCase();
            Matcher matcher = pattern.matcher(dbObjects[i]);
            if (!matcher.find()) {
                String error = String.format("Can not find separator in this arg: %s" +
                        " Separator can be symbol by regexp: '\\W'", dbObjects[i]);
                logger.error(error);
                throw new Exception(error);
            }
            String separator = matcher.group(0);
            int sepIndex = dbObjects[i].indexOf(separator);

            String name = dbObjects[i].substring(0, sepIndex);
            if (name.isEmpty()) {
                String error = String.format("Incorrect arg: %s. Name of db object can not be empty", dbObjects[i]);
                logger.error(error);
                throw new Exception(error);
            }

            String type = dbObjects[i].substring(sepIndex + 1);
            if (type.isEmpty()) {
                String error = String.format("Incorrect db type in this arg: %s", dbObjects[i]);
                logger.error(error);
                throw new Exception(error);
            }
            parsingObjects[i][0] = name;
            parsingObjects[i][1] = type;
        }
        return parsingObjects;
    }
}