package com.yashandb.liquibase.database;

import liquibase.CatalogAndSchema;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.DatabaseConnection;
import liquibase.database.OfflineConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.executor.ExecutorService;
import liquibase.Scope;
import liquibase.statement.core.RawCallStatement;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.statement.SqlStatement;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YashanDB 数据库适配器
 *
 * 不继承 OracleDatabase，避免走 oracleQuery() 分支（使用 USING 语法导致不兼容）
 * 改为继承 AbstractJdbcDatabase，走 JDBC 标准路径
 */
public class YashanDbDatabase extends AbstractJdbcDatabase {

    public static final String PRODUCT_NAME = "YashanDB";
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+).*");

    private Integer databaseMajorVersion;
    private Integer databaseMinorVersion;
    private Integer databasePatchVersion;

    /**
     * 纯内部系统 schema 列表：这些 schema 下永远不会存在用户业务表。
     *
     * 【设计原则】
     * Schema 级过滤会通过 else if 递归传播：一旦 schema 被判定为 system，
     * 该 schema 下所有对象（表、索引、序列等）都会被过滤。
     * 因此这里只包含【纯内部】schema，不包含 SYSTEM、DBSNMP、OUTLN 等
     * 可能包含用户业务表的 schema——那些通过表名模式（SYSTEM_TABLE_PREFIXES）过滤。
     *
     * 不包含 SYS：YashanDB 业务表通常建在 SYS 下。
     */
    private static final Set<String> SYSTEM_SCHEMAS = new HashSet<>(Arrays.asList(
        "CTXSYS",       // 全文检索（Oracle 兼容内部组件）
        "XDB",          // XML DB（Oracle 兼容内部组件）
        "YMP_DEFAULT"   // YMP 迁移工具默认 schema
    ));

    /**
     * 系统表名称前缀列表，配合 isSystemTableName() 使用。
     *
     * 【与 $ 后缀规则的分工】
     * - 规则1（endsWith "$"）：覆盖以 $ 结尾的字典表（OBJ$, COL$, TAB$ 等）
     * - 规则2（本列表）：覆盖两类系统表：
     *   a) $ 在名称中间的前缀型系统表（如 BIN$xxx, AUD$UNIFIED, YLS$POL, OL$HINTS）
     *   b) 不含 $ 的系统组件表（如 WRH$_SQLSTAT, SCHEDULER$_JOB, SLOW_LOG）
     */
    private static final String[] SYSTEM_TABLE_PREFIXES = {
        // ── $ 在名称中间的系统表前缀（不以 $ 结尾，需要前缀匹配）──
        "BIN$",            // 回收站: BIN$xxxxxx==$0
        "AQ$",             // 高级队列: AQ$xxx
        "DR$",             // 全文索引: DR$xxx
        "AUD$",            // 审计跟踪: AUD$UNIFIED
        "OL$",             // Outline 存储: OL$HINTS, OL$NODES
        "YLS$",            // YashanDB Label Security: YLS$POL, YLS$LAB 等
        "XDB$",            // XML DB 内部表: XDB$xxx
        "LOGSTDBY$",       // 逻辑 Standby: LOGSTDBY$CHECKPOINT, LOGSTDBY$EVENT

        // ── 不以 $ 结尾的系统表前缀 ──
        "SYS_IOT_OVER",    // 索引组织表溢出段
        "MLOG$_",          // 物化视图日志
        "RUPD$_",          // 物化视图刷新
        "WM$_",            // 工作空间管理 (Workspace Manager)
        "ISEQ$$_",         // 身份列自动序列 (Identity Sequence)
        "SYS_FBA",         // 闪回归档 (Flashback Archive)
        "WRH$_",           // AWR 历史数据
        "WRM$_",           // AWR 管理数据
        "WRI$_",           // AWR 内部数据
        "AUDIT_",          // 审计相关
        "DEF$_",           // 延迟事务 (Deferred Transactions)
        "STREAMS_",        // 流复制 / GoldenGate
        "YSTREAM_",        // YashanDB 流复制
        "GARBAGE_",        // YashanDB 垃圾回收
        "RSRC_",           // 资源管理 (Resource Manager)
        "SCHEDULER$_",     // 调度器 (DBMS_SCHEDULER)
        "MON_MODS",        // 监控修改统计
        "SLOW_LOG",        // 慢查询日志
        "DAM_",            // 数据资产管理
        "EXTERNAL_",       // 外部表元数据
        "DATABUCKET",      // 数据桶
        "PLAN_STATISTICS", // 执行计划统计
        "HIST_CHECK_INFO", // 历史检查信息
        "SCOL_DELETE_BITMAP", // 列存删除位图
    };

    // Oracle/YashanDB 保留字列表
    private static final Set<String> RESERVED_WORDS = new HashSet<>(Arrays.asList(
        "ACCESS", "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUDIT",
        "BETWEEN", "BY", "CHAR", "CHECK", "CLUSTER", "COLUMN", "COMMENT",
        "COMPRESS", "CONNECT", "CREATE", "CURRENT", "DATE", "DECIMAL", "DEFAULT",
        "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "EXCLUSIVE", "EXISTS",
        "FILE", "FLOAT", "FOR", "FROM", "GRANT", "GROUP", "HAVING", "IDENTIFIED",
        "IMMEDIATE", "IN", "INCREMENT", "INDEX", "INITIAL", "INSERT", "INTEGER",
        "INTERSECT", "INTO", "IS", "LEVEL", "LIKE", "LOCK", "LONG", "MAXEXTENTS",
        "MINUS", "MLSLABEL", "MODE", "MODIFY", "NOAUDIT", "NOCOMPRESS", "NOT",
        "NOWAIT", "NULL", "NUMBER", "OF", "OFFLINE", "ON", "ONLINE", "OPTION",
        "OR", "ORDER", "PCTFREE", "PRIOR", "PRIVILEGES", "PUBLIC", "RAW",
        "RENAME", "RESOURCE", "REVOKE", "ROW", "ROWID", "ROWNUM", "ROWS",
        "SELECT", "SESSION", "SET", "SHARE", "SIZE", "SMALLINT", "START",
        "SUCCESSFUL", "SYNONYM", "SYSDATE", "TABLE", "THEN", "TO", "TRIGGER",
        "UID", "UNION", "UNIQUE", "UPDATE", "USER", "VALIDATE", "VALUES",
        "VARCHAR", "VARCHAR2", "VIEW", "WHENEVER", "WHERE", "WITH",
        "PASSWORD"
    ));

    public YashanDbDatabase() {
        super.setCurrentDateTimeFunction("SYSTIMESTAMP");
    }

    @Override
    public String getShortName() {
        return "yashandb";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return PRODUCT_NAME;
    }

    @Override
    public int getPriority() {
        return PRIORITY_DEFAULT;
    }

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) throws DatabaseException {
        String databaseProductName = conn.getDatabaseProductName();
        return databaseProductName != null && databaseProductName.toLowerCase().contains("yashan");
    }

    @Override
    public String getDefaultDriver(String url) {
        if (url != null && url.startsWith("jdbc:yasdb")) {
            return "com.yashandb.jdbc.Driver";
        }
        return null;
    }

    @Override
    public Integer getDefaultPort() {
        return 1688;
    }

    @Override
    public boolean supportsSchemas() {
        // YashanDB 和 Oracle 一样，使用 schema 而非 catalog
        return false;
    }

    @Override
    public boolean supportsCatalogs() {
        return true;
    }

    @Override
    public String getJdbcCatalogName(CatalogAndSchema schema) {
        return null;
    }

    @Override
    public String getJdbcSchemaName(CatalogAndSchema schema) {
        return correctObjectName(
            schema.getCatalogName() != null ? schema.getCatalogName() : schema.getSchemaName(),
            Schema.class
        );
    }

    @Override
    public boolean supportsSequences() {
        return true;
    }

    @Override
    public boolean supportsInitiallyDeferrableColumns() {
        return true;
    }

    @Override
    public boolean supportsTablespaces() {
        return true;
    }

    @Override
    public boolean supportsAutoIncrement() {
        // YashanDB v23.4.2+ 支持 GENERATED BY DEFAULT AS IDENTITY
        try {
            int major = getDatabaseMajorVersion();
            int minor = getDatabaseMinorVersion();
            int patch = getDatabasePatchVersion();
            // IDENTITY 支持从 23.4.2 开始
            return (major > 23) ||
                   (major == 23 && minor > 4) ||
                   (major == 23 && minor == 4 && patch >= 2);
        } catch (DatabaseException e) {
            // 无法获取版本时，保守假设不支持
            return false;
        }
    }

    @Override
    public boolean supportsRestrictForeignKeys() {
        return true;  // YashanDB 支持 ON DELETE RESTRICT（与 Oracle 兼容）
    }

    @Override
    public boolean supportsDropTableCascadeConstraints() {
        return true;  // YashanDB 支持 DROP TABLE ... CASCADE CONSTRAINTS（与 Oracle 兼容）
    }

    @Override
    protected String getAutoIncrementClause() {
        return "GENERATED BY DEFAULT AS IDENTITY";
    }

    @Override
    public boolean isReservedWord(String objectName) {
        return RESERVED_WORDS.contains(objectName.toUpperCase());
    }

    @Override
    public String generatePrimaryKeyName(String tableName) {
        String primaryKeyName = "PK_" + tableName.toUpperCase(Locale.US);
        if (primaryKeyName.length() > 64) {  // YashanDB 标识符最大长度 64 bytes
            return primaryKeyName.substring(0, 64);
        }
        return primaryKeyName;
    }

    @Override
    public String getDateLiteral(String isoDate) {
        // YashanDB 使用 Oracle 风格的日期函数
        String normalLiteral = super.getDateLiteral(isoDate);

        if (isDateOnly(isoDate)) {
            return "TO_DATE(" + normalLiteral + ", 'YYYY-MM-DD')";
        } else if (isTimeOnly(isoDate)) {
            return "TO_DATE(" + normalLiteral + ", 'HH24:MI:SS')";
        } else if (isTimestamp(isoDate)) {
            return "TO_TIMESTAMP(" + normalLiteral + ", 'YYYY-MM-DD HH24:MI:SS.FF')";
        } else if (isDateTime(isoDate)) {
            int seppos = normalLiteral.lastIndexOf('.');
            if (seppos != -1) {
                normalLiteral = normalLiteral.substring(0, seppos) + "'";
            }
            return "TO_DATE(" + normalLiteral + ", 'YYYY-MM-DD HH24:MI:SS')";
        }
        return "UNSUPPORTED:" + isoDate;
    }

    @Override
    protected String getConnectionCatalogName() throws DatabaseException {
        if (getConnection() instanceof OfflineConnection) {
            return getConnection().getCatalog();
        }

        if (!(getConnection() instanceof JdbcConnection)) {
            return defaultCatalogName;
        }

        try {
            // YashanDB 使用 sys_context 获取当前 schema
            return Scope.getCurrentScope().getSingleton(ExecutorService.class)
                .getExecutor("jdbc", this)
                .queryForObject(
                    new RawCallStatement("select sys_context('userenv', 'current_schema') from dual"),
                    String.class
                );
        } catch (Exception e) {
            Scope.getCurrentScope().getLog(getClass()).info("Error getting default schema", e);
        }
        return null;
    }

    @Override
    protected SqlStatement getConnectionSchemaNameCallStatement() {
        return new RawCallStatement("select SYS_CONTEXT('USERENV','CURRENT_SCHEMA') as schema_name from dual");
    }

    /**
     * 判断数据库对象是否为系统对象。
     *
     * 【过滤架构】两层互补机制：
     *
     * 第1层 — Schema 级过滤（仅纯内部 schema）：
     *   SYSTEM_SCHEMAS 只包含 CTXSYS、XDB、YMP_DEFAULT 等纯内部 schema。
     *   这些 schema 下永远不会存在用户业务表，可以安全地整体过滤。
     *   注意：SYS、SYSTEM、DBSNMP 等不在此列表中，因为它们可能包含业务表。
     *
     * 第2层 — 名称模式过滤（主要机制）：
     *   SYSTEM_TABLE_PREFIXES 定义了所有已知的系统表/系统对象名称前缀。
     *   无论对象在哪个 schema 下，只要名称匹配就过滤。
     *   这是过滤散落在 SYS、SYSTEM 等 schema 中系统表的核心机制。
     *
     * 【调用链】
     *   YashanDbTableSnapshotGenerator.addTo()
     *     → 遍历 JDBC 元数据返回的表
     *     → 对每张表调用 database.isSystemObject(table)
     *     → 本方法先检查 schema，再检查表名模式
     *     → 系统表被跳过，业务表被添加到快照
     */
    @Override
    public boolean isSystemObject(DatabaseObject example) {
        if (example == null) {
            return false;
        }

        // Liquibase 自身的 tracking 表（DATABASECHANGELOG、DATABASECHANGELOGLOCK）
        // 即使在 SYS schema 下也不能被过滤，否则 Liquibase 无法正常工作
        if (this.isLiquibaseObject(example)) {
            return false;
        }

        // ── 第1层：Schema 级过滤 ──
        if (example instanceof Schema) {
            // Schema 对象本身：检查名称是否在纯内部 schema 列表中
            final String schemaName = example.getName();
            if (schemaName != null && SYSTEM_SCHEMAS.contains(schemaName.toUpperCase())) {
                return true;
            }
            // Schema 的 catalog 名称也可能匹配（Oracle 兼容模式下 schema ≈ catalog）
            final Schema parentSchema = example.getSchema();
            if (parentSchema != null) {
                final String catalogName = parentSchema.getCatalogName();
                if (catalogName != null && SYSTEM_SCHEMAS.contains(catalogName.toUpperCase())) {
                    return true;
                }
            }
        } else {
            // 非 Schema 对象（表、索引、序列等）：
            // 如果所属 schema 是纯内部 schema（CTXSYS/XDB/YMP_DEFAULT），整体过滤
            final Schema objSchema = example.getSchema();
            if (objSchema != null && isSystemObject(objSchema)) {
                return true;
            }
        }

        // ── 第2层：名称模式过滤（主要机制）──
        // 无论对象在哪个 schema 下，只要名称匹配系统表前缀就过滤。
        // 这确保了 SYS、SYSTEM 等 schema 下的系统字典表被正确过滤，
        // 同时不影响同 schema 下的用户业务表。
        final String objectName = example.getName();
        if (objectName != null && isSystemTableName(objectName)) {
            return true;
        }

        return super.isSystemObject(example);
    }

    /**
     * 判断表名是否为系统表。
     *
     * 规则1：以 "$" 结尾 → 系统表（Oracle/YashanDB 数据字典表的统一命名约定）
     *   例：OBJ$, COL$, TAB$, USER$, SEQ$, VIEW$, RECYCLEBIN$
     *
     * 规则2：匹配 SYSTEM_TABLE_PREFIXES → 系统表
     *   a) $ 在名称中间的：BIN$xxx, AUD$UNIFIED, YLS$POL, OL$HINTS
     *   b) 不含 $ 的组件表：WRH$_SQLSTAT, SCHEDULER$_JOB, SLOW_LOG
     */
    private boolean isSystemTableName(final String name) {
        // 规则1：以 "$" 结尾 = Oracle/YashanDB 数据字典表
        if (name.endsWith("$")) {
            return true;
        }
        // 规则2：匹配已知系统表前缀
        for (final String prefix : SYSTEM_TABLE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean jdbcCallsCatalogsSchemas() {
        return true;
    }

    @Override
    public void setConnection(DatabaseConnection conn) {
        super.setConnection(conn);

        // 尝试获取数据库版本
        if (conn instanceof JdbcConnection) {
            try {
                Connection sqlConn = ((JdbcConnection) conn).getUnderlyingConnection();
                DatabaseMetaData metaData = sqlConn.getMetaData();
                String version = metaData.getDatabaseProductVersion();
                if (version != null) {
                    Matcher matcher = VERSION_PATTERN.matcher(version);
                    if (matcher.matches()) {
                        this.databaseMajorVersion = Integer.valueOf(matcher.group(1));
                        this.databaseMinorVersion = Integer.valueOf(matcher.group(2));
                        this.databasePatchVersion = Integer.valueOf(matcher.group(3));
                    }
                }
            } catch (Exception e) {
                Scope.getCurrentScope().getLog(getClass()).info("Could not get YashanDB version", e);
            }
        }
    }

    @Override
    public int getDatabaseMajorVersion() throws DatabaseException {
        if (databaseMajorVersion != null) {
            return databaseMajorVersion;
        }
        return super.getDatabaseMajorVersion();
    }

    @Override
    public int getDatabaseMinorVersion() throws DatabaseException {
        if (databaseMinorVersion != null) {
            return databaseMinorVersion;
        }
        return super.getDatabaseMinorVersion();
    }

    public int getDatabasePatchVersion() throws DatabaseException {
        if (databasePatchVersion != null) {
            return databasePatchVersion;
        }
        return 0;  // 默认返回 0，表示未知补丁版本
    }
}
