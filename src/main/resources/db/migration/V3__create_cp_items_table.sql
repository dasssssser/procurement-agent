CREATE TABLE IF NOT EXISTS cp_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES tasks(id) ON DELETE CASCADE,
    supplier_name VARCHAR(255),
    product_name VARCHAR(255),
    price DECIMAL(15, 2),
    quantity INTEGER,
    characteristics JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cp_items_product_price ON cp_items(product_name, price);
CREATE INDEX IF NOT EXISTS idx_cp_items_task_id ON cp_items(task_id);