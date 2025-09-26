package com.qunhui.chen.fullstacktest.service.product


import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.qunhui.chen.fullstacktest.adapter.jobs.domain.ProductList
import com.qunhui.chen.fullstacktest.adapter.jobs.domain.RemoteProduct
import com.qunhui.chen.fullstacktest.repo.ProductRepo
import com.qunhui.chen.fullstacktest.repo.ProductUpsertCmd
import com.qunhui.chen.fullstacktest.repo.VariantRepo
import com.qunhui.chen.fullstacktest.repo.VariantUpsertCmd
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * @author Qunhui Chen
 * @date 2025/9/24 00:15
 */
@Service
class ProductSyncService(
    private val productRepo: ProductRepo,
    private val variantRepo: VariantRepo,
    private val mapper: ObjectMapper,
    private val transactionTemplate: TransactionTemplate,
    private val http: HttpClient

) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun sync() {
        val start = System.currentTimeMillis()
        val products = fetchProducts()
        if (products.isEmpty()) {
            log.warn("Fetch returned empty list; skip deletion to be safe.")
            return
        }

        // 以 updated_at 优先排序，缺失则用 created_at；取最新 50
        val top50 = products.sortedByDescending { it.updated_at ?: it.created_at }.take(50)

        val productCmds = top50.map { p ->
            ProductUpsertCmd(
                productId = p.id,
                title = p.title,
                vendor = p.vendor,
                productType = p.product_type,
                tags = p.tags,
                optionsJson = mapper.writeValueAsString(p.options),
                createdAt = p.created_at,
                updatedAt = p.updated_at
            )
        }
        val variantCmds = buildList {
            top50.forEach { p ->
                p.variants.forEach { v ->
                    add(
                        VariantUpsertCmd(
                            variantId = v.id,
                            productId = v.product_id,
                            sku = v.sku,
                            imageUrl = v.featured_image?.src,
                            price = v.price,
                            comparePrice = v.compare_at_price,
                            available = v.available,
                            position = v.position,
                            option1 = v.option1, option2 = v.option2, option3 = v.option3,
                            createdAt = v.created_at ?: p.created_at,
                            updatedAt = v.updated_at ?: p.updated_at
                        )
                    )
                }
            }
        }
        val keepIds = top50.map { it.id }

        transactionTemplate.executeWithoutResult {
            productCmds.forEach { productRepo.upsert(it) }
            variantCmds.forEach { variantRepo.upsert(it) }

            // 清理：只保留这 50 个 product（以及它们的 variants）
            variantRepo.deleteVariantsNotInProducts(keepIds, minGuard = 10)
            productRepo.deleteNotIn(keepIds, minGuard = 10)
        }

        log.info(
            "Sync OK: kept={}, upsertedProducts={}, upsertedVariants~{}, took={}ms",
            keepIds.size, top50.size, variantCmds.size, System.currentTimeMillis() - start
        )
    }

    private fun fetchProducts(): List<RemoteProduct> {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://famme.no/products.json"))
            .GET()
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) error("HTTP ${res.statusCode()}")
        val body = res.body()

        return try {
            mapper.readValue<ProductList>(body).products
        } catch (e: Exception) {
            log.error("failed to parse as ProductList, try as List<RemoteProduct>", e);
            throw e
        }
    }
}
