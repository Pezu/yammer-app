-- =============================================================================
-- First-class product catalog. Menus stop defining products inline — a menu leaf
-- now REFERENCES a catalog product, so "coca cola" is the same product on every
-- menu and consumption can be summarized per product.
-- =============================================================================
CREATE TABLE product (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id  UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    description  TEXT,
    price        NUMERIC(10, 2),
    vat_type_id  UUID REFERENCES vat_type(id),
    image_object VARCHAR(255)
);
CREATE INDEX idx_product_location_id ON product(location_id);

-- Existing orderable menu items carry no product mapping (dev data) — drop them,
-- then move the product-only columns off menu_item. image_object stays: categories
-- keep their own images (a product node's image comes from the product).
DELETE FROM menu_item WHERE orderable = true;
ALTER TABLE menu_item DROP COLUMN price;
ALTER TABLE menu_item DROP COLUMN vat_type_id;
ALTER TABLE menu_item DROP COLUMN combined;
ALTER TABLE menu_item ADD COLUMN product_id UUID REFERENCES product(id) ON DELETE CASCADE;
CREATE INDEX idx_menu_item_product_id ON menu_item(product_id);

-- A product's recipe: fractional quantities of other products
-- (e.g. hugo = 0.0714 of a prosecco bottle + 0.01 of a syrup bottle).
CREATE TABLE recipe_component (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id           UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    component_product_id UUID NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    quantity             NUMERIC(12, 4) NOT NULL,
    sort_order           INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_recipe_component_product ON recipe_component(product_id);
