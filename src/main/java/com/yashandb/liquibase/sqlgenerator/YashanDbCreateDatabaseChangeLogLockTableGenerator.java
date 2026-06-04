package com.yashandb.liquibase.sqlgenerator;

import com.yashandb.liquibase.database.YashanDbDatabase;
import liquibase.database.Database;
import liquibase.sqlgenerator.core.CreateDatabaseChangeLogLockTableGenerator;
import liquibase.statement.core.CreateDatabaseChangeLogLockTableStatement;

public class YashanDbCreateDatabaseChangeLogLockTableGenerator extends CreateDatabaseChangeLogLockTableGenerator {

    protected String getDateTimeTypeString(Database database) {
        if (database instanceof YashanDbDatabase) {
            return "timestamp";
        }
        return super.getDateTimeTypeString(database);
    }

    @Override
    public int getPriority() {
        return super.getPriority() + 1;
    }

    @Override
    public boolean supports(CreateDatabaseChangeLogLockTableStatement statement, Database database) {
        return database instanceof YashanDbDatabase;
    }
}
