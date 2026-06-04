package com.yashandb.liquibase.sqlgenerator;

import com.yashandb.liquibase.database.YashanDbDatabase;
import liquibase.database.Database;
import liquibase.datatype.DataTypeFactory;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sql.UnparsedSql;
import liquibase.sqlgenerator.SqlGeneratorChain;
import liquibase.sqlgenerator.core.SetNullableGenerator;
import liquibase.statement.core.SetNullableStatement;
import liquibase.structure.core.Column;
import liquibase.structure.core.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * YashanDB 专用的 SetNullable SQL 生成器。
 *
 * 【重写原因】
 * 父类 SetNullableGenerator 在 generateSql() 中通过 instanceof 判断数据库类型来决定 SQL 语法：
 * - OracleDatabase → ALTER TABLE t MODIFY col NOT NULL          （Oracle 语法）
 * - 其他数据库（含 YashanDbDatabase） → ALTER TABLE t ALTER COLUMN col SET NOT NULL （PostgreSQL/标准 SQL 语法）
 *
 * 由于 YashanDbDatabase 不再继承 OracleDatabase（为避免 JdbcDatabaseSnapshot 中 oracleQuery() 分支
 * 产生 USING 语法不兼容），Liquibase 的 instanceof OracleDatabase 判断对 YashanDbDatabase 全部返回 false，
 * 导致 SetNullableGenerator 走了通用分支，生成 "ALTER TABLE ... ALTER COLUMN ... SET NOT NULL"。
 *
 * YashanDB 不支持 "ALTER COLUMN ... SET NOT NULL" 这种 PostgreSQL 风格的语法，
 * 需要使用 Oracle 风格的 "ALTER TABLE ... MODIFY col NOT NULL"，因此必须重写。
 */
public class YashanDbSetNullableGenerator extends SetNullableGenerator {

    /**
     * 【重写原因】
     * 返回 PRIORITY_DATABASE 使本生成器优先级高于父类 SetNullableGenerator 的 PRIORITY_DEFAULT。
     * Liquibase 的 SqlGeneratorFactory 在选择生成器时按优先级排序，同优先级则按 supports() 匹配顺序，
     * PRIORITY_DATABASE 确保当 database 是 YashanDbDatabase 时，本生成器一定先于父类被选中。
     */
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    /**
     * 【重写原因】
     * 限定本生成器仅对 YashanDbDatabase 生效。
     * 父类 supports() 对所有非 SQLite、非 DB2z 的数据库都返回 true，
     * 如果不重写此方法，本生成器会对所有数据库生效，影响其他数据库类型的行为。
     */
    @Override
    public boolean supports(SetNullableStatement statement, Database database) {
        return database instanceof YashanDbDatabase;
    }

    /**
     * 【重写原因】
     * 父类 validate() 中对 OracleDatabase 有特殊校验逻辑（要求 constraintName 或 columnName 至少设一个），
     * 但对 YashanDbDatabase 走的是通用校验分支（仅检查 columnName 必填）。
     * YashanDB 和 Oracle 一样，设置 NULL 约束时如果没有 columnName 但有 constraintName 也可以工作，
     * 因此需要重写校验逻辑，与 Oracle 行为保持一致。
     */
    @Override
    public ValidationErrors validate(SetNullableStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {
        ValidationErrors validationErrors = new ValidationErrors();
        validationErrors.checkRequiredField("tableName", statement.getTableName());
        if (statement.isNullable()) {
            // YashanDB 允许通过 constraintName 来删除 NOT NULL 约束，不需要 columnName
            if (statement.getConstraintName() == null && statement.getColumnName() == null) {
                validationErrors.addError("YashanDB requires either constraintName or columnName to be set");
            }
        } else {
            validationErrors.checkRequiredField("columnName", statement.getColumnName());
        }
        return validationErrors;
    }

    /**
     * 【重写原因 —— 核心方法】
     * 父类 generateSql() 中通过 instanceof 判断生成不同 SQL：
     *
     *   if (database instanceof OracleDatabase) {
     *       sql = "ALTER TABLE t MODIFY col NOT NULL";           // ← YashanDB 需要的语法
     *   } else if (database instanceof HsqlDatabase || ...) {
     *       sql = "ALTER TABLE t ALTER COLUMN col SET NOT NULL";
     *   } else {
     *       sql = "ALTER TABLE t ALTER COLUMN col SET NOT NULL"; // ← YashanDbDatabase 实际走了这里
     *   }
     *
     * 因为 YashanDbDatabase 不继承 OracleDatabase，instanceof 判断为 false，
     * 所以生成了 "ALTER TABLE ... ALTER COLUMN col SET NOT NULL"，
     * YashanDB 解析此语句时报 YAS-04115 "SLICE|MCOL" expected but missing。
     *
     * 本方法直接生成 Oracle 风格的 MODIFY 语法，支持以下场景：
     * 1. 有 constraintName 且设为 NOT NULL → ALTER TABLE t MODIFY col CONSTRAINT c_name NOT NULL
     * 2. 有 constraintName 且设为 NULL     → ALTER TABLE t DROP CONSTRAINT c_name
     * 3. 无 constraintName                 → ALTER TABLE t MODIFY col [NOT] NULL
     *
     * 同时支持 ENABLE NOVALIDATE 选项（用于不验证已有数据的约束添加）。
     */
    @Override
    public Sql[] generateSql(SetNullableStatement statement, Database database, SqlGeneratorChain sqlGeneratorChain) {
        String nullableString = statement.isNullable() ? " NULL" : " NOT NULL";

        String sql;
        if (statement.getConstraintName() != null) {
            // 场景1：有约束名，设为 NOT NULL 时添加命名约束，设为 NULL 时删除约束
            if (!statement.isNullable()) {
                nullableString += !statement.isValidate() ? " ENABLE NOVALIDATE " : "";
                sql = "ALTER TABLE " + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName())
                    + " MODIFY " + database.escapeColumnName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName(), statement.getColumnName())
                    + " CONSTRAINT " + statement.getConstraintName() + nullableString;
            } else {
                sql = "ALTER TABLE " + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName())
                    + " DROP CONSTRAINT " + statement.getConstraintName();
            }
        } else {
            // 场景3：无约束名，直接 MODIFY 列的 NULL/NOT NULL 属性
            nullableString += !statement.isValidate() ? " ENABLE NOVALIDATE " : "";
            sql = "ALTER TABLE " + database.escapeTableName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName())
                + " MODIFY " + database.escapeColumnName(statement.getCatalogName(), statement.getSchemaName(), statement.getTableName(), statement.getColumnName())
                + nullableString;
        }

        List<Sql> returnList = new ArrayList<>();
        returnList.add(new UnparsedSql(sql, getAffectedColumn(statement)));
        return returnList.toArray(new Sql[0]);
    }

    protected Column getAffectedColumn(SetNullableStatement statement) {
        return new Column().setName(statement.getColumnName())
            .setRelation(new Table().setName(statement.getTableName()).setSchema(statement.getCatalogName(), statement.getSchemaName()));
    }
}
