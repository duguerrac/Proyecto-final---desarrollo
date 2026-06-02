// ==================== AUTH ====================
export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  username: string;
  email: string;
}

// ==================== ROBOT ====================
export type RobotStatusEnum = 'IDLE' | 'MOVING_TO_PICKUP' | 'PICKING_UP' | 'MOVING_TO_DELIVERY' | 'DELIVERING' | 'CHARGING' | 'ERROR';

// Matches backend RobotStatusController.RobotResponse
export interface Robot {
  robotId: string;
  name: string;
  batteryLevel: number;
  available: boolean;
  currentLocation: string;   // "RP-R00-C00" format → Row 0, Col 0
  operationalMode: string;   // RobotStatus enum name
}

// SSE telemetry event payload (raw RabbitMQ message from simulation)
export interface RobotTelemetry {
  robot: Robot;
  timestamp: string;
}

// ==================== PACKAGE ====================
export type PackageStatus = 'RECEIVED' | 'IN_TRANSIT' | 'DELIVERED' | 'STORED' | 'PICKED' | 'DISPATCHED';

export interface Package {
  id: string;
  sku: string;
  quantity: number;
  status: PackageStatus;
  receptionSpotCode: string;
  targetSpotCode: string;
  robotId: string | null;
  createdAt: string;
  // Legacy fields (may not be returned by all endpoints)
  trackingCode?: string;
  productName?: string;
  weight?: number;
  spotId?: string | null;
  spotLabel?: string | null;
  storedAt?: string | null;
}

export interface ReceivePackageRequest {
  sku: string;
  quantity: number;
  receptionSpotCode?: string;
}

// ==================== ORDER ====================
export type OrderStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface OrderLine {
  id?: string;
  sku: string;
  quantity: number;
  productName?: string;
  productId?: string;
  spotLabel?: string | null;
}

export interface Order {
  id: string;
  orderNumber?: string;      // may not be returned by backend
  status: OrderStatus;
  items?: OrderItem[];       // frontend alias for backend 'lines'
  lines?: OrderLine[];       // backend field name
  pickupSpotCode?: string;
  deliveryPoint?: string;
  robotId?: string | null;
  createdAt: string;
  completedAt?: string | null;
}

export interface OrderItem {
  id?: string;
  productId?: string;
  productName?: string;
  sku?: string;
  quantity: number;
  spotLabel?: string | null;
}

export interface CreateOrderRequest {
  items?: { productId: string; quantity: number }[];
  lines?: { sku: string; quantity: number }[];
  pickupSpotCode?: string;
  deliveryPoint?: string;
}

// ==================== WAREHOUSE LAYOUT ====================
export type CellType = 'EMPTY' | 'SHELF' | 'CHARGING' | 'DELIVERY_DOCK' | 'RECEIVING_DOCK' | 'ROBOT_SPAWN' | 'OBSTACLE';

export interface LayoutCell {
  rowIndex: number;
  colIndex: number;
  cellType: CellType;
}

export interface Spot {
  id: string;
  label: string;
  row: number;
  column: number;
  spotType: string;
  occupied: boolean;
  items: SpotItem[];
}

export interface SpotItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
}

export interface WarehouseLayout {
  id: string;
  name: string;
  rows: number;
  cols: number;
  cellSize: number;
  status: string;
  cells: LayoutCell[];
  spots: Spot[];
}

// ==================== INVENTORY ====================
export interface InventoryItem {
  id: string;
  productId: string;
  productName: string;
  quantity: number;
  spotId: string;
  spotLabel: string;
  storedAt: string;
}

// ==================== DASHBOARD ====================
export interface DashboardStats {
  totalPackages: number;
  pendingOrders: number;
  activeRobots: number;
  occupiedSpots: number;
  totalSpots: number;
}