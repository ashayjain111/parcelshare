INSERT INTO region (name) VALUES ('Delhi NCR'), ('Lucknow'), ('Kanpur'), ('Jaipur'), ('Chandigarh'), ('Dehradun'), ('Agra'), ('Varanasi');

INSERT INTO city (name, state, region_id) VALUES
 ('Delhi', 'Delhi', (SELECT id FROM region WHERE name = 'Delhi NCR')),
 ('Noida', 'Uttar Pradesh', (SELECT id FROM region WHERE name = 'Delhi NCR')),
 ('Gurugram', 'Haryana', (SELECT id FROM region WHERE name = 'Delhi NCR')),
 ('Ghaziabad', 'Uttar Pradesh', (SELECT id FROM region WHERE name = 'Delhi NCR')),
 ('Lucknow', 'Uttar Pradesh', (SELECT id FROM region WHERE name = 'Lucknow')),
 ('Kanpur', 'Uttar Pradesh', (SELECT id FROM region WHERE name = 'Kanpur')),
 ('Jaipur', 'Rajasthan', (SELECT id FROM region WHERE name = 'Jaipur')),
 ('Chandigarh', 'Chandigarh', (SELECT id FROM region WHERE name = 'Chandigarh')),
 ('Dehradun', 'Uttarakhand', (SELECT id FROM region WHERE name = 'Dehradun')),
 ('Agra', 'Uttar Pradesh', (SELECT id FROM region WHERE name = 'Agra')),
 ('Varanasi', 'Uttar Pradesh', (SELECT id FROM region WHERE name = 'Varanasi'));

INSERT INTO parcel_category (name, tier, max_grams, max_value_paise, active) VALUES
 ('Documents', 'ALLOWED', 2000, 300000, TRUE),
 ('Clothes', 'ALLOWED', 5000, 300000, TRUE),
 ('Books & stationery', 'ALLOWED', 5000, 200000, TRUE),
 ('Chargers & accessories', 'ALLOWED', 3000, 300000, TRUE),
 ('Small electronics', 'RESTRICTED', 3000, 300000, TRUE),
 ('Packaged food (sealed, non-perishable)', 'RESTRICTED', 3000, 100000, TRUE),
 ('Cash / jewellery / valuables', 'PROHIBITED', 0, 0, TRUE),
 ('Medicines', 'PROHIBITED', 0, 0, TRUE),
 ('Liquids, aerosols, loose batteries', 'PROHIBITED', 0, 0, TRUE),
 ('Alcohol / tobacco / drugs', 'PROHIBITED', 0, 0, TRUE),
 ('Weapons / hazardous items', 'PROHIBITED', 0, 0, TRUE);

INSERT INTO blocked_keyword (word) VALUES
 ('cash'), ('gold'), ('jewellery'), ('jewelry'), ('drug'), ('weed'), ('ganja'), ('charas'),
 ('alcohol'), ('liquor'), ('whisky'), ('weapon'), ('gun'), ('knife'), ('explosive'), ('passport'), ('medicine');
