-- Rendezvous drinks menu -> catalog products (Backoffice -> Menu -> Products).
-- Source: the two menu pages (Sept 2026). Prices are not on products (they go on menu entries).
--
-- Location: 4f884711-7c5e-41a7-9aec-338208ef3dba (a wrong id fails on the foreign key).
-- Safe to re-run: a product is skipped when one with the same name already exists there.
--
-- VAT: 21% on alcohol, energy and sugared soft drinks; 11% on water. Adjust before running if
-- your accountant says otherwise (Catalog -> VAT holds 21 / 11 / 0).

BEGIN;

-- ---------------------------------------------------------------- products
INSERT INTO yammer.product (location_id, name, description, vat_type_id)
SELECT '4f884711-7c5e-41a7-9aec-338208ef3dba'::uuid, v.name, v.description,
       (SELECT id FROM yammer.vat_type WHERE value = v.vat)
FROM (VALUES
    -- long drinks 250 ml
    ('Beefeater & Tonic',                '250 ml',                                                     21),
    ('Havana Cuban Spiced Cuba Libre',   '250 ml',                                                     21),
    ('Ramazzotti Amaro Tonic',           '250 ml',                                                     21),
    ('Ramazzotti Amaro Cranberry',       '250 ml',                                                     21),
    ('Paloma',                           '250 ml',                                                     21),
    ('Jameson & Cola',                   '250 ml',                                                     21),
    ('Vodka Tonic',                      '250 ml',                                                     21),
    ('Vodka Cranberry',                  '250 ml',                                                     21),
    -- non alcoholic
    ('Gin & Tonic "0"',                  'Beefeater "0", pink grapefruit soda, grapefruit',            21),
    ('Arancia Spritz',                   'Ramazzotti Arancia "0", Luc Belaire Rose "0", apa tonica',   21),
    -- spring bubbles
    ('Aperol Spritz',                    NULL,                                                         21),
    ('Hugo',                             NULL,                                                         21),
    ('A Glass of Prosecco',              NULL,                                                         21),
    -- on the rocks 40 ml
    ('Jameson',                          '40 ml',                                                      21),
    ('Absolut',                          '40 ml',                                                      21),
    ('Havana Cuban Spiced',              '40 ml',                                                      21),
    -- shots 25 ml
    ('Ramazzotti Amaro',                 '25 ml',                                                      21),
    ('Olmeca Altos Blanco',              '25 ml',                                                      21),
    ('Skrewball Peanut Butter',          '25 ml',                                                      21),
    -- soft drinks (clarifier in the description -> quantity in the name)
    ('S. Pellegrino 0.5 L',              'Apa minerala',                                               11),
    ('Acqua Panna 0.5 L',                'Apa plata',                                                  11),
    ('Coca Cola',                        '0.33 L',                                                     21),
    ('S. Pellegrino Tonica 0.33 L',      'Apa tonica',                                                 21),
    ('Three Cents Tonic',                '0.2 L',                                                      21),
    ('Three Cents Pink Grapefruit Soda', '0.2 L',                                                      21),
    ('Ocean Spray Cranberry',            '1 L',                                                        21),
    -- energy drinks 250 ml
    ('Red Bull Classic',                 '250 ml',                                                     21),
    ('Red Bull Sugar-Free',              '250 ml',                                                     21),
    -- premium bottle service 0.7 L
    ('Belvedere B10',                    '0.7 L',                                                      21),
    ('Belvedere',                        '0.7 L',                                                      21),
    ('Malfy Originale',                  '0.7 L',                                                      21),
    ('Malfy Rosa',                       '0.7 L',                                                      21),
    ('Bumbu Original',                   '0.7 L',                                                      21),
    ('Glenlivet 12YO',                   '0.7 L',                                                      21),
    ('Don Julio 1942',                   '0.7 L',                                                      21),
    ('Avion Reserva 44',                 '0.7 L',                                                      21),
    ('Avion Silver',                     '0.7 L',                                                      21),
    ('Avion Reposado',                   '0.7 L',                                                      21),
    -- champagne 0.75 L
    ('Moet & Chandon Imperial Brut',     '0.75 L',                                                     21),
    ('Veuve Clicquot Brut',              '0.75 L',                                                     21),
    -- sparkling (appellation in the description -> quantity in the name)
    ('Franciacorta Methuselah 6 L',      'DOCG Nintens la Merchesine',                                 21),
    ('Franciacorta Magnum 1.5 L',        'DOCG Nintens la Merchesine',                                 21),
    ('Franciacorta 0.75 L',              'DOCG Nintens la Merchesine',                                 21),
    ('Prosecco Le Monde 0.75 L',         'Extra dry',                                                  21),
    -- wine 0.75 L (producer/appellation in the description -> quantity in the name)
    ('Pinot Bianco 0.75 L',              'DOC Le Monde',                                               21),
    ('Aurato Pecorino 0.75 L',           'DOP Il Conte',                                               21),
    ('Sauvignon Blanc 0.75 L',           'Le Monde',                                                   21),
    ('Rose & Rose Marche Rosato 0.75 L', 'IGP Il Conte Villa Prandone',                                21)
) AS v(name, description, vat)
WHERE NOT EXISTS (SELECT 1 FROM yammer.product p
                  WHERE p.location_id = '4f884711-7c5e-41a7-9aec-338208ef3dba' AND p.name = v.name);

COMMIT;
