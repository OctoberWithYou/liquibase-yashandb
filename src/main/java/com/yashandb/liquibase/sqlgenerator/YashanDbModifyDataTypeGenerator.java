package com.yashandb.liquibase.sqlgenerator;

import com.yashandb.liquibase.database.YashanDbDatabase;
import liquibase.database.Database;
import liquibase.sqlgenerator.core.ModifyDataTypeGenerator;
import liquibase.statement.core.ModifyDataTypeStatement;

/**
 * YashanDB 专用的 ModifyDataType SQL 生成器。
 *
 * 【重写原因】
 * 父类 ModifyDataTypeGenerator 通过 instanceof 判断数据库类型来拼装修改列类型的 SQL：
 *
 *   父类 generateSql() 内部调用：
 *     alterTable += getModifyString(database);     // "MODIFY" 或 "ALTER COLUMN"
 *     alterTable += getPreDataTypeString(database); // " " 或 " TYPE " 或 " SET DATA TYPE "
 *     alterTable += newDataType;
 *
 *   父类 getModifyString() 中：
 *     if (database instanceof OracleDatabase || ...) return "MODIFY";
 *     else return "ALTER COLUMN";                    // ← YashanDbDatabase 走了这里
 *
 *   父类 getPreDataTypeString() 中：
 *     if (database instanceof OracleDatabase || ...) return " ";
 *     else return " TYPE ";                          // ← YashanDbDatabase 走了这里
 *
 * 结果 YashanDbDatabase 生成了：
 *   ALTER TABLE SYS.example_table ALTER COLUMN name TYPE VARCHAR(3000)
 *
 * YashanDB 不支持这种 PostgreSQL 风格的 "ALTER COLUMN ... TYPE" 语法，
 * 解析时报 YAS-04115 "SLICE|MCOL" expected but missing。
 *
 * 正确的语法应该是 Oracle 风格的：
 *   ALTER TABLE SYS.example_table MODIFY name VARCHAR(3000)
 *
 * 由于 YashanDbDatabase 不继承 OracleDatabase（为避免 JdbcDatabaseSnapshot 中 oracleQuery()
 * 分支产生 USING 语法不兼容），instanceof OracleDatabase 判断为 false，
 * 因此必须通过重写本生成器来修正 SQL 语法。
 */
public class YashanDbModifyDataTypeGenerator extends ModifyDataTypeGenerator {

    /**
     * 【重写原因】
     * 返回 PRIORITY_DATABASE 使本生成器优先级高于父类 ModifyDataTypeGenerator 的 PRIORITY_DEFAULT。
     * Liquibase 的 SqlGeneratorFactory 在选择生成器时按优先级排序，
     * PRIORITY_DATABASE 确保当 database 是 YashanDbDatabase 时，本生成器一定先于父类被选中。
     */
    @Override
    public int getPriority() {
        return PRIORITY_DATABASE;
    }

    /**
     * 【重写原因】
     * 限定本生成器仅对 YashanDbDatabase 生效。
     * 父类 supports() 对除 SQLite 和 DB2z 以外的所有数据库都返回 true，
     * 如果不重写此方法，本生成器会对所有数据库生效，影响其他数据库类型的行为。
     */
    @Override
    public boolean supports(ModifyDataTypeStatement statement, Database database) {
        return database instanceof YashanDbDatabase;
    }

    /**
     * 【重写原因 —— 核心方法】
     * 父类 getModifyString() 通过 instanceof 判断：
     *   if (database instanceof OracleDatabase || instanceof MySQLDatabase || ...) {
     *       return "MODIFY";        // ← YashanDB 需要的关键字
     *   } else {
     *       return "ALTER COLUMN";  // ← YashanDbDatabase 实际走了这里
     *   }
     *
     * YashanDB 使用 Oracle 风格的 MODIFY 语法，而非 ANSI SQL 的 ALTER COLUMN。
     * 直接返回 "MODIFY" 覆盖父类的 instanceof 判断逻辑。
     */
    @Override
    protected String getModifyString(Database database) {
        return "MODIFY";
    }

    /**
     * 【重写原因 —— 核心方法】
     * 父类 getPreDataTypeString() 通过 instanceof 判断：
     *   if (database instanceof OracleDatabase || instanceof MySQLDatabase || ...) {
     *       return " ";              // ← YashanDB 需要的：列名后直接跟类型
     *   } else if (database instanceof DerbyDatabase || instanceof AbstractDb2Database) {
     *       return " SET DATA TYPE ";
     *   } else {
     *       return " TYPE ";         // ← YashanDbDatabase 实际走了这里
     *   }
     *
     * YashanDB 语法为 "MODIFY col_name VARCHAR(3000)"，列名和数据类型之间只需要一个空格。
     * 而父类通用分支返回 " TYPE "，导致生成了 "MODIFY col_name TYPE VARCHAR(3000)"，
     * 这在 YashanDB 中是非法语法。直接返回 " " 覆盖父类逻辑。
     */
    @Override
    protected String getPreDataTypeString(Database database) {
        return " ";
    }
}
