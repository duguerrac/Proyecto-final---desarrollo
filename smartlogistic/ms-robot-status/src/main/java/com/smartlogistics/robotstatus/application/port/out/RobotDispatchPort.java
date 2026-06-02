package com.smartlogistics.robotstatus.application.port.out;

/**
 * Output port for dispatching robot commands and publishing
 * order/package notification events via messaging.
 * Pure Java — zero framework imports.
 */
public interface RobotDispatchPort {

    /**
     * Send a GOTO command to a specific robot.
     *
     * @param robotId    the robot to command
     * @param target     the target location
     * @param orderId    the associated order ID
     */
    void sendGoToCommand(String robotId, String target, long orderId);

    /**
     * Send a STOCK_IN mission command to a robot for package transport.
     *
     * @param robotId        the robot to dispatch
     * @param packageId      the package ID
     * @param sku            the item SKU
     * @param receptionSpot  reception spot code
     * @param targetSpot     target spot code
     * @param itemId         inventory item ID
     * @param quantity       item quantity
     */
    void sendStockInMission(String robotId, long packageId, String sku,
                            String receptionSpot, String targetSpot,
                            long itemId, int quantity);

    /**
     * Notify that an order has been dispatched to a robot.
     *
     * @param orderId the order ID
     * @param robotId the assigned robot ID
     */
    void publishOrderDispatched(long orderId, String robotId);

    /**
     * Notify that a package has been picked up by a robot.
     *
     * @param packageId the package ID
     * @param robotId   the robot that picked it up
     */
    void publishPackageTaken(String packageId, String robotId);

    /**
     * Notify that a package has been delivered.
     *
     * @param packageId the package ID
     */
    void publishPackageDelivered(String packageId);
}