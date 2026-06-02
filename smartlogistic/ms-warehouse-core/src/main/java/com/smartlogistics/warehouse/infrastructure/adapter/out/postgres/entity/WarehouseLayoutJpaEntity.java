package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "warehouse_layout")
public class WarehouseLayoutJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "rows_count", nullable = false)
    private int rowsCount;

    @Column(name = "cols_count", nullable = false)
    private int colsCount;

    @Column(name = "cell_size", nullable = false, precision = 8, scale = 2)
    private BigDecimal cellSize;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getRowsCount() { return rowsCount; }
    public void setRowsCount(int rowsCount) { this.rowsCount = rowsCount; }
    public int getColsCount() { return colsCount; }
    public void setColsCount(int colsCount) { this.colsCount = colsCount; }
    public BigDecimal getCellSize() { return cellSize; }
    public void setCellSize(BigDecimal cellSize) { this.cellSize = cellSize; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }
}