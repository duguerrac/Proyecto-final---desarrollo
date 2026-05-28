package com.smartlogistics.robotstatus.domain.model;

/**
 * Value Object representing the types of commands a robot can receive.
 * Pure Java — zero framework imports.
 */
public enum CommandType {
    GO_TO,
    PICK_UP,
    DROP_OFF,
    RETURN_DOCK
}