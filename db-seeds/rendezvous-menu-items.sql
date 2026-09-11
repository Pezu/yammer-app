-- Menu items for the two Rendezvous menus, from the two menu pages (Sept 2026).
-- Requires the products from rendezvous-products.sql (location 4f884711-7c5e-41a7-9aec-338208ef3dba).
--
--   Bar menu    8e227542-4ec0-48e0-a67c-b70b9e670c13  (page 1)
--   Bottle menu dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c  (page 2)
--
-- Each menu gets its categories (orderable = false) and, under them, one priced line per
-- product (orderable = true, product_id set). Safe to re-run: existing categories and lines
-- are skipped. The script stops if a line names a product that does not exist.

BEGIN;

-- The lines are used three times below (guard, categories, items), hence the temp table.
CREATE TEMP TABLE _lines (menu_id UUID, category TEXT, cat_order INT, product TEXT, price NUMERIC, line_order INT) ON COMMIT DROP;
INSERT INTO _lines VALUES
    -- ---------------------------------------------------------------- Bar menu
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Long Drinks',   1, 'Beefeater & Tonic',                42, 1),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Long Drinks',   1, 'Havana Cuban Spiced Cuba Libre',   42, 2),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Long Drinks',   1, 'Ramazzotti Amaro Tonic',           42, 3),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Long Drinks',   1, 'Ramazzotti Amaro Cranberry',       42, 4),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Long Drinks',   1, 'Paloma',                           42, 5),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Long Drinks',   1, 'Jameson & Cola',                   42, 6),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Long Drinks',   1, 'Vodka Tonic',                      42, 7),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Long Drinks',   1, 'Vodka Cranberry',                  42, 8),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Non Alcoholic', 2, 'Gin & Tonic "0"',                  42, 1),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Non Alcoholic', 2, 'Arancia Spritz',                   42, 2),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Spring Bubbles', 3, 'Aperol Spritz',                   49, 1),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Spring Bubbles', 3, 'Hugo',                            49, 2),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Spring Bubbles', 3, 'A Glass of Prosecco',             42, 3),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'On the Rocks',  4, 'Jameson',                          32, 1),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'On the Rocks',  4, 'Absolut',                          32, 2),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'On the Rocks',  4, 'Havana Cuban Spiced',              32, 3),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Shots',         5, 'Ramazzotti Amaro',                 25, 1),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Shots',         5, 'Olmeca Altos Blanco',              25, 2),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Shots',         5, 'Skrewball Peanut Butter',          25, 3),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Soft Drinks',   6, 'S. Pellegrino 0.5 L',              22, 1),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Soft Drinks',   6, 'Acqua Panna 0.5 L',                22, 2),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Soft Drinks',   6, 'Coca Cola',                        22, 3),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Soft Drinks',   6, 'S. Pellegrino Tonica 0.33 L',      22, 4),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Soft Drinks',   6, 'Three Cents Tonic',                22, 5),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Soft Drinks',   6, 'Three Cents Pink Grapefruit Soda', 22, 6),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Energy Drinks', 7, 'Red Bull Classic',                 27, 1),
    ('8e227542-4ec0-48e0-a67c-b70b9e670c13', 'Energy Drinks', 7, 'Red Bull Sugar-Free',              27, 2),
    -- ---------------------------------------------------------------- Bottle menu
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Vodka',         1, 'Belvedere B10',                  1500, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Vodka',         1, 'Belvedere',                       720, 2),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Gin',           2, 'Malfy Originale',                 720, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Gin',           2, 'Malfy Rosa',                      720, 2),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Rum',           3, 'Bumbu Original',                  720, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Whiskey',       4, 'Glenlivet 12YO',                  790, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Tequila',       5, 'Don Julio 1942',                 2900, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Tequila',       5, 'Avion Reserva 44',               1800, 2),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Tequila',       5, 'Avion Silver',                    720, 3),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Tequila',       5, 'Avion Reposado',                  720, 4),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Champagne',     6, 'Moet & Chandon Imperial Brut',    690, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Champagne',     6, 'Veuve Clicquot Brut',             890, 2),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Sparkling',     7, 'Franciacorta Methuselah 6 L',    4900, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Sparkling',     7, 'Franciacorta Magnum 1.5 L',      1190, 2),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Sparkling',     7, 'Franciacorta 0.75 L',             590, 3),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Sparkling',     7, 'Prosecco Le Monde 0.75 L',        390, 4),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Shots',         8, 'Ramazzotti Amaro',                 25, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Shots',         8, 'Olmeca Altos Blanco',              25, 2),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Shots',         8, 'Skrewball Peanut Butter',          25, 3),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'White Wine',    9, 'Pinot Bianco 0.75 L',             390, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'White Wine',    9, 'Aurato Pecorino 0.75 L',          350, 2),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'White Wine',    9, 'Sauvignon Blanc 0.75 L',          350, 3),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Rose Wine',    10, 'Rose & Rose Marche Rosato 0.75 L', 390, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Soft Drinks',  11, 'S. Pellegrino 0.5 L',              22, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Soft Drinks',  11, 'Acqua Panna 0.5 L',                22, 2),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Soft Drinks',  11, 'Coca Cola',                        22, 3),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Soft Drinks',  11, 'Three Cents Tonic',                28, 4),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Soft Drinks',  11, 'Three Cents Pink Grapefruit Soda', 28, 5),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Soft Drinks',  11, 'Ocean Spray Cranberry',           100, 6),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Energy Drinks', 12, 'Red Bull Classic',                27, 1),
    ('dd89f77c-2f10-4c1f-9f8f-9bc41d71cb6c', 'Energy Drinks', 12, 'Red Bull Sugar-Free',             27, 2);

-- Every line must name an existing product of the menu's location (typo guard).
DO $$
DECLARE missing TEXT;
BEGIN
    SELECT string_agg(DISTINCT x.product, ', ') INTO missing
    FROM _lines x
    JOIN yammer.menu m ON m.id = x.menu_id
    WHERE NOT EXISTS (SELECT 1 FROM yammer.product p WHERE p.location_id = m.location_id AND p.name = x.product);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Unknown products: %', missing;
    END IF;
END $$;

-- Categories (top level of each menu).
INSERT INTO yammer.menu_item (menu_id, parent_id, name, orderable, sort_order)
SELECT c.menu_id, NULL, c.category, false, c.cat_order
FROM (SELECT DISTINCT menu_id, category, cat_order FROM _lines) c
WHERE NOT EXISTS (SELECT 1 FROM yammer.menu_item e
                  WHERE e.menu_id = c.menu_id AND e.parent_id IS NULL AND e.name = c.category);

-- Priced lines under their category.
INSERT INTO yammer.menu_item (menu_id, parent_id, name, orderable, product_id, price, sort_order)
SELECT x.menu_id, cat.id, p.name, true, p.id, x.price, x.line_order
FROM _lines x
JOIN yammer.menu m ON m.id = x.menu_id
JOIN yammer.menu_item cat ON cat.menu_id = x.menu_id AND cat.parent_id IS NULL AND cat.name = x.category
JOIN yammer.product p ON p.location_id = m.location_id AND p.name = x.product
WHERE NOT EXISTS (SELECT 1 FROM yammer.menu_item e WHERE e.parent_id = cat.id AND e.product_id = p.id);

COMMIT;

-- Check:
-- SELECT m.name AS menu, c.name AS category, i.name, i.price
-- FROM yammer.menu_item i JOIN yammer.menu_item c ON c.id = i.parent_id JOIN yammer.menu m ON m.id = i.menu_id
-- ORDER BY m.name, c.sort_order, i.sort_order;
