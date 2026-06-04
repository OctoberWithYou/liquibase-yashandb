package com.yashandb.liquibase.sqlgenerator;

import com.yashandb.liquibase.database.YashanDbDatabase;
import liquibase.database.Database;
import liquibase.sqlgenerator.core.CreateDatabaseChangeLogTableGenerator;
import liquibase.statement.core.CreateDatabaseChangeLogTableStatement;

public class YashanDbCreateDatabaseChangeLogTableGenerator extends CreateDatabaseChangeLogTableGenerator {

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
    public boolean supports(CreateDatabaseChangeLogTableStatement statement, Database database) {
        return database instanceof YashanDbDatabase;
    }
}
