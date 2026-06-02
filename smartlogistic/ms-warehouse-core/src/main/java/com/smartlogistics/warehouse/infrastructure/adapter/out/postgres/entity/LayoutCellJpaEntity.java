package com.smartlogistics.warehouse.infrastructure.adapter.out.postgres.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "layout_cell")
public class LayoutCellJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "layout_id", nullable = false)
    private Long layoutId;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "col_index", nullable = false)
    private int colIndex;

    @Column(name = "cell_type", nullable = false, length = 30)
    private String cellType;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLayoutId() { return layoutId; }
    public void setLayoutId(Long layoutId) { this.layoutId = layoutId; }
    public int getRowIndex() { return rowIndex; }
    public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
    public int getColIndex() { return colIndex; }
    public void setColIndex(int colIndex) { this.colIndex = colIndex; }
    public String getCellType() { return cellType; }
    public void setCellType(String cellType) { this.cellType = cellType; }
}