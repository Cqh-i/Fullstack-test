package com.qunhui.chen.fullstacktest.service

/**
 * @author Qunhui Chen
 * @date 2025/9/24 00:15
 */
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.qunhui.chen.fullstacktest.jobs.domain.ProductList
import com.qunhui.chen.fullstacktest.jobs.domain.RemoteProduct
import com.qunhui.chen.fullstacktest.repo.ProductRepo
import com.qunhui.chen.fullstacktest.repo.ProductUpsertCmd
import com.qunhui.chen.fullstacktest.repo.VariantRepo
import com.qunhui.chen.fullstacktest.repo.VariantUpsertCmd
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
class ProductSyncService(
    private val productRepo: ProductRepo,
    private val variantRepo: VariantRepo
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val mapper = jacksonObjectMapper()

    @Scheduled(initialDelayString = "PT0S", fixedDelayString = "PT15M")
    @Transactional
    fun sync() {
        val start = System.currentTimeMillis()
        try {
            val products = fetchProducts()
            if (products.isEmpty()) {
                log.warn("Fetch returned empty list; skip deletion to be safe.")
                return
            }

            // 以 updated_at 优先排序，缺失则用 created_at；取最新 50
            val top50 = products.sortedByDescending { it.updated_at ?: it.created_at }.take(50)

            // upsert products
            top50.forEach { p ->
                productRepo.upsert(
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
                )

                // upsert variants
                p.variants.forEach { v ->
                    variantRepo.upsert(
                        VariantUpsertCmd(
                            variantId = v.id,
                            productId = v.product_id,          // 我们表里用“外部 product_id”做关联字段
                            title = v.title,
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

            // 清理：只保留这 50 个 product（以及它们的 variants）
            val keepIds = top50.map { it.id }
            variantRepo.deleteVariantsNotInProducts(keepIds, minGuard = 10) // 保护：集合太小不删
            productRepo.deleteNotIn(keepIds, minGuard = 10)

            val took = System.currentTimeMillis() - start
            log.info(
                "Sync OK: kept={}, upsertedProducts={}, upsertedVariants~{}, took={}ms",
                keepIds.size, top50.size, top50.sumOf { it.variants.size }, took
            )
        } catch (e: Exception) {
            log.error("Sync failed", e)
            // 事务回滚，由 @Transactional 保证
        }
    }

    private fun fetchProducts(): List<RemoteProduct> {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://famme.no/products.json"))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() != 200) error("HTTP ${res.statusCode()}")
        val body = res.body()

        // 两种结构都兼容：{ products:[...] } 或直接是 [...]
        return try {
            mapper.readValue<ProductList>(body).products
        } catch (_: Exception) {
            mapper.readValue<List<RemoteProduct>>(body)
        }
    }
}
