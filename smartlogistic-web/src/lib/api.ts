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
    return this.request<Package[]>('/packages');
  }

  async getPackage(id: string): Promise<Package> {
    return this.request<Package>(`/packages/${id}`);
  }

  async receivePackage(data: ReceivePackageRequest): Promise<Package> {
    return this.request<Package>('/packages/receive', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  // ==================== ORDERS ====================
  async getOrders(): Promise<Order[]> {
    return this.request<Order[]>('/orders');
  }

  async getOrder(id: string): Promise<Order> {
    return this.request<Order>(`/orders/${id}`);
  }

  async createOrder(data: CreateOrderRequest): Promise<Order> {
    return this.request<Order>('/orders', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  }

  // ==================== WAREHOUSE LAYOUT ====================
  async getWarehouseLayout(): Promise<WarehouseLayout> {
    return this.request<WarehouseLayout>('/layouts/active');
  }

  async getSpot(id: string): Promise<Spot> {
    return this.request<Spot>(`/spots/${id}`);
  }

  async getSpotItems(spotId: string) {
    return this.request<{ id: string; productId: string; productName: string; quantity: number }[]>(`/spots/${spotId}/items`);
  }

  // Get items stored at a specific grid cell (row, col)
  async getSpotItemsByCell(row: number, col: number): Promise<{ itemId: number; name: string; sku: string; quantityAvailable: number }[]> {
    return this.request(`/spots/by-cell/${row}/${col}/items`);
  }

  // ==================== SPOTS ====================
  async getSpots(): Promise<{ id: number; code: string; aisle: string; section: string; x: number; y: number; items: { productId: number; productName: string; sku: string; quantity: number }[] }[]> {
    return this.request('/spots');
  }

  // ==================== ROBOTS ====================
  async getRobots(): Promise<Robot[]> {
    return this.request<Robot[]>('/robots');
  }

  async getRobot(id: string): Promise<Robot> {
    return this.request<Robot>(`/robots/${id}`);
  }

  // ==================== DASHBOARD STATS ====================
  async getDashboardStats(): Promise<DashboardStats> {
    const [packages, orders, robots, layout] = await Promise.all([
      this.request<Package[]>('/packages').catch(() => []),
      this.request<Order[]>('/orders').catch(() => []),
      this.request<Robot[]>('/robots').catch(() => []),
      this.request<WarehouseLayout>('/layouts/active').catch(() => ({ id: '', name: '', rows: 0, cols: 0, cellSize: 0, status: '', cells: [], spots: [] } as WarehouseLayout)),
    ]);

    return {
      totalPackages: packages.length,
      pendingOrders: orders.filter(o => o.status === 'PENDING' || o.status === 'IN_PROGRESS').length,
      activeRobots: robots.filter(r => r.operationalMode !== 'IDLE' && r.operationalMode !== 'CHARGING').length,
      occupiedSpots: (layout.spots || []).filter(s => s.occupied).length,
      totalSpots: (layout.spots || []).filter(s => s.spotType === 'STORAGE').length,
    };
  }
}

export const api = new ApiClient();