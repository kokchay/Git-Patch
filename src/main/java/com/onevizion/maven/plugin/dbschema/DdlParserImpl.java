package com.onevizion.maven.plugin.dbschema;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.onevizion.maven.plugin.dbschema.vo.DbObjectType;
import com.onevizion.maven.plugin.dbschema.vo.FilterConfig;
import gudusoft.gsqlparser.*;
import gudusoft.gsqlparser.nodes.TColumnDefinition;
import gudusoft.gsqlparser.nodes.TColumnDefinitionList;
import gudusoft.gsqlparser.nodes.TViewAliasItem;
import gudusoft.gsqlparser.nodes.TViewAliasItemList;
import gudusoft.gsqlparser.stmt.TCommentOnSqlStmt;
import gudusoft.gsqlparser.stmt.TCreateTableSqlStatement;
import gudusoft.gsqlparser.stmt.TCreateViewSqlStatement;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class DdlParserImpl implements DdlParser {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Multimap<String, TableColumnInfo> tableColumnsInfos = ArrayListMultimap.create();
    private final Multimap<String, ViewColumnInfo> viewColumnsInfos = ArrayListMultimap.create();

    private final Map<String, ObjectInfoCommentInfo> tablesComments = Maps.newHashMap();
    private final Map<String, ObjectInfoCommentInfo> viewsComments = Maps.newHashMap();

    private FilterConfig filterTables = null;
    private FilterConfig filterViews = null;
    private ParseFileCompleteCallback parseFileCompleteCallback;

    @Override
    public void setParseFileCompleteCallback(ParseFileCompleteCallback callback) {
        this.parseFileCompleteCallback = callback;
    }

    @Override
    public void setFilterTables(FilterConfig filterTables) {
        this.filterTables = filterTables;
    }

    @Override
    public void setFilterViews(FilterConfig filterViews) {
        this.filterViews = filterViews;
    }

    @Override
    public Collection<ObjectInfoCommentInfo> getTablesComments() {
        return this.tablesComments.values();
    }

    @Override
    public ObjectInfoCommentInfo getTableComment(String tableName) {
        return this.tablesComments.get(tableName);
    }

    @Override
    public Collection<ObjectInfoCommentInfo> getViewsComments() {
        return this.viewsComments.values();
    }

    @Override
    public ObjectInfoCommentInfo getViewComment(String viewName) {
        return this.viewsComments.get(viewName);
    }

    @Override
    public Collection<TableColumnInfo> getTablesColumnsInfos() {
        return this.tableColumnsInfos.values();
    }

    @Override
    public Collection<TableColumnInfo> getTableColumnsInfos(String tableName) {
        return this.tableColumnsInfos.get(tableName);
    }

    @Override
    public Collection<ViewColumnInfo> getViewsColumnsInfos() {
        return this.viewColumnsInfos.values();
    }

    @Override
    public Collection<ViewColumnInfo> getViewColumnsInfos(String viewName) {
        return this.viewColumnsInfos.get(viewName);
    }

    @Override
    public void doParse(Collection<File> fileCollection) {
        for (File file : fileCollection) {
            logger.debug("Parsing ddl script: " + file.getAbsolutePath());

            TGSqlParser parser = new TGSqlParser(EDbVendor.dbvoracle);

            parser.setSqlfilename(file.getAbsolutePath());

            if (parser.parse() != 0) {
                TSyntaxError syntaxError = parser.getSyntaxErrors().get(0);
                logger.error("Syntax error near \"{}\", line {}, column {} at {}", syntaxError.tokentext, syntaxError.lineNo, syntaxError.columnNo, file.getAbsolutePath());

                continue;
            }

            this.parseStatements(parser.getSqlstatements());

            // Notifying
            this.parseFileCompleteCallback.parseFileCompelete(file);

            // Clear data
            this.tableColumnsInfos.clear();
            this.viewColumnsInfos.clear();
            this.tablesComments.clear();
            this.viewsComments.clear();
        }
    }

    static String stripObjectName(String object) {
        return StringUtils.strip(object, "\"").replaceAll("\\r|\\n", "");
    }

    static String stripDataType(String object) {
        return StringUtils.stripEnd(object, "_T");
    }

    private boolean isObjectExcluded(String objectName, DbObjectType dbObjectType) {
        if (dbObjectType == DbObjectType.TABLE) {
            if (this.filterTables != null && this.filterTables.getExclude() != null) {
                for (String excludePattern : this.filterTables.getExclude()) {
                    if (FilenameUtils.wildcardMatch(objectName, excludePattern, IOCase.INSENSITIVE)) {
                        logger.debug("Skipped object {} by table pattern \"{}\"", objectName, excludePattern);
                        return true;
                    }
                }
            }
        } else if (dbObjectType == DbObjectType.VIEW) {
            if (this.filterViews != null && this.filterViews.getExclude() != null) {
                for (String excludePattern : this.filterViews.getExclude()) {
                    if (FilenameUtils.wildcardMatch(objectName, excludePattern, IOCase.INSENSITIVE)) {
                        logger.debug("Skipped object {} by view pattern \"{}\"", objectName, excludePattern);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void parseStatements(TStatementList statementList) {
        for (TCustomSqlStatement statement : statementList) {
            if (statement instanceof TCreateTableSqlStatement) {
                TCreateTableSqlStatement createTableSqlStatement = (TCreateTableSqlStatement) statement;
                TColumnDefinitionList columnDefinitionList = createTableSqlStatement.getColumnList();
                String tableName = stripObjectName(createTableSqlStatement.getTableName().getTableString());

                if (this.isObjectExcluded(tableName, DbObjectType.TABLE)) {
                    continue;
                }

                for (int index = 0; index < columnDefinitionList.size(); index++) {
                    TColumnDefinition columnDefinition = columnDefinitionList.getColumn(index);
                    TableColumnInfo tableColumnInfo = new TableColumnInfo(null, columnDefinition, index + 1);

                    this.tableColumnsInfos.put(tableName, tableColumnInfo);
                }
            } else if (statement instanceof TCreateViewSqlStatement) {
                TCreateViewSqlStatement createViewSqlStatement = (TCreateViewSqlStatement) statement;
                TViewAliasItemList viewAliasItemList = createViewSqlStatement.getViewAliasClause()
                                                                             .getViewAliasItemList();
                String viewName = stripObjectName(createViewSqlStatement.getViewName().getTableString());

                if (this.isObjectExcluded(viewName, DbObjectType.VIEW)) {
                    continue;
                }

                for (int index = 0; index < viewAliasItemList.size(); index++) {
                    TViewAliasItem viewAliasItem = viewAliasItemList.getViewAliasItem(index);
                    ViewColumnInfo viewColumnInfo = new ViewColumnInfo(null, viewName, viewAliasItem, index + 1);

                    this.viewColumnsInfos.put(viewName, viewColumnInfo);
                }
            } else if (statement instanceof TCommentOnSqlStmt) {
                TCommentOnSqlStmt commentOnSqlStmt = (TCommentOnSqlStmt) statement;
                EDbObjectType objectType = commentOnSqlStmt.getDbObjectType();
                String objectName;

                if (objectType == EDbObjectType.column) {
                    objectName = commentOnSqlStmt.getObjectName().getColumnNameOnly();
                } else if (objectType == EDbObjectType.table || objectType == EDbObjectType.view) {
                    objectName = commentOnSqlStmt.getObjectName().getTableString();
                } else {
                    logger.error("Unknown comment statement type: {}", objectType.name());
                    continue;
                }

                objectName = stripObjectName(objectName);

                DbObjectType dbObjectType = null;
                if (objectType == EDbObjectType.table) {
                    dbObjectType = DbObjectType.TABLE;
                } else if (objectType == EDbObjectType.view) {
                    dbObjectType = DbObjectType.VIEW;
                }

                if (dbObjectType != null && !this.isObjectExcluded(objectName, dbObjectType)) {
                    continue;
                }

                if (objectType == EDbObjectType.column) {
                    String tableViewName = stripObjectName(commentOnSqlStmt.getObjectName().getTableString());

                    if (this.tableColumnsInfos.containsKey(tableViewName)) {
                        for (TableColumnInfo tableColumnInfo : this.tableColumnsInfos.get(tableViewName)) {
                            if (tableColumnInfo.getColumnName().equalsIgnoreCase(objectName)) {
                                tableColumnInfo.setCommentStatement(commentOnSqlStmt);
                                break;
                            }
                        }
                    } else if (this.viewColumnsInfos.containsKey(tableViewName)) {
                        for (ViewColumnInfo viewColumnInfo : this.viewColumnsInfos.get(tableViewName)) {
                            if (viewColumnInfo.getColumnName().equalsIgnoreCase(objectName)) {
                                viewColumnInfo.setCommentStatement(commentOnSqlStmt);
                                break;
                            }
                        }
                    } else {
                        // Create view/table statement should be writed before comment statements
                        logger.error("Table or view with name '{}' not found!", tableViewName);
                    }
                } else { // Table/View
                    ObjectInfoCommentInfo objectInfoCommentInfo = new ObjectInfoCommentInfo(commentOnSqlStmt, objectName);

                    if (objectType == EDbObjectType.table) {
                        this.tablesComments.put(objectName, objectInfoCommentInfo);
                    } else { // View
                        this.viewsComments.put(objectName, objectInfoCommentInfo);
                    }
                }
            }
        }
    }
}
