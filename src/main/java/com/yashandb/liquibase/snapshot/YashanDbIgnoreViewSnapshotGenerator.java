package com.yashandb.liquibase.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotGenerator;
import liquibase.snapshot.jvm.ViewSnapshotGenerator;
import liquibase.structure.DatabaseObject;

/**
 * YashanDB 专用的 ViewSnapshotGenerator，禁用视图快照。
 *
 * YashanDB 当前不需要通过 Liquibase 管理视图，因此通过继承 ViewSnapshotGenerator
 * 并重写 snapshotObject() 与 addTo() 为空实现，使 Liquibase 在快照阶段跳过所有视图对象。
 */
public class YashanDbIgnoreViewSnapshotGenerator extends ViewSnapshotGenerator {

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot)
            throws DatabaseException {
        return null;
    }

    @Override
    public Class<? extends SnapshotGenerator>[] replaces() {
        return new Class[]{ ViewSnapshotGenerator.class };
    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot)
            throws DatabaseException, InvalidExampleException {
        // do nothing — 不将任何视图添加到快照
    }
}
