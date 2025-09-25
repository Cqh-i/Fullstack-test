package com.qunhui.chen.fullstacktest.adapter.jobs.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.OffsetDateTime
import java.math.BigDecimal

/**
 * @author Qunhui Chen
 * @date 2025/9/23 23:12
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RemoteProduct(
    val id: Long,                                // → products.product_id
    val title: String,
    val vendor: String? = null,
    val product_type: String? = null,
    val tags: List<String> = emptyList(),        // → products.tags (TEXT[])
    val options: List<RemoteOption> = emptyList(), // → products.options_json (JSONB)
    val variants: List<RemoteVariant> = emptyList(),
    val created_at: OffsetDateTime? = null,      // → products.created_at
    val updated_at: OffsetDateTime? = null       // → products.updated_at
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RemoteVariant(
    val id: Long,                                // → variants.variant_id
    val product_id: Long,                        // → variants.product_id（外部ID）
    val title: String? = null,
    val sku: String? = null,
    val price: BigDecimal? = null,               // → variants.price
    val compare_at_price: BigDecimal? = null,    // → variants.compare_price
    val available: Boolean? = null,
    val position: Int? = null,
    val option1: String? = null,                 // → variants.option1/2/3
    val option2: String? = null,
    val option3: String? = null,
    val created_at: OffsetDateTime? = null,      // variants.created_at
    val updated_at: OffsetDateTime? = null,      // → variants.updated_at
    val featured_image: RemoteImage? = null      // 用来提取 image_url
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RemoteOption(
    val name: String? = null,
    val position: Int? = null,
    val values: List<String> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RemoteImage(
    val id: Long? = null,
    val src: String? = null,
    val position: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProductList(val products: List<RemoteProduct> = emptyList())