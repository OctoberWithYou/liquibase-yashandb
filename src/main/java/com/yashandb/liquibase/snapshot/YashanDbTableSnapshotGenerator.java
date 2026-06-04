package com.yashandb.liquibase.snapshot;

import com.yashandb.liquibase.database.YashanDbDatabase;
import liquibase.database.Database;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.CachedRow;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.JdbcDatabaseSnapshot;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.TableSnapshotGenerator;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;

import java.sql.SQLException;
import java.util.List;

/**
 * YashanDB 专用的 TableSnapshotGenerator，过滤系统表。
 *
 * 【重写原因】
 * Liquibase 的 SnapshotGeneratorChain.snapshot() 只在处理单个对象时调用 isSystemObject()，
 * 但 TableSnapshotGenerator.addTo() 批量枚举表时不会检查每个表是否为系统对象。
 * 这导致 YashanDB 的 SYS schema 下所有系统字典表（OBJ$, COL$, TAB$ 等）全部出现在快照中。
 *
 * 本生成器继承 TableSnapshotGenerator 并重写 addTo() 方法，在遍历 JDBC 元数据返回的表时，
 * 调用 database.isSystemObject() 过滤掉系统表，只保留业务表。
 */
public class YashanDbTableSnapshotGenerator extends TableSnapshotGenerator {

    @Override
    public int getPriority(Class<? extends DatabaseObject> objectType, Database database) {
        if (database instanceof YashanDbDatabase) {
            return PRIORITY_DATABASE;
        }
        return PRIORITY_NONE;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{ TableSnapshotGenerator.class };
    }

    /**
     * 【重写原因】
     * 父类 addTo() 遍历 JDBC DatabaseMetaData.getTables() 返回的所有表，
     * 直接将每个表添加到 Schema 中，不检查是否为系统对象。
     *
     * 本方法在添加表之前调用 database.isSystemObject(table) 过滤系统表，
     * 确保快照结果只包含业务表（如 EXAMPLE_TABLE 等）。
     */
    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Table.class)) {
            return;
        }

        if (foundObject instanceof Schema) {
            Database database = snapshot.getDatabase();
            Schema schema = (Schema) foundObject;

            try {
                List<CachedRow> tableMetaDataRs = ((JdbcDatabaseSnapshot) snapshot)
                        .getMetaDataFromCache()
                        .getTables(
                                ((liquibase.database.AbstractJdbcDatabase) database).getJdbcCatalogName(schema),
                                ((liquibase.database.AbstractJdbcDatabase) database).getJdbcSchemaName(schema),
                                null);

                for (CachedRow row : tableMetaDataRs) {
                    String tableName = row.getString("TABLE_NAME");
                    Table tableExample = (Table) new Table()
                            .setName(cleanNameFromDatabase(tableName, database))
                            .setSchema(schema);

                    // 关键过滤：调用 isSystemObject() 跳过系统表
                    if (database.isSystemObject(tableExample)) {
                        continue;
                    }

                    schema.addDatabaseObject(tableExample);
                }
            } catch (SQLException e) {
                throw new DatabaseException(e);
            }
        }
    }
}
