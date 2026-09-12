-- glo HILO tobacco & vape menu -> catalog products (Backoffice -> Menu -> Products).
-- Source: the glo HILO menu page (Sept 2026). Prices are on menu entries, not products;
-- the price of each group is noted in the section comment for when the menu is built.
--
-- Location: 4f884711-7c5e-41a7-9aec-338208ef3dba (a wrong id fails on the foreign key).
-- Safe to re-run: a product is skipped when one with the same name already exists there.
--
-- VAT: 21% (tobacco and vaping products carry the standard rate).

BEGIN;

INSERT INTO yammer.product (location_id, name, description, vat_type_id)
SELECT '4f884711-7c5e-41a7-9aec-338208ef3dba'::uuid, v.name, v.description,
       (SELECT id FROM yammer.vat_type WHERE value = v.vat)
FROM (VALUES
    -- glo HILO Plus (249 ron)
    ('glo HILO Plus Onyx',                   NULL,       21),
    ('glo HILO Plus Ruby',                   NULL,       21),
    ('glo HILO Plus Amethyst',               NULL,       21),
    ('glo HILO Plus Coral',                  NULL,       21),
    -- glo HILO (119 ron)
    ('glo HILO Onyx',                        NULL,       21),
    ('glo HILO Ruby',                        NULL,       21),
    ('glo HILO Coral',                       NULL,       21),
    -- glo Hyper Pro + (69 ron)
    ('glo Hyper Pro + Graphite',             NULL,       21),
    -- Virto (25 ron)
    ('Virto Classic Tobacco',                NULL,       21),
    ('Virto Copper Tobacco',                 NULL,       21),
    ('Virto Balanced Tobacco',               NULL,       21),
    ('Virto Azure Tobacco',                  NULL,       21),
    ('Virto Signature Tobacco',              NULL,       21),
    ('Virto Silver Tobacco',                 NULL,       21),
    -- Rivo (25 ron)
    ('Rivo Icy Click',                       NULL,       21),
    ('Rivo Scarlet Click',                   NULL,       21),
    ('Rivo Purple Click',                    NULL,       21),
    ('Rivo Sunset Click',                    NULL,       21),
    ('Rivo Fresco Click',                    NULL,       21),
    -- Veo (21 ron)
    ('Veo Blossom Twist',                    NULL,       21),
    ('Veo Amber Click',                      NULL,       21),
    ('Veo Ice Click',                        NULL,       21),
    ('Veo Scarlet Click',                    NULL,       21),
    -- Dunhill (32.5 ron)
    ('Dunhill Evoque Japanese Rose',         NULL,       21),
    ('Dunhill Evoque English Green',         NULL,       21),
    ('Dunhill Fine Cut Blonde Blend',        NULL,       21),
    ('Dunhill Fine Cut Swiss Blend',         NULL,       21),
    ('Dunhill Fine Cut Master Blend',        NULL,       21),
    ('Dunhill Fine Cut Bright Blend',        NULL,       21),
    -- Vogue (32.5 ron)
    ('Vogue Bleue',                          NULL,       21),
    ('Vogue Lilas',                          NULL,       21),
    -- glo by Dunhill (21 ron)
    ('glo by Dunhill Copper',                NULL,       21),
    ('glo by Dunhill Obsidian',              NULL,       21),
    -- VUSE (40 ron; Standalone Device 30 ron)
    ('VUSE Peppermint Ice',                  NULL,       21),
    ('VUSE Strawberry',                      NULL,       21),
    ('VUSE Grape',                           NULL,       21),
    ('VUSE Standalone Device',               NULL,       21),
    -- VELO (25 ron)
    ('VELO Bright Spearmint 4mg',            NULL,       21),
    ('VELO Cherry Ice 6mg',                  NULL,       21),
    ('VELO Smooth Peppermint 6mg',           NULL,       21),
    -- VUSE Pods (19 ron)
    ('VUSE Pods Cherry Ice',                 NULL,       21),
    ('VUSE Pods Spearmint Ice',              NULL,       21),
    ('VUSE Pods Berry Watermelon',           NULL,       21)
) AS v(name, description, vat)
WHERE NOT EXISTS (SELECT 1 FROM yammer.product p
                  WHERE p.location_id = '4f884711-7c5e-41a7-9aec-338208ef3dba' AND p.name = v.name);

COMMIT;
