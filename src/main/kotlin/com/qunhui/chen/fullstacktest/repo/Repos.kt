package com.qunhui.chen.fullstacktest.repo

/**
 * @author Qunhui Chen
 * @date 2025/9/24 00:08
 */

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime

// --- 参数对象 ---
data class ProductUpsertCmd(
    val productId: Long,
    val title: String,
    val vendor: String?,
    val productType: String?,
    val tags: List<String>?,           // 会转成 PG 的 text[] 字面量
    val optionsJson: String?,          // JSON 字符串，SQL 里 CAST(:options AS JSONB)
    val createdAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime?
)

data class VariantUpsertCmd(
    val variantId: Long,
    val productId: Long,               // 外部 product.id
    val title: String?,
    val sku: String?,
    val imageUrl: String?,
    val price: BigDecimal?,
    val comparePrice: BigDecimal?,
    val available: Boolean?,
    val position: Int?,
    val option1: String?, val option2: String?, val option3: String?,
    val createdAt: OffsetDateTime?, val updatedAt: OffsetDateTime?
)

@Repository
class ProductRepo(private val jdbc: JdbcClient) {

    fun upsert(cmd: ProductUpsertCmd): Int =
        jdbc.sql(UPSERT_PRODUCT_SQL)
            .param("pid", cmd.productId)
            .param("title", cmd.title)
            .param("vendor", cmd.vendor)
            .param("ptype", cmd.productType)
            .param("tags", toPgTextArrayLiteral(cmd.tags)) // -> CAST(:tags AS TEXT[])
            .param("options", cmd.optionsJson)            // -> CAST(:options AS JSONB)
            .param("created", cmd.createdAt)
            .param("updated", cmd.updatedAt)
            .update()

    /**
     * 删除不在 keepIds 里的 products（带“最小集保护”，防止意外清空）。
     * keepIds 很小时直接跳过删除。
     */
    fun deleteNotIn(keepIds: List<Long>, minGuard: Int = 10): Int {
        if (keepIds.isEmpty() || keepIds.size < minGuard) return 0
        return jdbc.sql(DELETE_PRODUCTS_NOT_IN_SQL)
            .param("ids", keepIds)
            .update()
    }

    // 将 List<String> 转成 PG 的 text[] 字面量：{"a","b"}；空/Null 返回 null
    private fun toPgTextArrayLiteral(values: List<String>?): String? {
        if (values == null) return null
        if (values.isEmpty()) return "{}"
        val escaped = values.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
        return "{$escaped}"
    }
}

@Repository
class VariantRepo(private val jdbc: JdbcClient) {

    fun upsert(cmd: VariantUpsertCmd): Int =
        jdbc.sql(UPSERT_VARIANT_SQL)
            .param("vid", cmd.variantId)
            .param("pid", cmd.productId)
            .param("title", cmd.title)
            .param("sku", cmd.sku)
            .param("img", cmd.imageUrl)
            .param("price", cmd.price)
            .param("cprice", cmd.comparePrice)
            .param("avail", cmd.available)
            .param("pos", cmd.position)
            .param("o1", cmd.option1).param("o2", cmd.option2).param("o3", cmd.option3)
            .param("created", cmd.createdAt).param("updated", cmd.updatedAt)
            .update()

    fun deleteVariantsNotInProducts(keepProductIds: List<Long>, minGuard: Int = 10): Int {
        if (keepProductIds.isEmpty() || keepProductIds.size < minGuard) return 0
        return jdbc.sql(DELETE_VARIANTS_NOT_IN_PRODUCTS_SQL)
            .param("ids", keepProductIds)
            .update()
    }
}
