package com.onevizion.maven.plugin.dbschema;

import com.google.common.collect.Lists;
import com.onevizion.maven.plugin.dbschema.DdlParser.TableColumnInfo;
import com.onevizion.maven.plugin.dbschema.DdlParser.ViewColumnInfo;
import com.onevizion.maven.plugin.dbschema.vo.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.onevizion.maven.plugin.dbschema.DdlParser.ObjectInfoCommentInfo;

public class SiteGenerator {

    private final DdlParser ddlParser;

    public SiteGenerator(DdlParser ddlParser) {
        this.ddlParser = ddlParser;
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String COMMENT_TEMPLATE = "comment-template.txt";
    private static final String NODE_TYPES = "nodeTypes.html";
    public static final String LIST_ENTRY_TEMPLATE = "listEntry-template.txt";
    public static final String VIEW_ROW_HTML_TEMPLATE = "viewRowHtmlTemplate.txt";
    public static final String VIEW_TEMPLATE_HTML = "view.template.html";
    public static final String TABLE_ROW_HTML_TEMPLATE = "tableRowHtmlTemplate.txt";
    public static final String TABLE_TEMPLATE_HTML = "table.template.html";

    private String rowsRegexp = "\\$\\{rows\\}";
    private String tabNameRegexp = "\\$\\{tableName\\}";
    private String columnNameRegexp = "\\$\\{columnName\\}";
    private String dataTypeRegexp = "\\$\\{dataType\\}";
    private String nullableRegexp = "\\$\\{nullable\\}";
    private String dataDefaultRegexp = "\\$\\{dataDefault\\}";
    private String columnIdRegexp = "\\$\\{columnId\\}";
    private String commentsRegexp = "\\$\\{comments\\}";

    public void copySiteTemplate(String sitePath) throws IOException {
        logger.info("Copying site template...");
        InputStream stream;
        stream = getClass().getProtectionDomain().getCodeSource().getLocation().openStream();
        SiteUtils.unZipSiteTemplate(stream, new File(sitePath));
    }

    public void deleteFiles(DeleteFilesConfig[] deleteFilesConfigs) throws Exception {
        logger.info("Deleting redundant files...");
        for (DeleteFilesConfig config : deleteFilesConfigs) {
            if (config.getInputDirectory() == null || config.getInputDirectory().isEmpty()) {
                String error = "inputDirectory param must not be empty in deleteFilesConfig";
                throw new Exception(error);
            }
            SiteUtils.deleteFiles(config);
        }
    }

    public void copyFiles(CopyFilesConfig[] copyFilesConfigs) throws Exception {
        logger.info("Copying files...");
        for (CopyFilesConfig config : copyFilesConfigs) {
            if (config.getSrcDir() == null || config.getSrcDir().isEmpty()) {
                String error = "srcDir param must not be empty in copyFilesConfig";
                throw new Exception(error);
            }
            if (config.getDestDir() == null || config.getDestDir().isEmpty()) {
                String error = "destDir param must not be empty in copyFilesConfig";
                throw new Exception(error);
            }
            SiteUtils.copyFiles(config);
        }
    }

    public void reformatFiles(ReformatFilesConfig[] reformatFilesConfigs, File... files) throws Exception {
        logger.debug("Formatting files...");
        for (ReformatFilesConfig config : reformatFilesConfigs) {
            if (config.getInputDirectory() == null || config.getInputDirectory().isEmpty()) {
                String error = "inputDirectory param must not be empty in reformatFilesConfig";
                throw new Exception(error);
            }
            SiteUtils.reformatFiles(config);
        }
    }

    public void addTableComments(AddTablesCommentsConfig config, String... dbObjects) throws IOException {
        List<DdlParser.ObjectInfoCommentInfo> comments = Lists.newArrayList();
        logger.debug("Adding tables comments...");
        if (dbObjects.length == 0) {
            comments.addAll(ddlParser.getTablesComments());
        } else {
            for (String table : dbObjects) {
                comments.add(ddlParser.getTableComment(table));
            }
        }

        addComments(config, comments);
    }

    public void addViewComments(AddTablesCommentsConfig config, String... dbObjects) throws IOException {
        List<DdlParser.ObjectInfoCommentInfo> comments = Lists.newArrayList();
        logger.debug("Adding views comments...");
        if (dbObjects.length == 0) {
            comments.addAll(ddlParser.getViewsComments());
        } else {
            for (String view : dbObjects) {
                comments.add(ddlParser.getViewComment(view));
            }
        }
        addComments(config, comments);
    }

    private void addComments(AddTablesCommentsConfig config, List<DdlParser.ObjectInfoCommentInfo> comments) throws IOException {
        InputStream commentTemplateIs = getClass().getClassLoader().getResourceAsStream(COMMENT_TEMPLATE);
        StringWriter writer = new StringWriter();
        IOUtils.copy(commentTemplateIs, writer);
        String commentTemplate = writer.toString();
        String commentRegexp = "\\$\\{comment\\}";
        String tableTypeRegexp = "\\$\\{tableType\\}";
        for (ObjectInfoCommentInfo object : comments) {
            String commentText = object.getCommentMessage();
            String currentComment;
            if (commentText != null) {
                currentComment = commentTemplate.replaceAll(commentRegexp, commentText);
            } else {
                currentComment = "";
            }
            currentComment = currentComment.replaceFirst(tableTypeRegexp, config.getTableType());
            String path = config.getTablesDir() + "/" + object.getObjectName() + ".html";
            File htmlFile = new File(path);
            List<String> contents = FileUtils.readLines(htmlFile);
            int currentStringInList = config.getLineNum() - 1;
            contents.add(currentStringInList, currentComment);
            FileUtils.writeLines(htmlFile, contents);
        }
    }

    public void generateTableOfContents(AddTableOfContentsConfig[] configs) throws Exception {
        InputStream listEntryTemplateIs = getClass().getClassLoader().getResourceAsStream(LIST_ENTRY_TEMPLATE);
        StringWriter writer = new StringWriter();
        IOUtils.copy(listEntryTemplateIs, writer);
        String listEntryTemplate = writer.toString();
        String filePathRegexp = "\\$\\{filePath}";
        String fileNameRegexp = "\\$\\{fileName}";
        String tableOfContents = "index.html";
        for (AddTableOfContentsConfig config : configs) {
            File dir = new File(config.getInputDir());
            String include = "*";
            FileFilter filter = new WildcardFileFilter(include, IOCase.INSENSITIVE);
            File[] filteredFiles = dir.listFiles(filter);
            if (filteredFiles == null || filteredFiles.length == 0) {
                throw new Exception(include + " files not found in " + dir);
            } else {
                File tableOfContentsFile = new File(dir + File.separator + tableOfContents);
                List<String> contents = FileUtils.readLines(tableOfContentsFile);
                int lineNum = config.getLineNum();
                int currentStringInList = lineNum - 1;
                if (lineNum > contents.size()) {
                    throw new Exception(tableOfContents + " file has less than " + lineNum + " lines");
                }
                List<String> filteredFileNames = Stream.of(filteredFiles).map(File::getName).collect(Collectors.toList());
                filteredFileNames.sort(Comparator.naturalOrder());
                for (String fileName : filteredFileNames) {
                    if (!tableOfContents.equals(fileName.toLowerCase())) {
                        String listEntry = listEntryTemplate.replaceFirst(filePathRegexp, fileName);
                        int dotIndex = fileName.indexOf(".");
                        String nameWithoutExtension = fileName.substring(0, dotIndex);
                        listEntry = listEntry.replaceFirst(fileNameRegexp, nameWithoutExtension);
                        contents.add(currentStringInList++, listEntry);
                    }
                }
                FileUtils.writeLines(tableOfContentsFile, contents);
            }
        }
    }

    public void generateTablesHtml(String tablesDir, String... dbObjects) throws IOException {
        logger.debug("Generate tables html...");
        StringWriter rowHtmlWriter = new StringWriter();
        InputStream rowHtmlTemplateIs = getClass().getClassLoader().getResourceAsStream(TABLE_ROW_HTML_TEMPLATE);
        IOUtils.copy(rowHtmlTemplateIs, rowHtmlWriter);
        String rowHtmlTemplate = rowHtmlWriter.toString();

        StringWriter tableHtmlWriter = new StringWriter();
        InputStream tableHtmlTemplateIs = getClass().getClassLoader().getResourceAsStream(TABLE_TEMPLATE_HTML);
        IOUtils.copy(tableHtmlTemplateIs, tableHtmlWriter);
        String tableHtmlTemplate = tableHtmlWriter.toString();

        List<TableColumnInfo> tableInfos;
        if (dbObjects.length == 0) {
            tableInfos = new ArrayList<>(ddlParser.getTablesColumnsInfos());
        } else {
            tableInfos = new ArrayList<>();
            for (String tableName : dbObjects) {
                Collection<TableColumnInfo> infos = ddlParser.getTableColumnsInfos(tableName);
                tableInfos.addAll(infos);              
            }
        }

        tableInfos.sort(Comparator.comparing(TableColumnInfo::getColumnName));
 
        String rows = "";
        String tabName = null;

        int tabInfosSize = tableInfos.size();
        if (tabInfosSize != 0) {
            tabName = tableInfos.iterator().next().getObjectName();
        }

        int i = 0;
        for (Iterator<TableColumnInfo> iterator = tableInfos.iterator(); iterator.hasNext(); i++) {
            TableColumnInfo tableColumnInfo = iterator.next();

            String row = rowHtmlTemplate.replaceFirst(columnNameRegexp, tableColumnInfo.getColumnName());
            row = row.replaceFirst(dataTypeRegexp, tableColumnInfo.getDataType());
            row = row.replaceFirst(nullableRegexp, tableColumnInfo.getNullable());
            row = row.replaceFirst(dataDefaultRegexp, tableColumnInfo.getDataDefault());
            row = row.replaceFirst(columnIdRegexp, tableColumnInfo.getColumnId());
            row = row.replaceFirst(commentsRegexp, tableColumnInfo.getCommentMessage());

            if (!tabName.equals(tableColumnInfo.getObjectName())) {
                String content = tableHtmlTemplate.replaceFirst(tabNameRegexp, tabName);
                content = content.replaceFirst(rowsRegexp, rows);
                File tableHtml = new File(tablesDir + File.separator + tabName.toUpperCase() + ".html");
                FileUtils.write(tableHtml, content);
                rows = row + "\n";
                tabName = tableColumnInfo.getObjectName();
            } else if (i == tabInfosSize - 1) {
                rows += row + "\n";
                String content = tableHtmlTemplate.replaceFirst(tabNameRegexp, tabName);
                content = content.replaceFirst(rowsRegexp, rows);
                File tableHtml = new File(tablesDir + File.separator + tabName.toUpperCase() + ".html");
                FileUtils.write(tableHtml, content);
            } else {
                rows += row + "\n";
            }
        }
    }

    public void generateViewsHtml(String viewsDir, String... dbObjects) throws IOException {
        logger.debug("Generate views html...");
        StringWriter rowHtmlWriter = new StringWriter();
        InputStream rowHtmlTemplateIs = getClass().getClassLoader().getResourceAsStream(VIEW_ROW_HTML_TEMPLATE);
        IOUtils.copy(rowHtmlTemplateIs, rowHtmlWriter);
        String rowHtmlTemplate = rowHtmlWriter.toString();

        StringWriter viewHtmlWriter = new StringWriter();
        InputStream viewHtmlTemplateIs = getClass().getClassLoader().getResourceAsStream(VIEW_TEMPLATE_HTML);
        IOUtils.copy(viewHtmlTemplateIs, viewHtmlWriter);
        String viewHtmlTemplate = viewHtmlWriter.toString();

        List<ViewColumnInfo> viewInfos;
        if (dbObjects.length == 0) {
            viewInfos = new ArrayList<>(ddlParser.getViewsColumnsInfos());
        } else {
            viewInfos = new ArrayList<>();
            for (String viewName : dbObjects) {
                Collection<DdlParserImpl.ViewColumnInfo> infos = ddlParser.getViewColumnsInfos(viewName);
                viewInfos.addAll(infos);
            }
        }

        viewInfos.sort(Comparator.comparing(ViewColumnInfo::getColumnName));

        String rows = "";
        String viewName = null;
        int vwsInfosSize = viewInfos.size();
        if (vwsInfosSize != 0) {
            viewName = viewInfos.iterator().next().getObjectName();
        }

        int i = 0;
        for (Iterator<DdlParser.ViewColumnInfo> iterator = viewInfos.iterator(); iterator.hasNext(); i++) {
            ViewColumnInfo viewColumnInfo = iterator.next();

            String row = rowHtmlTemplate.replaceFirst(columnNameRegexp, viewColumnInfo.getColumnName());
            row = row.replaceFirst(columnIdRegexp, viewColumnInfo.getColumnId());
            row = row.replaceFirst(commentsRegexp, viewColumnInfo.getCommentMessage());

            if (!viewName.equals(viewColumnInfo.getObjectName())) {
                String content = viewHtmlTemplate.replaceFirst(tabNameRegexp, viewName);
                content = content.replaceFirst(rowsRegexp, rows);
                File viewHtml = new File(viewsDir + File.separator + viewName.toUpperCase() + ".html");
                FileUtils.write(viewHtml, content);
                rows = row + "\n";
                viewName = viewColumnInfo.getObjectName();
            } else if (i == vwsInfosSize - 1) {
                rows += row + "\n";
                String content = viewHtmlTemplate.replaceFirst(tabNameRegexp, viewName);
                content = content.replaceFirst(rowsRegexp, rows);
                File viewHtml = new File(viewsDir + File.separator + viewName.toUpperCase() + ".html");
                FileUtils.write(viewHtml, content);
            } else {
                rows += row + "\n";
            }
        }
    }

    public void addProjectName(String sitePath, String projectName) throws IOException {
        File nodeTypesFile = new File(sitePath + File.separator + NODE_TYPES);
        String nodeTypesContent = FileUtils.readFileToString(nodeTypesFile);
        String projectNameRegex = "\\$\\{projectName\\}";
        nodeTypesContent = nodeTypesContent.replaceFirst(projectNameRegex, projectName);
        FileUtils.write(nodeTypesFile, nodeTypesContent);
    }
}