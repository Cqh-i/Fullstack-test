package com.qunhui.chen.fullstacktest.repo

/**
 * @author Qunhui Chen
 * @date 2025/9/24 00:07
 */

internal const val UPSERT_PRODUCT_SQL = """
INSERT INTO products(product_id, title, vendor, product_type, tags, options_json, created_at, updated_at)
VALUES (:pid, :title, :vendor, :ptype, :tags, CAST(:options AS JSONB), :created, :updated)
ON CONFLICT (product_id) DO UPDATE SET
  title        = EXCLUDED.title,
  vendor       = COALESCE(EXCLUDED.vendor, products.vendor),
  product_type = COALESCE(EXCLUDED.product_type, products.product_type),
  tags         = COALESCE(EXCLUDED.tags, products.tags),
  options_json = COALESCE(EXCLUDED.options_json, products.options_json),
  created_at   = COALESCE(EXCLUDED.created_at, products.created_at),
  updated_at   = EXCLUDED.updated_at
WHERE
  products.updated_at IS DISTINCT FROM EXCLUDED.updated_at
  OR products.title   IS DISTINCT FROM EXCLUDED.title
"""

internal const val UPSERT_VARIANT_SQL = """
INSERT INTO variants(
  variant_id, product_id, title, sku, image_url, price, compare_price, available, position,
  option1, option2, option3, created_at, updated_at
) VALUES (
  :vid, :pid, :title, :sku, :img, :price, :cprice, :avail, :pos,
  :o1, :o2, :o3, :created, :updated
)
ON CONFLICT (variant_id) DO UPDATE SET
  title         = EXCLUDED.title,
  sku           = EXCLUDED.sku,
  image_url     = EXCLUDED.image_url,
  price         = EXCLUDED.price,
  compare_price = EXCLUDED.compare_price,
  available     = EXCLUDED.available,
  position      = EXCLUDED.position,
  option1       = EXCLUDED.option1,
  option2       = EXCLUDED.option2,
  option3       = EXCLUDED.option3,
  updated_at    = EXCLUDED.updated_at
WHERE
  variants.updated_at IS DISTINCT FROM EXCLUDED.updated_at
  OR variants.price   IS DISTINCT FROM EXCLUDED.price
"""

internal const val DELETE_VARIANTS_NOT_IN_PRODUCTS_SQL = """
DELETE FROM variants
WHERE product_id NOT IN (:ids)
"""

internal const val DELETE_PRODUCTS_NOT_IN_SQL = """
DELETE FROM products
WHERE product_id NOT IN (:ids)
"""
