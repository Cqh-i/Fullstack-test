package com.qunhui.chen.fullstacktest.repo

import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * @author Qunhui Chen
 * @date 2025/9/24 01:02
 */
data class ProductListRow(
    val productId: Long,
    val title: String,
    val vendor: String?,
    val minPrice: BigDecimal?,
    val updatedAt: OffsetDateTime?,
    val imageUrl: String?,
    val productType: String?,
    val tags: List<String>?,
)