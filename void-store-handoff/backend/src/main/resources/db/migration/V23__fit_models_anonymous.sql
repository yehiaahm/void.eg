-- The models' names are not shown to shoppers (owner, 2026-11-14): the product page lists them as
-- "Model 1 / 2 / 3" with height and size worn. The names are therefore removed from the data instead
-- of being kept (and sent by the public API) without being used.

ALTER TABLE product_fit_models
  DROP COLUMN name_en,
  DROP COLUMN name_ar;
