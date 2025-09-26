package com.qunhui.chen.fullstacktest.service.product

/**
 * @author Qunhui Chen
 * @date 2025/9/26 16:11
 */

import com.fasterxml.jackson.databind.ObjectMapper
import com.qunhui.chen.fullstacktest.adapter.web.domain.CreateProductForm
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

            form.variants.forEach { v ->
                variantRepo.upsert(
                    VariantUpsertCmd(
                        variantId = v.variantId,
                        productId = form.productId,
                        sku = v.sku,
                        imageUrl = v.imageUrl,
                        price = v.price,
                        comparePrice = v.comparePrice,
                        available = v.available,
                        position = null,
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
}
