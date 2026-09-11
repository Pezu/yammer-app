-- Optional title printed on every card (same size and colour as the table name), e.g. the
-- venue name above the QR, mirroring the table name below it.
ALTER TABLE qr_template ADD COLUMN title VARCHAR(100);
ALTER TABLE qr_template ADD COLUMN title_y NUMERIC(6,4) NOT NULL DEFAULT 0.1;

UPDATE qr_template SET title = 'Rendezvous', title_y = 0.1386 WHERE name = 'Rendezvous';
