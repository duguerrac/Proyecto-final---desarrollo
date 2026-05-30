package com.smartlogistics.robotstatus.application.port.in;

/**
 * Input port for publishing robot status snapshots.
 * Pure Java — zero framework imports.
 */
public interface PublishSnapshotUseCase {

    /**
     * Publish a batch snapshot of all robots to the message bus.
     */
    void publishBatchSnapshot();
}