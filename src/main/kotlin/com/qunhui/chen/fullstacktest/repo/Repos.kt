package com.qunhui.chen.fullstacktest.repo


import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * @author Qunhui Chen
 * @date 2025/9/24 00:08
 */
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
    val sku: String?,
    val imageUrl: String?,
    val price: BigDecimal?,
    val comparePrice: BigDecimal?,
    val available: Boolean?,
    val position: Int?,
    val option1: String?, val option2: String?, val option3: String?,
    val createdAt: OffsetDateTime?, val updatedAt: OffsetDateTime?
)

data class OptionDef(val name: String?, val position: Int?)

@Repository
class ProductRepo(
    private val jdbc: JdbcClient,
    private val mapper: ObjectMapper
) {

    fun loadOptionNames(productId: Long): List<String> {
        // 直接按类型查询成 String，可用 singleOrNull() / optional()
        val json: String? = jdbc.sql(
            "SELECT options_json FROM products WHERE product_id=:pid"
        )
            .param("pid", productId)
            .query(String::class.java)
            .optional().orElse(null)

        if (json.isNullOrBlank()) return emptyList()

        // 解析 -> 排序 -> 取前3个列名
        val defs: List<OptionDef> = try {
            mapper.readValue(json)   // 见下一节：需要扩展函数或 TypeReference
        } catch (_: Exception) {
            return emptyList()
        }

        return defs
            .sortedBy { it.position ?: Int.MAX_VALUE }
            .mapNotNull { it.name }
            .take(3)
    }

    fun existsByProductId(productId: Long): Boolean {
        return jdbc.sql("""
            select exists(
              select 1 from products
              where product_id = :productId
            )
        """.trimIndent())
            .param("productId", productId)
            .query(Boolean::class.java)
            .single() ?: false
    }

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

    /** 删除不在 keepIds 里的 products（带“最小集保护”，防止意外清空）。 */
    fun deleteNotIn(keepIds: List<Long>, minGuard: Int = 10): Int {
        if (keepIds.isEmpty() || keepIds.size < minGuard) return 0
        return jdbc.sql(DELETE_PRODUCTS_NOT_IN_SQL)
            .param("ids", keepIds)
            .update()
    }

    fun listForViewPaged(limit: Int, offset: Int, search: String?): List<ProductListRow> =
        jdbc.sql(SELECT_PRODUCTS_FOR_VIEW_PAGED_SQL)
            .param("limit", limit)
            .param("offset", offset)
            .param("search_pattern", if (search.isNullOrBlank()) "" else "%$search%")
            .query { rs, _ ->

                val tags: List<String>? = rs.getArray("tags")?.let { arr ->
                    @Suppress("UNCHECKED_CAST")
                    (arr.array as Array<String>).toList()
                }

                ProductListRow(
                    productId = rs.getLong("product_id"),
                    title = rs.getString("title"),
                    vendor = rs.getString("vendor"),
                    minPrice = rs.getBigDecimal("min_price"),
                    updatedAt = rs.getObject("updated_at", java.time.OffsetDateTime::class.java),
                    imageUrl = rs.getString("image_url"),
                    productType = rs.getString("product_type"),
                    tags = tags

                )
            }.list()

    fun countForView(search: String?): Long =
        jdbc.sql(COUNT_PRODUCTS_FOR_VIEW_SQL)
            .param("search_pattern", if (search.isNullOrBlank()) "" else "%$search%")
            .query { rs, _ -> rs.getLong(1) }
            .single()

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

    fun listByProductId(productId: Long): List<VariantRow> =
        jdbc.sql(
            """
            SELECT variant_id, option1, option2, option3, sku, price, compare_price, available, image_url
            FROM variants
            WHERE product_id = :pid
            ORDER BY available DESC NULLS LAST, position NULLS LAST, variant_id
            """.trimIndent()
        )
            .param("pid", productId)
            .query { rs, _ ->
                VariantRow(
                    variantId = rs.getLong("variant_id"),
                    option1 = rs.getString("option1"),
                    option2 = rs.getString("option2"),
                    option3 = rs.getString("option3"),
                    sku = rs.getString("sku"),
                    price = rs.getBigDecimal("price"),
                    comparePrice = rs.getBigDecimal("compare_price"),
                    available = rs.getObject("available", java.lang.Boolean::class.java) == true,
                    imageUrl = rs.getString("image_url")
                )
            }.list()

    fun upsert(cmd: VariantUpsertCmd): Int =
        jdbc.sql(UPSERT_VARIANT_SQL)
            .param("vid", cmd.variantId)
            .param("pid", cmd.productId)
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
