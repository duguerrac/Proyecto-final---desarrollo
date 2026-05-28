package com.smartlogistics.warehouse.application.port.in;

import com.smartlogistics.warehouse.application.dto.InventorySpotResponse;
import java.util.List;

public interface GetInventorySpotsUseCase {
    List<InventorySpotResponse> getBySku(String sku);
}
