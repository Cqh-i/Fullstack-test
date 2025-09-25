package com.qunhui.chen.fullstacktest.repo

import java.math.BigDecimal

/**
 * @author Qunhui Chen
 * @date 2025/9/24 17:42
 */
data class VariantRow(
    val variantId: Long,
    val option1: String?,
    val option2: String?,
    val option3: String?,
    val sku: String?,
    val price: BigDecimal?,
    val comparePrice: BigDecimal?,
    val available: Boolean?,   // 允许为 null；模板里 null/false 都按 “No” 显示
    val imageUrl: String?
    // 如需在列表里用到再加：
    // val title: String? = null,
    // val position: Int? = null
)