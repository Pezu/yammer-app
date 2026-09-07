-- =============================================
-- Menu  (a location can have several menus, each a tree of items)
-- Unlike the old project there is no event level — menus are per location.
-- =============================================
CREATE TABLE menu (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_menu_location_id ON menu(location_id);

-- A menu is a tree of items: categories (orderable=false) and products
-- (orderable=true + price). name is TEXT (stores rich-text/HTML).
CREATE TABLE menu_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_id      UUID NOT NULL REFERENCES menu(id) ON DELETE CASCADE,
    parent_id    UUID REFERENCES menu_item(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    orderable    BOOLEAN NOT NULL DEFAULT false,
    price        NUMERIC(10, 2),
    vat_type_id  UUID REFERENCES vat_type(id),
    image_object VARCHAR(255),
    combined     BOOLEAN NOT NULL DEFAULT false,
    sort_order   INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_menu_item_menu_id ON menu_item(menu_id);
CREATE INDEX idx_menu_item_parent_id ON menu_item(parent_id);
