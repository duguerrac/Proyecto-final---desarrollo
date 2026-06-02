import { AuthResponse, LoginRequest, RegisterRequest, Package, ReceivePackageRequest, Order, CreateOrderRequest, WarehouseLayout, Spot, Robot, DashboardStats } from './types';

// Always point to the nginx gateway that routes to all microservices.
// In Docker: nginx is at http://nginx:80 (internal) or http://localhost:8086 (host).
// For local dev: we use port 8086 on the host.
const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8086/api';

class ApiClient {
  private getToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem('auth_token');
  }

  private getHeaders(): HeadersInit {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };
    const token = this.getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
    return headers;
  }

  private async request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const response = await fetch(`${API_URL}${endpoint}`, {
      ...options,
      headers: {
        ...this.getHeaders(),
        ...options.headers,
      },
    });

    if (response.status === 401) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem('auth_token');
        localStorage.removeItem('user');
        window.location.href = '/login';
      }
      throw new Error('Unauthorized');
    }

    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: 'Request failed' }));
      throw new Error(error.message || `HTTP ${response.status}`);
    }

    // For 204 No Content
    if (response.status === 204) return {} as T;

    return response.json();
  }

  // ==================== AUTH ====================
  async login(data: LoginRequest): Promise<AuthResponse> {
    return this.request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  async register(data: RegisterRequest): Promise<AuthResponse> {
    return this.request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  // ==================== PACKAGES ====================
  async getPackages(): Promise<Package[]> {
    const raw = await this.request<any[]>('/packages');
    return (raw || []).map(p => ({
      id: String(p.id ?? ''),
      sku: p.sku ?? '',
      quantity: p.quantity ?? 0,
      status: p.status ?? 'RECEIVED',
      receptionSpotCode: p.receptionSpotCode ?? '',
      targetSpotCode: p.targetSpotCode ?? '',
      robotId: p.robotId ?? null,
      createdAt: p.createdAt ?? '',
      trackingCode: p.trackingCode,
      productName: p.productName,
      weight: p.weight,
      spotId: p.spotId,
      spotLabel: p.spotLabel,
      storedAt: p.storedAt,
    }));
  }

  async getPackage(id: string): Promise<Package> {
    const p = await this.request<any>(`/packages/${id}`);
    return {
      id: String(p.id ?? ''),
      sku: p.sku ?? '',
      quantity: p.quantity ?? 0,
      status: p.status ?? 'RECEIVED',
      receptionSpotCode: p.receptionSpotCode ?? '',
      targetSpotCode: p.targetSpotCode ?? '',
      robotId: p.robotId ?? null,
      createdAt: p.createdAt ?? '',
    };
  }

  async receivePackage(data: ReceivePackageRequest): Promise<Package> {
    const p = await this.request<any>('/packages/receive', {
      method: 'POST',
      body: JSON.stringify(data),
    });
    return {
      id: String(p.id ?? ''),
      sku: p.sku ?? '',
      quantity: p.quantity ?? 0,
      status: p.status ?? 'RECEIVED',
      receptionSpotCode: p.receptionSpotCode ?? '',
      targetSpotCode: p.targetSpotCode ?? '',
      robotId: p.robotId ?? null,
      createdAt: p.createdAt ?? '',
    };
  }

  // ==================== ORDERS ====================
  private normalizeOrder(raw: any): Order {
    const lines = raw.lines || raw.items || [];
    return {
      id: String(raw.id ?? ''),
      orderNumber: raw.orderNumber || `ORD-${raw.id}`,
      status: raw.status || 'PENDING',
      items: lines.map((l: any) => ({
        id: l.id ?? String(l.sku ?? ''),
        productId: l.productId ?? l.sku ?? '',
        productName: l.productName ?? l.sku ?? '',
        sku: l.sku ?? '',
        quantity: l.quantity ?? 0,
        spotLabel: l.spotLabel ?? null,
      })),
      lines,
      pickupSpotCode: raw.pickupSpotCode,
      deliveryPoint: raw.deliveryPoint,
      robotId: raw.robotId ?? null,
      createdAt: raw.createdAt ?? '',
      completedAt: raw.completedAt ?? null,
    };
  }

  async getOrders(): Promise<Order[]> {
    const raw = await this.request<any[]>('/orders');
    return (raw || []).map(o => this.normalizeOrder(o));
  }

  async getOrder(id: string): Promise<Order> {
    const raw = await this.request<any>(`/orders/${id}`);
    return this.normalizeOrder(raw);
  }

  async createOrder(data: CreateOrderRequest): Promise<Order> {
    // Backend expects { lines: [{sku, quantity}], pickupSpotCode, deliveryPoint }
    const payload: any = {};
    if (data.lines && data.lines.length > 0) {
      payload.lines = data.lines;
    } else if (data.items && data.items.length > 0) {
      payload.lines = data.items.map(i => ({ sku: i.productId, quantity: i.quantity }));
    } else {
      payload.lines = [];
    }
    if (data.pickupSpotCode) payload.pickupSpotCode = data.pickupSpotCode;
    if (data.deliveryPoint) payload.deliveryPoint = data.deliveryPoint;
    const raw = await this.request<any>('/orders', {
      method: 'POST',
      body: JSON.stringify(payload),
    });
    return this.normalizeOrder(raw);
  }

  // ==================== WAREHOUSE LAYOUT ====================
  async getWarehouseLayout(): Promise<WarehouseLayout> {
    const raw = await this.request<any>('/layouts/active');
    return {
      id: String(raw?.id ?? ''),
      name: raw?.name ?? '',
      rows: Number(raw?.rows ?? 0),
      cols: Number(raw?.cols ?? 0),
      cellSize: Number(raw?.cellSize ?? 0),
      status: raw?.status ?? '',
      cells: raw?.cells || [],
      spots: raw?.spots || [],
    };
  }

  async getSpot(id: string): Promise<Spot> {
    return this.request<Spot>(`/spots/${id}`);
  }

  async getSpotItems(spotId: string) {
    return this.request<{ id: string; productId: string; productName: string; quantity: number }[]>(`/spots/${spotId}/items`);
  }

  // Get items stored at a specific grid cell (row, col)
  async getSpotItemsByCell(row: number, col: number): Promise<{ itemId: number; name: string; sku: string; quantityAvailable: number }[]> {
    try {
      const raw = await this.request<any[]>(`/spots/by-cell/${row}/${col}/items`);
      return (raw || []).map(item => ({
        itemId: item.productId ?? item.itemId ?? 0,
        name: item.productName ?? item.name ?? '',
        sku: item.sku ?? '',
        quantityAvailable: item.quantity ?? item.quantityAvailable ?? 0,
      }));
    } catch {
      return [];
    }
  }

  // ==================== SPOTS ====================
  async getSpots(): Promise<{ id: number; code: string; aisle: string; section: string; x: number; y: number; rootPointCode?: string; items: { productId: number; productName: string; sku: string; quantity: number }[] }[]> {
    return this.request('/spots');
  }

  // ==================== ROBOTS ====================
  async getRobots(): Promise<Robot[]> {
    const raw = await this.request<any[]>('/robots');
    return (raw || []).map(r => ({
      robotId: r.robotId || r.id || '',
      name: r.name || 'Unknown',
      batteryLevel: Number(r.batteryLevel ?? 0),
      available: r.available ?? true,
      currentLocation: r.currentLocation || '',
      operationalMode: r.operationalMode || 'IDLE',
    }));
  }

  async getRobot(id: string): Promise<Robot> {
    const r = await this.request<any>(`/robots/${id}/status`);
    return {
      robotId: r.robotId || r.id || id,
      name: r.name || 'Unknown',
      batteryLevel: Number(r.batteryLevel ?? 0),
      available: r.available ?? true,
      currentLocation: r.currentLocation || '',
      operationalMode: r.operationalMode || 'IDLE',
    };
  }

  // ==================== DASHBOARD STATS ====================
  async getDashboardStats(): Promise<DashboardStats> {
    const [packages, orders, robots, layout] = await Promise.all([
      this.getPackages().catch(() => []),
      this.getOrders().catch(() => []),
      this.getRobots().catch(() => []),
      this.getWarehouseLayout().catch(() => ({ id: '', name: '', rows: 0, cols: 0, cellSize: 0, status: '', cells: [], spots: [] } as WarehouseLayout)),
    ]);

    return {
      totalPackages: (packages || []).length,
      pendingOrders: (orders || []).filter(o => o.status === 'PENDING' || o.status === 'IN_PROGRESS').length,
      activeRobots: (robots || []).filter(r => r.operationalMode !== 'IDLE' && r.operationalMode !== 'CHARGING').length,
      occupiedSpots: (layout?.spots || []).filter(s => s.occupied).length,
      totalSpots: (layout?.spots || []).filter(s => s.spotType === 'STORAGE').length,
    };
  }
}

export const api = new ApiClient();