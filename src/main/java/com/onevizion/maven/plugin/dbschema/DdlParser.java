package com.onevizion.maven.plugin.dbschema;

import java.io.File;
import java.util.Collection;

import com.onevizion.maven.plugin.dbschema.vo.FilterConfig;
import gudusoft.gsqlparser.EConstraintType;
import gudusoft.gsqlparser.nodes.TColumnDefinition;
import gudusoft.gsqlparser.nodes.TConstraint;
import gudusoft.gsqlparser.nodes.TConstraintList;
import gudusoft.gsqlparser.nodes.TExpression;
import gudusoft.gsqlparser.nodes.TTypeName;
import gudusoft.gsqlparser.nodes.TViewAliasItem;
import gudusoft.gsqlparser.stmt.TCommentOnSqlStmt;

public interface DdlParser {
    void setParseFileCompleteCallback(ParseFileCompleteCallback callback);

    Collection<ObjectInfoCommentInfo> getTablesComments();

    ObjectInfoCommentInfo getTableComment(String tableName);

    Collection<ObjectInfoCommentInfo> getViewsComments();

    ObjectInfoCommentInfo getViewComment(String viewName);

    Collection<TableColumnInfo> getTablesColumnsInfos();

    Collection<TableColumnInfo> getTableColumnsInfos(String tableName);

    Collection<ViewColumnInfo> getViewsColumnsInfos();

    Collection<ViewColumnInfo> getViewColumnsInfos(String viewName);

    void setFilterTables(FilterConfig filterTables);

    void setFilterViews(FilterConfig filterViews);

    void doParse(Collection<File> fileCollection);

    interface ObjectInfo {
        String getObjectName();
    }

    interface CommentInfo {
        String getCommentMessage();
    }

    interface ColumnInfo {
        String getColumnName();

        String getDataType();

        String getNullable();

        String getDataDefault();

        String getColumnId();
    }

    class ObjectInfoCommentInfo implements ObjectInfo, CommentInfo {
        private TCommentOnSqlStmt commentStatement;
        private final String objectName;

        protected ObjectInfoCommentInfo(TCommentOnSqlStmt commentStatement, String objectName) {
            this.commentStatement = commentStatement;
            this.objectName = DdlParserImpl.stripObjectName(objectName);
        }

        public void setCommentStatement(TCommentOnSqlStmt commentStatement) {
            this.commentStatement = commentStatement;
        }

        @Override
        public String getCommentMessage() {
            return this.commentStatement != null ? this.commentStatement.getMessage().getValueToken().getTextWithoutQuoted() : "";
        }

        @Override
        public String getObjectName() {
            return this.objectName;
        }
    }

    class TableColumnInfo extends ObjectInfoCommentInfo implements ColumnInfo {
        private final TColumnDefinition columnDefinition;
        private final int columnIndex;

        public TableColumnInfo(TCommentOnSqlStmt commentStatement, TColumnDefinition columnDefinition, int columnIndex) {
            super(commentStatement, columnDefinition.getColumnName().getSourceTable().getName());
            this.columnDefinition = columnDefinition;
            this.columnIndex = columnIndex;
        }

        @Override
        public String getColumnName() {
            return DdlParserImpl.stripObjectName(this.columnDefinition.getColumnName().getColumnNameOnly());
        }

        @Override
        public String getDataType() {
            TTypeName typeName = this.columnDefinition.getDatatype();
            String suffix = "";

            if (typeName.getLength() != null) {
                suffix = "(" + typeName.getLength().getStringValue() + ")";
            } else if (typeName.getPrecision() != null && typeName.getScale() != null) {
                suffix = "(" + typeName.getPrecision().getStringValue() + "," +
                        typeName.getScale().getStringValue() + ")";
            }

            return DdlParserImpl.stripDataType(typeName.getDataType().name().toUpperCase()) + suffix;
        }

        @Override
        public String getNullable() {
            String value = "Yes";
            TConstraintList constraintList = this.columnDefinition.getConstraints();

            if (constraintList != null) {
                for (int index = 0; index < constraintList.size(); index++) {
                    TConstraint constraint = constraintList.getConstraint(index);

                    if (constraint.getConstraint_type() == EConstraintType.notnull ||
                            constraint.getConstraint_type() == EConstraintType.primary_key) {
                        value = "No";
                        break;
                    }
                }
            }

            return value;
        }

        @Override
        public String getDataDefault() {
            TExpression expression = this.columnDefinition.getDefaultExpression();
            return expression != null ? expression.toString() : "null";
        }

        @Override
        public String getColumnId() {
            return String.valueOf(this.columnIndex);
        }
    }

    class ViewColumnInfo extends ObjectInfoCommentInfo implements ColumnInfo {
        private final TViewAliasItem viewAliasItem;
        private final int columnIndex;

        public ViewColumnInfo(TCommentOnSqlStmt commentStatement, String viewName, TViewAliasItem viewAliasItem, int columnIndex) {
            super(commentStatement, viewName);
            this.viewAliasItem = viewAliasItem;
            this.columnIndex = columnIndex;
        }

        @Override
        public String getColumnName() {
            return DdlParserImpl.stripObjectName(this.viewAliasItem.getAlias().getObjectString());
        }

        @Override
        public String getDataType() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getNullable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getDataDefault() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getColumnId() {
            return String.valueOf(this.columnIndex);
        }
    }

    interface ParseFileCompleteCallback {
        void parseFileCompelete(File file);
    }
}
