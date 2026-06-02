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
     * Send a STOCK_OUT mission command to a robot for order fulfillment.
     * Robot goes to pickupSpot (shelf) to pick items, then delivers to deliverySpot.
     *
     * @param robotId        the robot to dispatch
     * @param orderId        the order ID
     * @param pickupSpotCode spot code where items are stored (shelf)
     * @param deliverySpotCode spot code for delivery dock
     * @param itemSku        the item SKU
     * @param quantity       item quantity
     */
    void sendStockOutMission(String robotId, long orderId,
                             String pickupSpotCode, String deliverySpotCode,
                             String itemSku, int quantity);

    /**
     * Notify that a package has been delivered.
     *
     * @param packageId the package ID
     */
    void publishPackageDelivered(String packageId);

    /**
     * Notify that a package/order has been delivered with mission context.
     *
     * @param packageId   the package or order ID
     * @param missionType the mission type (STOCK_IN, STOCK_OUT, etc.)
     * @param spotCode    the spot code where delivery occurred
     */
    void publishPackageDelivered(String packageId, String missionType, String spotCode);
}
