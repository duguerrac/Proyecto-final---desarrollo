-- Seed inventory items with clothing and shoes
INSERT INTO inventory_item (sku, name) VALUES
    ('SHO-001', 'Nike Air Max 90'),
    ('SHO-002', 'Adidas Ultraboost'),
    ('SHO-003', 'Puma RS-X'),
    ('SHO-004', 'Converse Chuck Taylor'),
    ('SHO-005', 'Vans Old Skool'),
    ('SHT-001', 'Camiseta Polo Classic'),
    ('SHT-002', 'Camisa Formal Manga Larga'),
    ('SHT-003', 'Camiseta Algodón Básica'),
    ('SHT-004', 'Polo Deportivo Dri-FIT'),
    ('PNT-001', 'Jeans Slim Fit'),
    ('PNT-002', 'Jogger Deportivo'),
    ('PNT-003', 'Pantalón Formal'),
    ('JCK-001', 'Chaqueta de Cuero'),
    ('JCK-002', 'Bomber Jacket'),
    ('ACC-001', 'Gorra Baseball'),
    ('ACC-002', 'Cinturón Cuero')
ON CONFLICT (sku) DO NOTHING;