-- Bar menu (8e227542-4ec0-48e0-a67c-b70b9e670c13): category TIGARETE with the glo HILO products (see glo-products.sql).
-- Safe to re-run: the category and existing lines are skipped; stops if a product is missing.

BEGIN;

CREATE TEMP TABLE _lines (product TEXT, price NUMERIC, line_order INT) ON COMMIT DROP;
INSERT INTO _lines VALUES
    ('glo HILO Plus Onyx',                            249,  1),
    ('glo HILO Plus Ruby',                            249,  2),
    ('glo HILO Plus Amethyst',                        249,  3),
    ('glo HILO Plus Coral',                           249,  4),
    ('glo HILO Onyx',                                 119,  5),
    ('glo HILO Ruby',                                 119,  6),
    ('glo HILO Coral',                                119,  7),
    ('glo Hyper Pro + Graphite',                       69,  8),
    ('Virto Classic Tobacco',                          25,  9),
    ('Virto Copper Tobacco',                           25, 10),
    ('Virto Balanced Tobacco',                         25, 11),
    ('Virto Azure Tobacco',                            25, 12),
    ('Virto Signature Tobacco',                        25, 13),
    ('Virto Silver Tobacco',                           25, 14),
    ('Rivo Icy Click',                                 25, 15),
    ('Rivo Scarlet Click',                             25, 16),
    ('Rivo Purple Click',                              25, 17),
    ('Rivo Sunset Click',                              25, 18),
    ('Rivo Fresco Click',                              25, 19),
    ('Veo Blossom Twist',                              21, 20),
    ('Veo Amber Click',                                21, 21),
    ('Veo Ice Click',                                  21, 22),
    ('Veo Scarlet Click',                              21, 23),
    ('Dunhill Evoque Japanese Rose',                 32.5, 24),
    ('Dunhill Evoque English Green',                 32.5, 25),
    ('Dunhill Fine Cut Blonde Blend',                32.5, 26),
    ('Dunhill Fine Cut Swiss Blend',                 32.5, 27),
    ('Dunhill Fine Cut Master Blend',                32.5, 28),
    ('Dunhill Fine Cut Bright Blend',                32.5, 29),
    ('Vogue Bleue',                                  32.5, 30),
    ('Vogue Lilas',                                  32.5, 31),
    ('glo by Dunhill Copper',                          21, 32),
    ('glo by Dunhill Obsidian',                        21, 33),
    ('VUSE Peppermint Ice',                            40, 34),
    ('VUSE Strawberry',                                40, 35),
    ('VUSE Grape',                                     40, 36),
    ('VUSE Standalone Device',                         30, 37),
    ('VELO Bright Spearmint 4mg',                      25, 38),
    ('VELO Cherry Ice 6mg',                            25, 39),
    ('VELO Smooth Peppermint 6mg',                     25, 40),
    ('VUSE Pods Cherry Ice',                           19, 41),
    ('VUSE Pods Spearmint Ice',                        19, 42),
    ('VUSE Pods Berry Watermelon',                     19, 43);

-- every line must name an existing product of the menu's location (typo guard)
DO $$
DECLARE missing TEXT;
BEGIN
    SELECT string_agg(x.product, ', ') INTO missing
    FROM _lines x
    WHERE NOT EXISTS (
        SELECT 1 FROM yammer.product p
        JOIN yammer.menu m ON m.location_id = p.location_id
        WHERE m.id = '8e227542-4ec0-48e0-a67c-b70b9e670c13' AND p.name = x.product);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Unknown products: %', missing;
    END IF;
END $$;

-- the category (top level, after the existing ones)
INSERT INTO yammer.menu_item (menu_id, parent_id, name, orderable, sort_order)
SELECT '8e227542-4ec0-48e0-a67c-b70b9e670c13', NULL, 'TIGARETE', false,
       COALESCE((SELECT max(sort_order) FROM yammer.menu_item WHERE menu_id = '8e227542-4ec0-48e0-a67c-b70b9e670c13' AND parent_id IS NULL), 0) + 1
WHERE NOT EXISTS (SELECT 1 FROM yammer.menu_item
                  WHERE menu_id = '8e227542-4ec0-48e0-a67c-b70b9e670c13' AND parent_id IS NULL AND name = 'TIGARETE');

-- the priced lines under it
INSERT INTO yammer.menu_item (menu_id, parent_id, name, orderable, product_id, price, sort_order)
SELECT m.id, cat.id, p.name, true, p.id, x.price, x.line_order
FROM _lines x
JOIN yammer.menu m ON m.id = '8e227542-4ec0-48e0-a67c-b70b9e670c13'
JOIN yammer.menu_item cat ON cat.menu_id = m.id AND cat.parent_id IS NULL AND cat.name = 'TIGARETE'
JOIN yammer.product p ON p.location_id = m.location_id AND p.name = x.product
WHERE NOT EXISTS (SELECT 1 FROM yammer.menu_item e WHERE e.parent_id = cat.id AND e.product_id = p.id);

COMMIT;
