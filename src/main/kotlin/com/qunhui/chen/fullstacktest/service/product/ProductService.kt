package com.qunhui.chen.fullstacktest.service.product

/**
 * @author Qunhui Chen
 * @date 2025/9/26 16:11
 */

import com.fasterxml.jackson.databind.ObjectMapper
import com.qunhui.chen.fullstacktest.adapter.web.domain.CreateProductForm
import com.qunhui.chen.fullstacktest.adapter.web.domain.UpdateProductForm
import com.qunhui.chen.fullstacktest.repo.ProductRepo
import com.qunhui.chen.fullstacktest.repo.ProductUpsertCmd
import com.qunhui.chen.fullstacktest.repo.VariantRepo
import com.qunhui.chen.fullstacktest.repo.VariantUpsertCmd
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.OffsetDateTime

/**
 * ProductService — 使用 TransactionTemplate 的编程式事务实现
 */
@Service
class ProductService(
    private val productRepo: ProductRepo,
    private val variantRepo: VariantRepo,
    private val mapper: ObjectMapper,
    private val txTemplate: TransactionTemplate
) {
    /**
     * 从表单创建/更新产品与变体（幂等 upsert）
     */
    fun addProduct(form: CreateProductForm) {
        val now = OffsetDateTime.now()
        val tags = parseTags(form.tagsText)
        val optionsJson = buildOptionsJson(form)

        txTemplate.executeWithoutResult {
            productRepo.upsert(
                ProductUpsertCmd(
                    productId = form.productId,
                    title = form.title,
                    vendor = form.vendor,
                    productType = form.productType,
                    tags = tags,
                    optionsJson = optionsJson,
                    createdAt = now,
                    updatedAt = now
                )
            )

            form.variants.forEachIndexed { index, v ->
                variantRepo.upsert(
                    VariantUpsertCmd(
                        variantId = v.variantId,
                        productId = form.productId,
                        sku = v.sku,
                        imageUrl = v.imageUrl,
                        price = v.price,
                        comparePrice = v.comparePrice,
                        available = v.available,
                        position = index + 1, // 从 1 开始
                        option1 = v.option1,
                        option2 = v.option2,
                        option3 = v.option3,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    fun updateProduct(form: UpdateProductForm) {
        val now = OffsetDateTime.now()
        val tags = parseTags(form.tagsText)
        val optionsJson = buildOptionsJson(form.optionNames, form.variants.map { Triple(it.option1, it.option2, it.option3) })

        txTemplate.executeWithoutResult {
            productRepo.upsert(
                ProductUpsertCmd(
                    productId = form.productId,
                    title = form.title,
                    vendor = form.vendor,
                    productType = form.productType,
                    tags = tags,
                    optionsJson = optionsJson,
                    createdAt = null, // 保持原 created_at（UPSERT 中已使用 COALESCE）
                    updatedAt = now
                )
            )

            form.variants.forEachIndexed { index, v ->
                variantRepo.upsert(
                    VariantUpsertCmd(
                        variantId = v.variantId,
                        productId = form.productId,
                        sku = v.sku,
                        imageUrl = v.imageUrl,
                        price = v.price,
                        comparePrice = v.comparePrice,
                        available = v.available,
                        position = index + 1,
                        option1 = v.option1,
                        option2 = v.option2,
                        option3 = v.option3,
                        createdAt = null, // 同上
                        updatedAt = now
                    )
                )
            }
        }
    }

    fun deleteProduct(productId: Long) {
        txTemplate.executeWithoutResult {
            variantRepo.deleteByProductId(productId)
            productRepo.deleteByProductId(productId)
        }
    }

    fun existsByProductId(productId: Long): Boolean =
        productRepo.existsByProductId(productId)

    /**
     * 简化的首屏查询（保持与旧控制器一致的行为）
     */
    fun listFirstPage(limit: Int = 10) = productRepo.listForViewPaged(limit = limit, offset = 0, search = null)

    private fun parseTags(tagsText: String?): List<String>? = tagsText
        ?.split(',', '，', ';')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }

    private data class Opt(val name: String, val position: Int, val values: List<String>)

    private fun buildOptionsJson(form: CreateProductForm): String? {
        val optNames = form.optionNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .mapIndexed { idx, name -> idx to name }

        if (optNames.isEmpty()) return null

        val values1 = form.variants.mapNotNull { it.option1?.trim() }.distinct()
        val values2 = form.variants.mapNotNull { it.option2?.trim() }.distinct()
        val values3 = form.variants.mapNotNull { it.option3?.trim() }.distinct()

        val list = optNames.map { (i, name) ->
            val vals = when (i) {
                0 -> values1; 1 -> values2; else -> values3
            }
            Opt(name, i + 1, vals)
        }
        return mapper.writeValueAsString(list)
    }

    // 重载：用于编辑页（传入明确的 optionNames 和变体选项值）
    private fun buildOptionsJson(optionNames: List<String>?, optionTriples: List<Triple<String?, String?, String?>>): String? {
        val names = (optionNames ?: emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(3)
            .mapIndexed { idx, name -> idx to name }
        if (names.isEmpty()) return null

        val values1 = optionTriples.mapNotNull { it.first?.trim() }.distinct()
        val values2 = optionTriples.mapNotNull { it.second?.trim() }.distinct()
        val values3 = optionTriples.mapNotNull { it.third?.trim() }.distinct()

        val list = names.map { (i, name) ->
            val vals = when (i) { 0 -> values1; 1 -> values2; else -> values3 }
            Opt(name, i + 1, vals)
        }
        return mapper.writeValueAsString(list)
    }
}
