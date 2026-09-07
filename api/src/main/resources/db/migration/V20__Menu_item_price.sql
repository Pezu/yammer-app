-- Price is per MENU ENTRY, not per product — the same catalog product can cost
-- differently on different menus. The catalog keeps name/description/VAT/image.
ALTER TABLE menu_item ADD COLUMN price NUMERIC(10, 2);

-- Carry over the price a leaf inherited from its product so menus keep working.
UPDATE menu_item mi SET price = p.price FROM product p WHERE mi.product_id = p.id;

ALTER TABLE product DROP COLUMN price;
